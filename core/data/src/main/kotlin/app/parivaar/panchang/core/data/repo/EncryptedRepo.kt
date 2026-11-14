package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.Records
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.CodecException
import app.parivaar.panchang.core.data.codec.PayloadBytes
import app.parivaar.panchang.core.data.codec.PayloadMigrations
import app.parivaar.panchang.core.data.crypto.Bytes
import app.parivaar.panchang.core.data.crypto.CryptoException
import app.parivaar.panchang.core.data.crypto.DekRing
import app.parivaar.panchang.core.data.crypto.RowEnvelope
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.RecordTx
import app.parivaar.panchang.core.data.store.StoreClosedException
import app.parivaar.panchang.core.data.store.StoredRow
import app.parivaar.panchang.core.model.profile.RecordId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.security.SecureRandom

private const val HEX_RADIX = 16
private const val HEX_PER_BYTE = 2
private const val BYTE_MASK = 0xff

internal fun RecordId.toBytes(): ByteArray =
    ByteArray(RecordId.LENGTH / HEX_PER_BYTE) { i ->
        val start = i * HEX_PER_BYTE
        hex.substring(start, start + HEX_PER_BYTE).toInt(HEX_RADIX).toByte()
    }

internal fun bytesToId(b: ByteArray): RecordId {
    val sb = StringBuilder(b.size * HEX_PER_BYTE)
    for (v in b) sb.append((v.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0'))
    return RecordId(sb.toString())
}

/**
 * Shared machinery of the encrypted repositories: seals records into rows (AES-256-GCM, AAD binds table, id,
 * payload version and key id), opens and decodes rows, and counts rows that fail any check as unreadable.
 * Every operation answers LOCKED unless the vault is Ready. Plaintext byte arrays are wiped after use.
 */
internal abstract class EncryptedRepo(
    private val store: RecordStore,
    private val ring: DekRing,
    private val vaultState: StateFlow<VaultState>,
    private val rng: SecureRandom,
    private val migrations: PayloadMigrations = PayloadMigrations.DEFAULT,
) {
    /** Runs [block] when the vault is Ready; maps unexpected failures to IO (never carrying their message). */
    @Suppress("TooGenericExceptionCaught")
    protected suspend fun <T> ready(block: suspend () -> DataResult<T>): DataResult<T> {
        if (vaultState.value != VaultState.Ready) return DataResult.Err(DataError.LOCKED)
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: StoreClosedException) {
            DataResult.Err(DataError.LOCKED) // a delete-all closed the store after the state check
        } catch (_: Exception) {
            DataResult.Err(DataError.IO)
        }
    }

    protected suspend fun <T> tx(block: RecordTx.() -> T): T = store.tx(block)

    protected fun seal(tbl: Int, id: RecordId, json: JsonObject, pos: Int): StoredRow {
        val plain = PayloadBytes.encode(json)
        try {
            val idBytes = id.toBytes()
            val blob = RowEnvelope.seal(ring, tbl, idBytes, PayloadMigrations.CURRENT_VERSION, plain, rng)
            return StoredRow(tbl, idBytes, pos, PayloadMigrations.CURRENT_VERSION, blob)
        } finally {
            Bytes.wipe(plain)
        }
    }

    /** Decrypts and decodes [row]; null when anything about it is wrong (tamper, wrong key, id mismatch). */
    protected fun <T> open(row: StoredRow, decode: (JsonElement) -> T, idOf: (T) -> RecordId?): T? {
        val plain = decrypt(row) ?: return null
        return try {
            val record = decode(PayloadBytes.decode(plain, row.tbl, row.ver, migrations))
            val id = idOf(record)
            if (id == null || id.toBytes().contentEquals(row.id)) record else null
        } catch (_: CodecException) {
            null
        } finally {
            Bytes.wipe(plain)
        }
    }

    private fun decrypt(row: StoredRow): ByteArray? =
        try {
            RowEnvelope.open(ring, row.tbl, row.id, row.ver, row.blob)
        } catch (_: CryptoException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    protected fun <T> readAll(rows: List<StoredRow>, decode: (JsonElement) -> T, idOf: (T) -> RecordId?): Records<T> {
        val items = ArrayList<T>(rows.size)
        var bad = 0
        for (r in rows) {
            val v = open(r, decode, idOf)
            if (v == null) bad++ else items.add(v)
        }
        return Records(items, bad)
    }

    protected fun <T> observe(tbl: Int, decode: (JsonElement) -> T, idOf: (T) -> RecordId?): Flow<Records<T>> =
        combine(vaultState, store.changes) { s, _ -> s }.let { trigger ->
            kotlinx.coroutines.flow.flow {
                trigger.collect { s ->
                    val rows = if (s == VaultState.Ready) store.tx { list(tbl) } else emptyList()
                    emit(readAll(rows, decode, idOf))
                }
            }
        }
}
