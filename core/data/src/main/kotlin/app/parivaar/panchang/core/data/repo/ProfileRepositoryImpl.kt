package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.data.Records
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.DataLimits
import app.parivaar.panchang.core.data.codec.PayloadMigrations
import app.parivaar.panchang.core.data.codec.ProfileCodec
import app.parivaar.panchang.core.data.crypto.DekRing
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.StoredRow
import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

internal class ProfileRepositoryImpl(
    store: RecordStore,
    ring: DekRing,
    state: StateFlow<VaultState>,
    rng: SecureRandom,
    migrations: PayloadMigrations = PayloadMigrations.DEFAULT,
) : EncryptedRepo(store, ring, state, rng, migrations), ProfileRepository {
    private fun open(row: StoredRow): Profile? =
        open(row, ProfileCodec::fromJson) { it.id }

    override fun observeAll(): Flow<Records<Profile>> = observe(Tbl.PROFILE, ProfileCodec::fromJson) { it.id }

    override suspend fun get(id: RecordId): DataResult<Profile> =
        ready {
            val row = tx { get(Tbl.PROFILE, id.toBytes()) } ?: return@ready DataResult.Err(DataError.NOT_FOUND)
            open(row)?.let { DataResult.Ok(it) } ?: DataResult.Err(DataError.IO)
        }

    override suspend fun upsert(p: Profile): DataResult<Unit> =
        ready {
            if (p.displayName.length !in 1..DataLimits.NAME_MAX || (p.notes?.length ?: 0) > DataLimits.NOTES_MAX ||
                !ProfileCodec.isValid(p)
            ) {
                return@ready DataResult.Err(DataError.VALIDATION)
            }
            tx {
                val idBytes = p.id.toBytes()
                val existing = get(Tbl.PROFILE, idBytes)
                val rows = list(Tbl.PROFILE)
                val err = checkRules(p, existing != null, rows)
                if (err != null) return@tx DataResult.Err(err)
                val pos = existing?.pos ?: ((rows.maxOfOrNull { it.pos } ?: -1) + 1)
                put(seal(Tbl.PROFILE, p.id, ProfileCodec.toJson(p), pos))
                DataResult.Ok(Unit)
            }
        }

    private fun checkRules(p: Profile, exists: Boolean, rows: List<StoredRow>): DataError? {
        if (!exists && rows.size >= DataLimits.MAX_PROFILES) return DataError.LIMIT
        val otherSelf =
            p.relation == Relation.SELF &&
                rows.any { r ->
                    !r.id.contentEquals(p.id.toBytes()) && open(r)?.relation == Relation.SELF
                }
        return if (otherSelf) DataError.SELF_EXISTS else null
    }

    override suspend fun delete(id: RecordId): DataResult<Unit> =
        ready {
            if (tx { delete(Tbl.PROFILE, id.toBytes()) }) DataResult.Ok(Unit) else DataResult.Err(DataError.NOT_FOUND)
        }

    /** Listed ids take positions 0..n-1 in order; profiles not listed follow in their old order. */
    override suspend fun reorder(ids: List<RecordId>): DataResult<Unit> =
        ready {
            if (ids.toSet().size != ids.size) return@ready DataResult.Err(DataError.VALIDATION)
            tx {
                val rows = list(Tbl.PROFILE)
                val byId = rows.associateBy { bytesToId(it.id) }
                if (ids.any { it !in byId }) return@tx DataResult.Err(DataError.NOT_FOUND)
                val rest = rows.filter { bytesToId(it.id) !in ids }
                val ordered = ids.map { byId.getValue(it) } + rest
                ordered.forEachIndexed { i, r -> put(StoredRow(r.tbl, r.id, i, r.ver, r.blob)) }
                DataResult.Ok(Unit)
            }
        }
}
