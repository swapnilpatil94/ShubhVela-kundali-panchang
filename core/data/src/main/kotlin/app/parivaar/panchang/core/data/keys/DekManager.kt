package app.parivaar.panchang.core.data.keys

import app.parivaar.panchang.core.data.crypto.AesGcm
import app.parivaar.panchang.core.data.crypto.Bytes
import app.parivaar.panchang.core.data.crypto.CryptoException
import app.parivaar.panchang.core.data.crypto.DekRing
import app.parivaar.panchang.core.data.crypto.MutableDekRing
import app.parivaar.panchang.core.data.crypto.RowEnvelope
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.RecordTx
import app.parivaar.panchang.core.data.store.StoredRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.security.SecureRandom

/**
 * Owns the DEK ring (spec section 4 "Key state", "Rotation"). Wrapped DEKs live in [keysDir]
 * (`noBackupFilesDir/keys`) as `dek.<g>.bin`. Never wipes, overwrites or deletes a DEK that a row still
 * uses (D8): key problems are reported as [KeyState] and the user chooses recovery.
 *
 * Rotation order: write `dek.<g+1>.bin` (temp + fsync + rename), re-encrypt every row under g+1 in one
 * transaction, then delete older files no row uses. A crash at any point leaves every row readable by a
 * key file still on disk; [open] loads all `dek.*.bin`, uses the highest g for new rows, and prunes
 * older files once no row names them.
 */
