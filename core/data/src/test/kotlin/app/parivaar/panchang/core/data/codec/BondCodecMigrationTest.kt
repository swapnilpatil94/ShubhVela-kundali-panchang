package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import app.parivaar.panchang.core.model.profile.RecordId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** M12: rows stored before `vs`, `profileRef` and `pv` existed still load; new keys round-trip. */
class BondCodecMigrationTest {
    private val oldRow =
        """{"id":"00000000000000000000000000000004","traits":{"name":"Gamma","mn":26,"mp":4,"mr":11,"lg":null,""" +
            """"mars":2,"ven":0,"sun":11,"gana":1,"yoni":2,"nadi":3},"kind":"friend",""" +
            """"createdAt":"2024-01-01T00:00:00Z"}"""

    @Test
    fun anOldRowWithoutM12KeysStillLoads() {
        val b = BondCodec.fromJson(parseJson(oldRow.encodeToByteArray()))
        assertNull(b.traits.vashya)
        assertNull(b.profileRef)
        assertEquals(1, b.payloadVersion)
        assertEquals(F.bond, b)
    }

    @Test
    fun aPreM12BondEncodesByteIdenticallyToBefore() {
        assertEquals(oldRow, BondCodec.toJson(F.bond).toString())
    }

    @Test
    fun m12KeysRoundTrip() {
        val b = F.bond.copy(
            traits = F.bond.traits.copy(vashya = 3),
            profileRef = RecordId("0000000000000000000000000000000a"),
            payloadVersion = 1,
        )
        assertEquals(b, BondCodec.fromJson(BondCodec.toJson(b)))
        val v2 = b.copy(payloadVersion = 2)
        assertEquals(v2, BondCodec.fromJson(BondCodec.toJson(v2)))
    }

    @Test
    fun outOfRangeVashyaOrUnknownKeysAreRejected() {
        val badVs = oldRow.replace("\"nadi\":3", "\"nadi\":3,\"vs\":5")
        assertFailsWith<CodecException> { BondCodec.fromJson(parseJson(badVs.encodeToByteArray())) }
        val extra = oldRow.replace("\"kind\"", "\"zz\":1,\"kind\"")
        assertFailsWith<CodecException> { BondCodec.fromJson(parseJson(extra.encodeToByteArray())) }
        assertFalse("vs" in BondCodec.toJson(F.bond).toString())
    }
}
