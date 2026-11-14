package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.SnapshotRestorer
import app.parivaar.panchang.core.data.codec.FamilyDocument
import app.parivaar.panchang.core.data.crypto.AesGcm
import app.parivaar.panchang.core.data.crypto.Bytes
import app.parivaar.panchang.core.data.keys.DekManager
import app.parivaar.panchang.core.data.keys.DirSync
import app.parivaar.panchang.core.data.keys.KeyState
import app.parivaar.panchang.core.data.keys.KeyWrapper
import app.parivaar.panchang.core.data.keys.WrappedKeyFile
import app.parivaar.panchang.core.data.store.RecordStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Encrypted backup snapshot and new-device restore (spec section 4 "Snapshot", section 5; D4).
 *
 * K_b (32 random bytes, generation g) is kept wrapped under the KEK as `keys/kb.<g>.bin` and, off-device, in
 * [backupKeys]. A snapshot is written only when [BackupKeyStore.isAvailable]; M06 binds
 * [UnavailableBackupKeyStore], so no `snapshot.bin` is ever written in M06.
 *
 * K_b rotation order: write `kb.<g+1>.bin`, write the new snapshot to `snapshot.bin.tmp`, store (g+1, K_b) in
 * [backupKeys], rename the temp file over `snapshot.bin`, delete older `kb.*.bin`. A crash before the store
 * step leaves the old snapshot and old K_b consistent; a crash after it is healed by the next [writeNow],
 * which rotates whenever the local and stored K_b disagree.
 *
 * Restore (Restoring): decrypt with the retrieved K_b -> new KEK + DEK g=1 -> insert everything in one
 * transaction -> rotate K_b (best effort; the data is already safe) -> true. Any failure before the commit
 * clears the partial rows, deletes the new DEK, keeps `snapshot.bin` and K_b, and returns false (KeyLost).
 */
