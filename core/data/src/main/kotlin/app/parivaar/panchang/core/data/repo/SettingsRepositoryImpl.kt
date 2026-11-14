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

/** Settings live in one encrypted row (tbl 4, id = 16 zero bytes). An unreadable row reads as defaults. */
internal class SettingsRepositoryImpl(
    store: RecordStore,
    ring: DekRing,
    state: StateFlow<VaultState>,
    rng: SecureRandom,
    migrations: PayloadMigrations = PayloadMigrations.DEFAULT,
) : EncryptedRepo(store, ring, state, rng, migrations), SettingsRepository {
    private val settingsId = RecordId("0".repeat(RecordId.LENGTH))

    private fun read(row: StoredRow?): AppSettings =
        row?.let { open(it, SettingsCodec::fromJson) { _ -> null } } ?: AppSettings()

    override val settings: Flow<AppSettings> =
        observe(Tbl.SETTINGS, SettingsCodec::fromJson) { null }.map { it.items.firstOrNull() ?: AppSettings() }

    override suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit> =
        ready {
            tx {
                val current = read(get(Tbl.SETTINGS, settingsId.toBytes()))
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
}
