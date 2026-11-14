package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import app.parivaar.panchang.core.data.codec.PayloadBytes
import app.parivaar.panchang.core.data.codec.ProfileCodec
import app.parivaar.panchang.core.data.crypto.RowEnvelope
import app.parivaar.panchang.core.data.store.StoredRow
import app.parivaar.panchang.core.data.store.Tbl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RowTamperTest {
    private fun profileBytes() = PayloadBytes.encode(ProfileCodec.toJson(F.profileA))

    private val rowId = F.profileA.id.toBytes()

    private suspend fun setup(): Pair<RepoTestKit, StoredRow> {
        val k = RepoTestKit()
        k.profiles.upsert(F.profileA)
        return k to k.store.dump().single()
    }

    private suspend fun replaceWith(k: RepoTestKit, old: StoredRow, new: StoredRow) {
        k.store.tx {
            delete(old.tbl, old.id)
            put(new)
        }
    }

    private suspend fun assertRejected(k: RepoTestKit, new: StoredRow, old: StoredRow) {
        replaceWith(k, old, new)
        val r = k.profiles.observeAll().first()
        assertTrue(r.items.isEmpty())
        assertEquals(1, r.unreadable)
        if (new.tbl == Tbl.PROFILE && new.id.contentEquals(old.id)) {
            assertEquals(DataResult.Err(DataError.IO), k.profiles.get(F.profileA.id))
        }
        replaceWith(k, new, old)
        assertEquals(listOf(F.profileA), k.profiles.observeAll().first().items)
    }

    @Test
    fun everyByteFlipIsRejected() = runBlocking {
        val (k, row) = setup()
        for (i in row.blob.indices) {
            val b = row.blob.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertRejected(k, StoredRow(row.tbl, row.id, row.pos, row.ver, b), row)
        }
    }

    @Test
    fun everyTruncationAndExtraByteIsRejected() = runBlocking {
        val (k, row) = setup()
        for (n in row.blob.indices) {
            assertRejected(k, StoredRow(row.tbl, row.id, row.pos, row.ver, row.blob.copyOf(n)), row)
        }
        assertRejected(k, StoredRow(row.tbl, row.id, row.pos, row.ver, row.blob + 0), row)
    }

    @Test
    fun rowMovedToAnotherIdOrTableIsRejected() = runBlocking {
        val (k, row) = setup()
        val otherId = F.id(77).toBytes()
        assertRejected(k, StoredRow(row.tbl, otherId, row.pos, row.ver, row.blob), row)
        replaceWith(k, row, StoredRow(Tbl.PLACE, row.id, 0, row.ver, row.blob))
        assertEquals(1, k.places.observeAll().first().unreadable)
        assertEquals(0, k.profiles.observeAll().first().unreadable)
    }

    @Test
    fun changedVersionOrKeyIdIsRejected() = runBlocking {
        val (k, row) = setup()
        assertRejected(k, StoredRow(row.tbl, row.id, row.pos, 2, row.blob), row)
        assertRejected(k, StoredRow(row.tbl, row.id, row.pos, 0, row.blob), row)
        k.ring.put(2, ByteArray(32) { 0x43 }, makeCurrent = false)
        val b = row.blob.copyOf().also { it[4] = 2 }
        assertRejected(k, StoredRow(row.tbl, row.id, row.pos, row.ver, b), row)
        val unknown = row.blob.copyOf().also { it[4] = 9 }
        assertRejected(k, StoredRow(row.tbl, row.id, row.pos, row.ver, unknown), row)
    }

    @Test
    fun rowSealedUnderUnknownKeyIsUnreadableNotFatal() = runBlocking {
        val (k, row) = setup()
        val other = app.parivaar.panchang.core.data.crypto.MutableDekRing()
        other.put(1, ByteArray(32) { 0x55 }, makeCurrent = true)
        val blob = RowEnvelope.seal(other, row.tbl, row.id, row.ver, profileBytes(), k.rng)
        assertRejected(k, StoredRow(row.tbl, row.id, row.pos, row.ver, blob), row)
    }

    @Test
    fun payloadIdMustEqualRowId() = runBlocking {
        val (k, row) = setup()
        val otherId = F.id(88).toBytes()
        // Validly sealed under the right key and AAD for otherId, but the payload says it is profile A.
        val blob = RowEnvelope.seal(k.ring, Tbl.PROFILE, otherId, row.ver, profileBytes(), k.rng)
        k.store.tx { put(StoredRow(Tbl.PROFILE, otherId, 1, row.ver, blob)) }
        val r = k.profiles.observeAll().first()
        assertEquals(listOf(F.profileA), r.items)
        assertEquals(1, r.unreadable)
    }

    @Test
    fun undecodablePlaintextIsUnreadable() = runBlocking {
        val (k, row) = setup()
        val blob = RowEnvelope.seal(k.ring, Tbl.PROFILE, rowId, row.ver, "{\"id\":1}".toByteArray(), k.rng)
        assertRejected(k, StoredRow(row.tbl, row.id, row.pos, row.ver, blob), row)
    }

    @Test
    fun readingNeverModifiesTheStore() = runBlocking {
        val (k, row) = setup()
        k.store.tx { put(StoredRow(row.tbl, F.id(5).toBytes(), 0, 1, ByteArray(80))) }
        val before = k.store.dump()
        k.profiles.observeAll().first()
        assertEquals(before, k.store.dump())
    }
}
