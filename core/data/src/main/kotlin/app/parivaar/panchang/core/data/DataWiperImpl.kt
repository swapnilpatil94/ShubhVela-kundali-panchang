package app.parivaar.panchang.core.data

import app.parivaar.panchang.core.data.backup.BackupKeyStore
import app.parivaar.panchang.core.data.keys.DekManager
import app.parivaar.panchang.core.data.keys.KeyLostException
import app.parivaar.panchang.core.data.keys.KeyUnavailableException
import app.parivaar.panchang.core.data.keys.KeyWrapper
import app.parivaar.panchang.core.data.store.RecordStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Delete all data (DPDP erase; spec section 5). First [lockVault] (repositories answer LOCKED from here on), then
 * KEK alias, wrapped keys, database ([RecordStore.closeAndDelete]: waits for in-flight transactions under the store
 * lock and keeps the store closed so nothing can recreate the database), backup snapshot files, share cache,
 * off-device backup key, in-memory keys. Continues past errors and reports the first one. Idempotent. The
 * non-personal `geo_places.db` is kept.
 */
internal class DataWiperImpl(
    private val wrapper: KeyWrapper,
    private val keysDir: File,
    private val store: RecordStore,
    private val deleteDatabase: () -> Boolean,
    private val backupDir: File,
    private val shareCacheDir: File,
    private val backupKeys: BackupKeyStore,
    private val dek: DekManager,
    private val lockVault: () -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DataWiper {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteAll(): DataResult<Unit> =
        withContext(io) {
            var first: DataError? = null
            fun step(failure: DataError?) {
                if (first == null) first = failure
            }

            fun attempt(error: DataError, block: () -> Boolean) =
                step(
                    try {
                        if (block()) null else error
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error
                    },
                )

            attempt(DataError.IO) { lockVault().let { true } }
            step(deleteKek())
            attempt(DataError.IO) { clearDir(keysDir) }
            step(deleteStore())
            attempt(DataError.IO) { clearDir(backupDir) }
            attempt(DataError.IO) { clearDir(shareCacheDir) }
            attempt(DataError.IO) { backupKeys.clear().let { true } }
            attempt(DataError.IO) { dek.close().let { true } }
            first?.let { DataResult.Err(it) } ?: DataResult.Ok(Unit)
        }

    /** Closes the store under its lock (waits for in-flight writes) and deletes the database files. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun deleteStore(): DataError? =
        try {
            if (store.closeAndDelete(deleteDatabase)) null else DataError.IO
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DataError.IO
        }

    /** A key that is already gone or invalidated counts as deleted; a transient keystore failure does not. */
    private fun deleteKek(): DataError? =
        try {
            wrapper.delete()
            null
        } catch (_: KeyLostException) {
            null
        } catch (_: KeyUnavailableException) {
            DataError.KEY_UNAVAILABLE
        }

    /** Deletes everything inside [dir] (not the directory). True if nothing is left. */
    private fun clearDir(dir: File): Boolean {
        val children = dir.listFiles() ?: return !dir.exists()
        var ok = true
        for (c in children) ok = c.deleteRecursively() && ok
        return ok
    }
}