internal class DekManager(
    private val keysDir: File,
    private val wrapper: KeyWrapper,
    private val rng: SecureRandom,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) : AutoCloseable {
    private val mutex = Mutex()
    private val keys = MutableDekRing()

    /** Keys for [RowEnvelope]; empty until [open] or [createFresh] returns [KeyState.Ready]. */
    val ring: DekRing get() = keys

    /** Loads every wrapped DEK. Keystore failures are retried once after 250 ms. Store errors propagate. */
    suspend fun open(store: RecordStore): KeyState = mutex.withLock {
        val first = load(store)
        if (first != KeyState.Unavailable) return@withLock first
        retryDelay(RETRY_DELAY_MS)
        load(store)
    }

    /**
     * First start or new device: creates the KEK if absent and DEK g=1. Refuses if any wrapped DEK
     * exists (never overwrite, D8). Keystore errors map to [KeyState.Lost] / [KeyState.Unavailable].
     */
    suspend fun createFresh(): KeyState = mutex.withLock {
        check(WrappedKeyFile.list(keysDir, DEK).isEmpty()) { "wrapped key exists" }
        val dek = newKey()
        var kept = false
        try {
            val state = keystoreState {
                if (!wrapper.exists()) wrapper.create()
                val bytes = WrappedKeyFile.seal(wrapper, DEK, FIRST_GEN, dek)
                WrappedKeyFile.writeAtomic(keysDir, fileName(FIRST_GEN), bytes)
            }
            if (state == KeyState.Ready) {
                keys.close()
                keys.put(FIRST_GEN, dek, makeCurrent = true)
                kept = true
            }
            state
        } finally {
            if (!kept) Bytes.wipe(dek)
        }
    }

    /**
     * Rotates to a new DEK generation and returns it. Throws [KeyLostException] /
     * [KeyUnavailableException] / [IOException] or the store's exception; on any throw the old keys stay
     * loaded and every row stays readable. Rows that do not decrypt (tampered) are left untouched and keep
     * their key file alive.
     */
    suspend fun rotate(store: RecordStore): Int = mutex.withLock {
        val old = keys.currentId
        val g = maxOf(old, WrappedKeyFile.list(keysDir, DEK).keys.maxOrNull() ?: 0) + 1
        val dek = newKey()
        var kept = false
        try {
            WrappedKeyFile.writeAtomic(keysDir, fileName(g), WrappedKeyFile.seal(wrapper, DEK, g, dek))
            kept = true
        } finally {
            if (!kept) Bytes.wipe(dek)
        }
        keys.put(g, dek, makeCurrent = false)
        val sealing = object : DekRing {
            override val currentId: Int = g
            override fun key(id: Int): ByteArray? = keys.key(id)
        }
        store.tx { reencryptAll(sealing) }
        keys.makeCurrent(g)
        prune(store)
        g
    }

    override fun close() = keys.close()

    override fun toString(): String = "DekManager(redacted)"

    private suspend fun load(store: RecordStore): KeyState {
        WrappedKeyFile.deleteTemps(keysDir)
        val files = WrappedKeyFile.list(keysDir, DEK)
        if (files.isEmpty()) {
            val hasRows = store.tx { TABLES.any { count(it) > 0 } }
            return if (hasRows) KeyState.Lost else KeyState.Empty
        }
        val loaded = LinkedHashMap<Int, ByteArray>()
        val state = keystoreState {
            if (!wrapper.exists()) throw KeyLostException()
            for ((g, f) in files) loaded[g] = WrappedKeyFile.open(wrapper, f.readBytes(), DEK, g)
        }
        keys.close()
        if (state == KeyState.Ready) {
            loaded.forEach { (g, k) -> keys.put(g, k, makeCurrent = false) }
            keys.makeCurrent(loaded.keys.max())
            prune(store)
        } else {
            loaded.values.forEach { Bytes.wipe(it) }
        }
        return state
    }

    private fun RecordTx.reencryptAll(sealing: DekRing) {
        for (tbl in TABLES) {
            for (row in list(tbl)) {
                val keyId = keyIdOrNull(row.blob)
                val plain = if (keyId != null && keyId != sealing.currentId) openOrNull(row) else null
                if (plain == null) continue
                try {
                    val blob = RowEnvelope.seal(sealing, row.tbl, row.id, row.ver, plain, rng)
                    put(StoredRow(row.tbl, row.id, row.pos, row.ver, blob))
                } finally {
                    Bytes.wipe(plain)
                }
            }
        }
    }

    private fun openOrNull(row: StoredRow): ByteArray? =
        try {
            RowEnvelope.open(keys, row.tbl, row.id, row.ver, row.blob)
        } catch (@Suppress("SwallowedException") e: CryptoException) {
            null
        }

    /** Deletes key files older than the current one that no row names. Failed deletes retry next open. */
    private suspend fun prune(store: RecordStore) {
        val current = keys.currentId
        val used = store.tx { TABLES.flatMap { list(it) }.mapNotNull { keyIdOrNull(it.blob) }.toSet() }
        for ((g, f) in WrappedKeyFile.list(keysDir, DEK)) {
            if (g == current || g in used) continue
            if (f.delete() && g in keys.ids) keys.remove(g)
        }
    }

    private fun newKey(): ByteArray = ByteArray(AesGcm.KEY_BYTES).also { rng.nextBytes(it) }

    private fun fileName(g: Int) = WrappedKeyFile.fileName(DEK, g)

    private companion object {
        const val DEK = WrappedKeyFile.PURPOSE_DEK
        const val FIRST_GEN = 1
        const val RETRY_DELAY_MS = 250L
        val TABLES = listOf(
            RowEnvelope.TBL_PROFILE, RowEnvelope.TBL_PLACE, RowEnvelope.TBL_BOND, RowEnvelope.TBL_SETTINGS,
        )
    }
}

/** Runs a keystore/file step: Ready, or Lost / Unavailable by failure kind (never destructive). */
private inline fun keystoreState(block: () -> Unit): KeyState =
    try {
        block()
        KeyState.Ready
    } catch (@Suppress("SwallowedException") e: KeyLostException) {
        KeyState.Lost
    } catch (@Suppress("SwallowedException") e: CryptoException) {
        KeyState.Lost
    } catch (@Suppress("SwallowedException") e: KeyUnavailableException) {
        KeyState.Unavailable
    } catch (@Suppress("SwallowedException") e: IOException) {
        KeyState.Unavailable
    }

private fun keyIdOrNull(blob: ByteArray): Int? =
    try {
        RowEnvelope.keyIdOf(blob)
    } catch (@Suppress("SwallowedException") e: CryptoException) {
        null
    }
