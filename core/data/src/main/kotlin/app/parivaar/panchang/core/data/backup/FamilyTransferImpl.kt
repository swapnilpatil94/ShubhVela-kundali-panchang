package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.FamilyTransfer
import app.parivaar.panchang.core.data.ImportMode
import app.parivaar.panchang.core.data.TransferSummary
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.BondCodec
import app.parivaar.panchang.core.data.codec.CodecException
import app.parivaar.panchang.core.data.codec.DataLimits
import app.parivaar.panchang.core.data.codec.FamilyDocument
import app.parivaar.panchang.core.data.codec.PayloadBytes
import app.parivaar.panchang.core.data.codec.PayloadMigrations
import app.parivaar.panchang.core.data.codec.PlaceCodec
import app.parivaar.panchang.core.data.codec.ProfileCodec
import app.parivaar.panchang.core.data.codec.SettingsCodec
import app.parivaar.panchang.core.data.crypto.Bytes
import app.parivaar.panchang.core.data.crypto.CryptoException
import app.parivaar.panchang.core.data.crypto.DekRing
import app.parivaar.panchang.core.data.crypto.RowEnvelope
import app.parivaar.panchang.core.data.repo.toBytes
import app.parivaar.panchang.core.data.store.RecordTx
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.StoreClosedException
import app.parivaar.panchang.core.data.store.StoredRow
import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * Passphrase export/import (spec section 4 "Export", "Errors"). Both calls zero the passphrase. Import reads at
 * most 8 MiB + 1 B, decrypts and decodes everything first, then writes in one transaction; nothing is written on
 * any error. MERGE: same id, incoming wins iff strictly later (Profile.updatedAt, SavedPlace.lastUsedAt,
 * Bond.createdAt) or the local row is unreadable; local settings are kept. REPLACE_ALL replaces everything.
 * The summary counts the records in the file (REPLACE_ALL) or the records added or replaced (MERGE).
 */
