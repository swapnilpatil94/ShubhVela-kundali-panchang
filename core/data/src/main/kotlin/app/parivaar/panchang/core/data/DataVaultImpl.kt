package app.parivaar.panchang.core.data

import app.parivaar.panchang.core.data.keys.DekManager
import app.parivaar.panchang.core.data.keys.KeyState
import app.parivaar.panchang.core.data.store.RecordStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * New-device restore hook (implemented by the snapshot manager, C3). [canRestore] is true when a snapshot file
 * exists and the backup key can be retrieved. [restore] performs the whole restore (spec section 5) and returns
 * false on any failure, after cleaning up its partial work and keeping the snapshot and K_b.
 */
internal interface SnapshotRestorer {
    fun canRestore(): Boolean

    suspend fun restore(): Boolean

    object None : SnapshotRestorer {
        override fun canRestore(): Boolean = false

        override suspend fun restore(): Boolean = false
    }
}

/**
 * Maps the key state to [VaultState] (spec section 4). Never wipes or overwrites a key by itself: the only
 * destructive path is [startFresh], which the user chooses. [wiper] is a provider because the wiper itself
 * needs the vault's key holder.
 */
internal class DataVaultImpl(
    private val store: RecordStore,
    private val keys: DekManager,
    private val wiper: () -> DataWiper,
    private val restorer: SnapshotRestorer = SnapshotRestorer.None,
) : DataVault {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<VaultState>(VaultState.Opening)

    override val state: StateFlow<VaultState> get() = mutableState

    override suspend fun open(): VaultState =
        mutex.withLock {
            val next = resolve()
            mutableState.value = next
            next
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolve(): VaultState =
        try {
            store.reopen() // a finished wipe closed the store; opening the vault allows transactions again
            when (keys.open(store)) {
                KeyState.Ready -> VaultState.Ready
                KeyState.Lost -> VaultState.KeyLost
                KeyState.Unavailable -> VaultState.KeyUnavailable
                KeyState.Empty -> fromEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Store failure (for example a database newer than this app): nothing is wiped, data stays on disk.
            VaultState.KeyUnavailable
        }

    private suspend fun fromEmpty(): VaultState {
        if (restorer.canRestore()) {
            mutableState.value = VaultState.Restoring
            return if (restorer.restore()) VaultState.Ready else VaultState.KeyLost
        }
        return when (keys.createFresh()) {
            KeyState.Ready -> VaultState.Ready
            KeyState.Unavailable -> VaultState.KeyUnavailable
            KeyState.Lost, KeyState.Empty -> VaultState.KeyLost
        }
    }

    /** Called by the wiper before anything is deleted: repositories answer LOCKED until [open] runs again. */
    fun markClosed() {
        mutableState.value = VaultState.Opening
    }

    override suspend fun startFresh(): DataResult<Unit> {
        val wiped = wiper().deleteAll()
        if (wiped is DataResult.Err) return wiped
        return when (open()) {
            VaultState.Ready -> DataResult.Ok(Unit)
            VaultState.KeyUnavailable -> DataResult.Err(DataError.KEY_UNAVAILABLE)
            else -> DataResult.Err(DataError.KEY_LOST)
        }
    }
}