internal class SnapshotManager(
    private val backupDir: File,
    private val keysDir: File,
    private val store: RecordStore,
    private val dek: DekManager,
    private val wrapper: KeyWrapper,
    private val backupKeys: BackupKeyStore,
    private val rng: SecureRandom,
    private val clock: () -> Instant,
    private val debounceMs: Long = DEBOUNCE_MS,
) : SnapshotRestorer {
    private val mutex = Mutex()

    val snapshotFile: File get() = File(backupDir, SNAPSHOT)

    /** True if no snapshot exists or it is older than 24 h (written on open in that case). */
    fun isStale(): Boolean {
        val f = snapshotFile
        return !f.isFile || clock() - Instant.fromEpochMilliseconds(f.lastModified()) > STALE_AFTER
    }

    /** Writes the snapshot if stale; see [writeNow]. */
    suspend fun writeIfStale(): Boolean = isStale() && writeNow()

    /** Rewrites the snapshot 2 s after the last change. Returns at once when no backup key store is available. */
    @OptIn(FlowPreview::class)
    suspend fun watch() {
        if (!backupKeys.isAvailable()) return
        store.changes.drop(1).debounce(debounceMs).collect { writeNow() }
    }

    /** Encrypts every readable record under K_b into `snapshot.bin`. False if unavailable, locked or failed. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun writeNow(): Boolean = mutex.withLock {
        if (!backupKeys.isAvailable() || !hasCurrentKey(dek)) return@withLock false
        val plain = store.tx { FamilyRows.read(this, dek.ring) }.toBytes()
        try {
            if (plain.size > SnapshotFormat.MAX_PLAINTEXT) return@withLock false
            writeUnderCurrentKb(plain)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            Bytes.wipe(plain)
        }
    }

    override fun canRestore(): Boolean =
        snapshotFile.isFile && backupKeys.retrieve()?.also { Bytes.wipe(it.second) } != null

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override suspend fun restore(): Boolean = mutex.withLock {
        // Only from the Empty state: never touch an existing DEK (D8).
        if (WrappedKeyFile.list(keysDir, WrappedKeyFile.PURPOSE_DEK).isNotEmpty()) return@withLock false
        val remote = backupKeys.retrieve() ?: return@withLock false
        var plain: ByteArray? = null
        try {
            val bytes = readSnapshot(snapshotFile) ?: return@withLock false
            plain = SnapshotFormat.open(remote.second, remote.first, bytes)
            val doc = FamilyDocument.fromBytes(plain)
            check(dek.createFresh() == KeyState.Ready) { "restore key creation failed" }
            store.tx { FamilyRows.replaceAll(this, dek.ring, rng, doc) }
        } catch (e: CancellationException) {
            discardPartial()
            throw e
        } catch (_: Exception) {
            discardPartial()
            Bytes.wipe(plain)
            Bytes.wipe(remote.second)
            return@withLock false
        }
        try {
            // Never touch the off-device key store unless it is available (D4).
            if (backupKeys.isAvailable()) rotateKb(nextGeneration(keysDir, remote.first), checkNotNull(plain))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Data is committed; the snapshot and K_b stay as they were and the next writeNow rotates.
        } finally {
            Bytes.wipe(plain)
            Bytes.wipe(remote.second)
        }
        true
    }

    private fun writeUnderCurrentKb(plain: ByteArray) {
        val local = loadLocalKb()
        val remote = backupKeys.retrieve()
        try {
            val consistent = local != null && remote != null && local.first == remote.first &&
                Bytes.constantTimeEquals(local.second, remote.second)
            if (consistent) {
                writeReplacing(snapshotFile, SnapshotFormat.seal(local!!.second, local.first, plain, rng))
            } else {
                rotateKb(nextGeneration(keysDir, maxOf(local?.first ?: 0, remote?.first ?: 0)), plain)
            }
        } finally {
            Bytes.wipe(local?.second)
            Bytes.wipe(remote?.second)
        }
    }

    /** Latest local K_b, or null if none or it cannot be unwrapped (then a new generation is created). */
    @Suppress("TooGenericExceptionCaught")
    private fun loadLocalKb(): Pair<Int, ByteArray>? {
        val (g, f) = WrappedKeyFile.list(keysDir, WrappedKeyFile.PURPOSE_KB).entries.maxByOrNull { it.key }
            ?.toPair() ?: return null
        return try {
            g to WrappedKeyFile.open(wrapper, f.readBytes(), WrappedKeyFile.PURPOSE_KB, g)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun rotateKb(g: Int, plain: ByteArray) {
        val kb = ByteArray(AesGcm.KEY_BYTES).also { rng.nextBytes(it) }
        val tmp = File(backupDir, SNAPSHOT + TMP)
        try {
            WrappedKeyFile.writeAtomic(keysDir, WrappedKeyFile.fileName(WrappedKeyFile.PURPOSE_KB, g),
                WrappedKeyFile.seal(wrapper, WrappedKeyFile.PURPOSE_KB, g, kb))
            writeSynced(tmp, SnapshotFormat.seal(kb, g, plain, rng))
            backupKeys.store(g, kb)
            if (!tmp.renameTo(snapshotFile)) throw IOException("snapshot rename failed")
            DirSync.fsync(backupDir)
            WrappedKeyFile.list(keysDir, WrappedKeyFile.PURPOSE_KB).filterKeys { it < g }.values.forEach { it.delete() }
        } finally {
            Bytes.wipe(kb)
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Clears rows written by a failed restore and deletes the DEK it created; snapshot and K_b are kept. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun discardPartial() {
        try {
            store.tx { clearAll() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing more to do; the rows cannot be read without the DEK deleted below.
        }
        WrappedKeyFile.list(keysDir, WrappedKeyFile.PURPOSE_DEK).values.forEach { it.delete() }
        dek.close()
    }

    override fun toString(): String = "SnapshotManager(redacted)"

    private companion object {
        const val SNAPSHOT = "snapshot.bin"
        const val TMP = ".tmp"
        const val DEBOUNCE_MS = 2_000L
        val STALE_AFTER = 24.hours
    }
}

private fun hasCurrentKey(dek: DekManager): Boolean =
    try {
        dek.ring.currentId >= 1
    } catch (@Suppress("SwallowedException") e: IllegalStateException) {
        false
    }

private fun nextGeneration(keysDir: File, atLeast: Int): Int =
    maxOf(atLeast, WrappedKeyFile.list(keysDir, WrappedKeyFile.PURPOSE_KB).keys.maxOrNull() ?: 0) + 1

private fun readSnapshot(f: File): ByteArray? =
    if (f.isFile && f.length() in SnapshotFormat.MIN_BYTES..SnapshotFormat.MAX_BYTES) f.readBytes() else null

private fun writeReplacing(target: File, bytes: ByteArray) {
    val tmp = File(target.parentFile, target.name + ".tmp")
    try {
        writeSynced(tmp, bytes)
        if (!tmp.renameTo(target)) throw IOException("snapshot rename failed")
        DirSync.fsync(target.parentFile)
    } finally {
        if (tmp.exists()) tmp.delete()
    }
}

private fun writeSynced(f: File, bytes: ByteArray) {
    val dir = f.parentFile
    if (dir != null && !dir.isDirectory && !dir.mkdirs()) throw IOException("backup dir unavailable")
    FileOutputStream(f).use {
        it.write(bytes)
        it.fd.sync()
    }
}
