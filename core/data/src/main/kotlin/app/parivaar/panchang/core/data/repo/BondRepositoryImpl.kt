package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.BondRepository
import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.Records
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.BondCodec
import app.parivaar.panchang.core.data.codec.DataLimits
import app.parivaar.panchang.core.data.codec.PayloadMigrations
import app.parivaar.panchang.core.data.crypto.DekRing
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.profile.RecordId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

internal class BondRepositoryImpl(
    store: RecordStore,
    ring: DekRing,
    state: StateFlow<VaultState>,
    rng: SecureRandom,
    migrations: PayloadMigrations = PayloadMigrations.DEFAULT,
) : EncryptedRepo(store, ring, state, rng, migrations), BondRepository {
    override fun observeAll(): Flow<Records<Bond>> = observe(Tbl.BOND, BondCodec::fromJson) { it.id }

    override suspend fun upsert(b: Bond): DataResult<Unit> =
        ready {
            tx {
                val exists = get(Tbl.BOND, b.id.toBytes()) != null
                if (!exists && count(Tbl.BOND) >= DataLimits.MAX_BONDS) {
                    return@tx DataResult.Err(DataError.LIMIT)
                }
                put(seal(Tbl.BOND, b.id, BondCodec.toJson(b), pos = 0))
                DataResult.Ok(Unit)
            }
        }

    override suspend fun delete(id: RecordId): DataResult<Unit> =
        ready {
            if (tx { delete(Tbl.BOND, id.toBytes()) }) DataResult.Ok(Unit) else DataResult.Err(DataError.NOT_FOUND)
        }
}
