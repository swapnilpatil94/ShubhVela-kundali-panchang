package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.DataLimits
import app.parivaar.panchang.core.data.codec.PayloadMigrations
import app.parivaar.panchang.core.data.codec.SettingsCodec
import app.parivaar.panchang.core.data.crypto.DekRing
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.StoredRow
import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

/**
 * Settings live in one encrypted row (tbl 4, id = 16 zero bytes). An unreadable row reads as defaults in memory
 * only and is flagged by [unreadable]; [update] refuses to overwrite it until [resetUnreadable] (Fable review 3).
 */
internal class SettingsRepositoryImpl(
    store: RecordStore,
    ring: DekRing,
    state: StateFlow<VaultState>,
    rng: SecureRandom,
    migrations: PayloadMigrations = PayloadMigrations.DEFAULT,
) : EncryptedRepo(store, ring, state, rng, migrations), SettingsRepository {
    private val settingsId = RecordId("0".repeat(RecordId.LENGTH))

    private fun decode(row: StoredRow): AppSettings? = open(row, SettingsCodec::fromJson) { _ -> null }

    private val records = observe(Tbl.SETTINGS, SettingsCodec::fromJson) { null }

    override val settings: Flow<AppSettings> = records.map { it.items.firstOrNull() ?: AppSettings() }

    override val unreadable: Flow<Boolean> = records.map { it.unreadable > 0 }

    override suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit> =
        ready {
            tx {
                val row = get(Tbl.SETTINGS, settingsId.toBytes())
                val current =
                    if (row == null) AppSettings() else decode(row) ?: return@tx DataResult.Err(DataError.UNREADABLE)
                val next =
                    try {
                        f(current)
                    } catch (_: IllegalArgumentException) {
                        return@tx DataResult.Err(DataError.VALIDATION)
                    }
                if ((next.localeTag?.length ?: 1) !in 1..DataLimits.LOCALE_MAX) {
                    return@tx DataResult.Err(DataError.VALIDATION)
                }
                put(seal(Tbl.SETTINGS, settingsId, SettingsCodec.toJson(next), pos = 0))
                DataResult.Ok(Unit)
            }
        }

    override suspend fun resetUnreadable(): DataResult<Unit> =
        ready {
            tx {
                val row = get(Tbl.SETTINGS, settingsId.toBytes())
                if (row != null && decode(row) == null) {
                    put(seal(Tbl.SETTINGS, settingsId, SettingsCodec.toJson(AppSettings()), pos = 0))
                }
                DataResult.Ok(Unit)
            }
        }
}
