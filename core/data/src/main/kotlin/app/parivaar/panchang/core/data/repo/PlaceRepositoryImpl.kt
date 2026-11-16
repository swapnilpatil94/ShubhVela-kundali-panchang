package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.PlaceRepository
import app.parivaar.panchang.core.data.Records
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.DataLimits
import app.parivaar.panchang.core.data.codec.PayloadMigrations
import app.parivaar.panchang.core.data.codec.PlaceCodec
import app.parivaar.panchang.core.data.crypto.DekRing
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.profile.RecordId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

internal class PlaceRepositoryImpl(
    store: RecordStore,
    ring: DekRing,
    state: StateFlow<VaultState>,
    rng: SecureRandom,
    migrations: PayloadMigrations = PayloadMigrations.DEFAULT,
) : EncryptedRepo(store, ring, state, rng, migrations), PlaceRepository {
    override fun observeAll(): Flow<Records<SavedPlace>> = observe(Tbl.PLACE, PlaceCodec::fromJson) { it.id }

    override suspend fun upsert(p: SavedPlace): DataResult<Unit> =
        ready {
            if (!PlaceCodec.isValid(p)) return@ready DataResult.Err(DataError.VALIDATION)
            tx {
                val exists = get(Tbl.PLACE, p.id.toBytes()) != null
                if (!exists && count(Tbl.PLACE) >= DataLimits.MAX_PLACES) {
                    return@tx DataResult.Err(DataError.LIMIT)
                }
                put(seal(Tbl.PLACE, p.id, PlaceCodec.toJson(p), pos = 0))
                DataResult.Ok(Unit)
            }
        }

    override suspend fun delete(id: RecordId): DataResult<Unit> =
        ready {
            if (tx { delete(Tbl.PLACE, id.toBytes()) }) DataResult.Ok(Unit) else DataResult.Err(DataError.NOT_FOUND)
        }
}