internal class FamilyTransferImpl(
    private val store: RecordStore,
    private val ring: DekRing,
    private val state: StateFlow<VaultState>,
    private val rng: SecureRandom,
    private val kdfDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FamilyTransfer {

    override suspend fun export(out: OutputStream, passphrase: CharArray): DataResult<TransferSummary> =
        guarded(passphrase) {
            when {
                state.value != VaultState.Ready -> DataResult.Err(DataError.LOCKED)
                !ExportFormat.meetsPolicy(passphrase) -> DataResult.Err(DataError.PASSPHRASE_POLICY)
                else -> exportReady(out, passphrase)
            }
        }

    override suspend fun import(
        input: InputStream,
        passphrase: CharArray,
        mode: ImportMode,
    ): DataResult<TransferSummary> =
        guarded(passphrase) {
            if (state.value != VaultState.Ready) return@guarded DataResult.Err(DataError.LOCKED)
            val bytes = readBounded(input) ?: return@guarded DataResult.Err(DataError.FILE_TOO_LARGE)
            val plain = try {
                withContext(kdfDispatcher) { ExportFormat.open(passphrase, bytes) }
            } catch (e: CryptoException) {
                return@guarded DataResult.Err(errorOf(e.reason))
            }
            try {
                val doc = FamilyDocument.fromBytes(plain)
                store.tx {
                    if (mode == ImportMode.REPLACE_ALL) FamilyRows.replaceAll(this, ring, rng, doc) else merge(doc)
                }
            } catch (@Suppress("SwallowedException") e: CodecException) {
                DataResult.Err(DataError.FILE_FORMAT)
            } finally {
                Bytes.wipe(plain)
            }
        }

    private suspend fun exportReady(out: OutputStream, passphrase: CharArray): DataResult<TransferSummary> {
        val doc = store.tx { FamilyRows.read(this, ring) }
        val plain = doc.toBytes()
        try {
            if (plain.size > ExportFormat.MAX_PLAINTEXT) return DataResult.Err(DataError.FILE_TOO_LARGE)
            val file = withContext(kdfDispatcher) { ExportFormat.seal(passphrase, plain, rng) }
            out.write(file)
            out.flush()
            return DataResult.Ok(TransferSummary(doc.profiles.size, doc.places.size, doc.bonds.size))
        } finally {
            Bytes.wipe(plain)
        }
    }

    private fun RecordTx.merge(doc: FamilyDocument): DataResult<TransferSummary> {
        val profiles = FamilyRows.plan(this, ring, Tbl.PROFILE, doc.profiles, ProfileCodec::fromJson, { it.id }) {
                a, b ->
            a.updatedAt > b.updatedAt
        }
        val places = FamilyRows.plan(this, ring, Tbl.PLACE, doc.places, PlaceCodec::fromJson, { it.id }) { a, b ->
            a.lastUsedAt > b.lastUsedAt
        }
        val bonds = FamilyRows.plan(this, ring, Tbl.BOND, doc.bonds, BondCodec::fromJson, { it.id }) { a, b ->
            a.createdAt > b.createdAt
        }
        val overLimit = profiles.finalCount > DataLimits.MAX_PROFILES || places.finalCount > DataLimits.MAX_PLACES ||
            bonds.finalCount > DataLimits.MAX_BONDS
        return when {
            overLimit -> DataResult.Err(DataError.LIMIT)
            profiles.result.count { it.relation == Relation.SELF } > 1 -> DataResult.Err(DataError.SELF_EXISTS)
            else -> {
                profiles.writes.forEach { (p, pos) -> put(row(Tbl.PROFILE, p.id, pos, ProfileCodec.toJson(p))) }
                places.writes.forEach { (p, pos) -> put(row(Tbl.PLACE, p.id, pos, PlaceCodec.toJson(p))) }
                bonds.writes.forEach { (b, pos) -> put(row(Tbl.BOND, b.id, pos, BondCodec.toJson(b))) }
                DataResult.Ok(TransferSummary(profiles.writes.size, places.writes.size, bonds.writes.size))
            }
        }
    }

    private fun row(tbl: Int, id: RecordId, pos: Int, json: JsonObject) = FamilyRows.row(ring, rng, tbl, id, pos, json)

    /** Zeroes [passphrase] in every case; unexpected failures become IO without their message. */
    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun guarded(
        passphrase: CharArray,
        block: () -> DataResult<TransferSummary>,
    ): DataResult<TransferSummary> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: StoreClosedException) {
            DataResult.Err(DataError.LOCKED)
        } catch (_: Exception) {
            DataResult.Err(DataError.IO)
        } finally {
            Bytes.wipe(passphrase)
        }

    private fun readBounded(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(READ_CHUNK)
        while (out.size() <= ExportFormat.MAX_BYTES) {
            val n = input.read(buf, 0, minOf(buf.size, ExportFormat.MAX_BYTES + 1 - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return if (out.size() > ExportFormat.MAX_BYTES) null else out.toByteArray()
    }

    private fun errorOf(reason: CryptoException.Reason): DataError = when (reason) {
        CryptoException.Reason.FORMAT -> DataError.FILE_FORMAT
        CryptoException.Reason.VERSION -> DataError.FILE_VERSION
        CryptoException.Reason.AUTH -> DataError.FILE_AUTH
    }

    private companion object {
        const val READ_CHUNK = 64 * 1024
    }
}

/** Reads and writes a whole [FamilyDocument] as encrypted rows; shared by export, import and snapshots. */
internal object FamilyRows {
    private val SETTINGS_ID = ByteArray(RowEnvelope.ID_BYTES)

    /** Merge plan for one table: rows to write (record, pos) and the resulting readable records. */
    class Plan<T>(val writes: List<Pair<T, Int>>, val result: List<T>, val finalCount: Int)

    /** Every readable record; unreadable rows are skipped (they are never exported or snapshotted). */
    fun read(tx: RecordTx, ring: DekRing): FamilyDocument {
        val profiles = tx.list(Tbl.PROFILE).mapNotNull { decode(ring, it, ProfileCodec::fromJson) { p -> p.id } }
        val places = tx.list(Tbl.PLACE).mapNotNull { decode(ring, it, PlaceCodec::fromJson) { p -> p.id } }
        val bonds = tx.list(Tbl.BOND).mapNotNull { decode(ring, it, BondCodec::fromJson) { b -> b.id } }
        val settings = tx.get(Tbl.SETTINGS, SETTINGS_ID)?.let { decode(ring, it, SettingsCodec::fromJson) { null } }
        return FamilyDocument(profiles, places, bonds, settings ?: AppSettings())
    }

    /** Clears every row and writes [doc] (profiles keep file order as pos). */
    fun replaceAll(tx: RecordTx, ring: DekRing, rng: SecureRandom, doc: FamilyDocument): DataResult<TransferSummary> {
        tx.clearAll()
        doc.profiles.forEachIndexed { i, p -> tx.put(row(ring, rng, Tbl.PROFILE, p.id, i, ProfileCodec.toJson(p))) }
        doc.places.forEach { p -> tx.put(row(ring, rng, Tbl.PLACE, p.id, 0, PlaceCodec.toJson(p))) }
        doc.bonds.forEach { b -> tx.put(row(ring, rng, Tbl.BOND, b.id, 0, BondCodec.toJson(b))) }
        tx.put(sealRow(ring, rng, Tbl.SETTINGS, SETTINGS_ID, 0, SettingsCodec.toJson(doc.settings)))
        return DataResult.Ok(TransferSummary(doc.profiles.size, doc.places.size, doc.bonds.size))
    }

    fun <T> plan(
        tx: RecordTx, ring: DekRing, tbl: Int, incoming: List<T>, decode: (JsonElement) -> T,
        idOf: (T) -> RecordId, newer: (T, T) -> Boolean,
    ): Plan<T> {
        val rows = tx.list(tbl)
        val rowByHex = rows.associateBy { hexOf(it.id) }
        val localByHex = rows.associate { hexOf(it.id) to decode(ring, it, decode, idOf) }
        var nextPos = (rows.maxOfOrNull { it.pos } ?: -1) + 1
        val writes = ArrayList<Pair<T, Int>>()
        val replaced = HashSet<String>()
        for (t in incoming) {
            val hex = idOf(t).hex
            val row = rowByHex[hex]
            val local = localByHex[hex]
            if (row == null) {
                writes += t to nextPos++
            } else if (local == null || newer(t, local)) {
                writes += t to row.pos
                replaced += hex
            }
        }
        val kept = localByHex.filterKeys { it !in replaced }.values.filterNotNull()
        return Plan(writes, kept + writes.map { it.first }, rows.size + writes.size - replaced.size)
    }

    fun row(ring: DekRing, rng: SecureRandom, tbl: Int, id: RecordId, pos: Int, json: JsonObject): StoredRow =
        sealRow(ring, rng, tbl, id.toBytes(), pos, json)

    private fun sealRow(
        ring: DekRing, rng: SecureRandom, tbl: Int, id: ByteArray, pos: Int, json: JsonObject,
    ): StoredRow {
        val plain = PayloadBytes.encode(json)
        try {
            val ver = PayloadMigrations.CURRENT_VERSION
            return StoredRow(tbl, id, pos, ver, RowEnvelope.seal(ring, tbl, id, ver, plain, rng))
        } finally {
            Bytes.wipe(plain)
        }
    }

    private fun hexOf(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    private fun <T> decode(ring: DekRing, row: StoredRow, decode: (JsonElement) -> T, idOf: (T) -> RecordId?): T? {
        val plain = try {
            RowEnvelope.open(ring, row.tbl, row.id, row.ver, row.blob)
        } catch (@Suppress("SwallowedException") e: CryptoException) {
            return null
        }
        return try {
            val v = decode(PayloadBytes.decode(plain, row.tbl, row.ver))
            val id = idOf(v)
            if (id == null || id.toBytes().contentEquals(row.id)) v else null
        } catch (@Suppress("SwallowedException") e: CodecException) {
            null
        } finally {
            Bytes.wipe(plain)
        }
    }
}
