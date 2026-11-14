package app.parivaar.panchang.core.data

import android.content.Context
import app.parivaar.panchang.core.data.backup.FamilyTransferImpl
import app.parivaar.panchang.core.data.backup.SnapshotManager
import app.parivaar.panchang.core.data.backup.UnavailableBackupKeyStore
import app.parivaar.panchang.core.data.keys.DekManager
import app.parivaar.panchang.core.data.keys.KeystoreKeyWrapper
import app.parivaar.panchang.core.data.repo.BondRepositoryImpl
import app.parivaar.panchang.core.data.repo.PlaceRepositoryImpl
import app.parivaar.panchang.core.data.repo.ProfileRepositoryImpl
import app.parivaar.panchang.core.data.repo.SettingsRepositoryImpl
import app.parivaar.panchang.core.data.store.Schema
import app.parivaar.panchang.core.data.store.SqliteRecordStore
import java.io.File
import java.security.SecureRandom
import kotlin.time.Clock

/**
 * Public entry point to the encrypted family store: builds the internal graph (store, keys, repositories,
 * transfer, wiper) without touching disk or the Keystore. Nothing opens until [start] runs, and every
 * repository answers LOCKED until then.
 */
class DataGraph private constructor(
    val vault: DataVault,
    val profiles: ProfileRepository,
    val places: PlaceRepository,
    val bonds: BondRepository,
    val settings: SettingsRepository,
    val transfer: FamilyTransfer,
    val wiper: DataWiper,
    private val snapshots: SnapshotManager,
) {
    /**
     * Opens the vault and, when ready, keeps the encrypted snapshot current. Suspends for as long as snapshots are
     * watched (returns at once while no backup key store is bound, as in M06). Launch it from an application
     * scope on `Dispatchers.IO`, never on the main thread.
     */
    suspend fun start() {
        if (vault.open() != VaultState.Ready) return
        snapshots.writeIfStale()
        snapshots.watch()
    }

    companion object {
        private const val BACKUP_DIR = "backup"
        private const val KEYS_DIR = "keys"
        private const val SHARE_DIR = "share"

        fun create(context: Context): DataGraph {
            val app = context.applicationContext
            val rng = SecureRandom()
            val clock = { Clock.System.now() }
            val keysDir = File(app.noBackupFilesDir, KEYS_DIR)
            val backupDir = File(app.filesDir, BACKUP_DIR)
            val store = SqliteRecordStore(app)
            val wrapper = KeystoreKeyWrapper()
            val dek = DekManager(keysDir, wrapper, rng)
            val backupKeys = UnavailableBackupKeyStore
            val snapshots = SnapshotManager(backupDir, keysDir, store, dek, wrapper, backupKeys, rng, clock)
            lateinit var vault: DataVaultImpl
            val wiper =
                DataWiperImpl(
                    wrapper = wrapper,
                    keysDir = keysDir,
                    store = store,
                    deleteDatabase = { app.deleteDatabase(Schema.DB_NAME) },
                    backupDir = backupDir,
                    shareCacheDir = File(app.cacheDir, SHARE_DIR),
                    backupKeys = backupKeys,
                    dek = dek,
                    lockVault = { vault.markClosed() },
                )
            vault = DataVaultImpl(store, dek, { wiper }, snapshots)
            return DataGraph(
                vault = vault,
                profiles = ProfileRepositoryImpl(store, dek.ring, vault.state, rng),
                places = PlaceRepositoryImpl(store, dek.ring, vault.state, rng),
                bonds = BondRepositoryImpl(store, dek.ring, vault.state, rng),
                settings = SettingsRepositoryImpl(store, dek.ring, vault.state, rng),
                transfer = FamilyTransferImpl(store, dek.ring, vault.state, rng),
                wiper = wiper,
                snapshots = snapshots,
            )
        }
    }
}
