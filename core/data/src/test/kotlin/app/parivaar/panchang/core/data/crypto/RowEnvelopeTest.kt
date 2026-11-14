package app.parivaar.panchang.core.data.crypto

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RowEnvelopeTest {
    private val rng = SecureRandom()
    private fun key(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }
    private val id = ByteArray(16) { (0xA0 + it).toByte() }
    private val idHex = id.joinToString("") { "%02x".format(it) }
    private val plain = """{"displayName":"Asha Devi","date":"1990-04-02"}""".toByteArray()

    private fun ring(vararg ids: Int): MutableDekRing =
        MutableDekRing().apply { ids.forEach { put(it, key(), makeCurrent = true) } }

    private fun rejected(
        r: DekRing, blob: ByteArray, tbl: Int = 1, rowId: ByteArray = id, ver: Int = 1,
    ): CryptoException {
        val e = assertFailsWith<CryptoException> { RowEnvelope.open(r, tbl, rowId, ver, blob) }
        val m = e.message!!
        assertFalse(m.contains("Asha") || m.contains(idHex) || m.any { it.isDigit() }, "message must be static")
        assertEquals("app.parivaar.panchang.core.data.crypto.CryptoException: $m", e.toString())
        assertNull(e.cause)
        return e
    }

    @Test fun aadLayoutIsExact() {
        val aad = RowEnvelope.aad(tbl = 3, id = id, payloadVer = 0x0102, keyId = 0x0A0B0C0D)
        assertEquals(27, aad.size)
        assertEquals(RowEnvelope.AAD_BYTES, aad.size)
        val expected = "50505231" + "03" + idHex + "0102" + "0a0b0c0d"
        assertEquals(expected, aad.joinToString("") { "%02x".format(it) })
    }

    @Test fun sealLayoutAndRoundTrip() {
        val r = ring(7)
        val blob = RowEnvelope.seal(r, 1, id, 1, plain, rng)
        assertEquals(1 + 4 + 12 + plain.size + 16, blob.size)
        assertEquals(1, blob[0].toInt())
        assertEquals(7L, Bytes.getU32(blob, 1))
        assertEquals(7, RowEnvelope.keyIdOf(blob))
        assertContentEquals(plain, RowEnvelope.open(r, 1, id, 1, blob))
        val again = RowEnvelope.seal(r, 1, id, 1, plain, rng)
        assertFalse(blob.copyOfRange(5, 17).contentEquals(again.copyOfRange(5, 17)), "fresh nonce per seal")
    }

    @Test fun settingsRowWithZeroIdAndEmptyPayload() {
        val r = ring(1)
        val zero = ByteArray(16)
        val blob = RowEnvelope.seal(r, RowEnvelope.TBL_SETTINGS, zero, 1, ByteArray(0), rng)
        assertEquals(RowEnvelope.MIN_BYTES, blob.size)
        assertContentEquals(ByteArray(0), RowEnvelope.open(r, RowEnvelope.TBL_SETTINGS, zero, 1, blob))
    }

    @Test fun flippedBytesFailClosed() {
        val r = ring(1)
        val blob = RowEnvelope.seal(r, 1, id, 1, plain, rng)
        fun flip(i: Int) = blob.copyOf().also { it[i] = (it[i].toInt() xor 0xFF).toByte() }
        assertEquals(CryptoException.Reason.VERSION, rejected(r, flip(0)).reason)
        // keyId: high byte flipped -> out of range, low byte flipped -> unknown key
        assertEquals(CryptoException.Reason.FORMAT, rejected(r, flip(1)).reason)
        assertEquals(CryptoException.Reason.AUTH, rejected(r, flip(4)).reason)
        val positions = listOf(5, 16, 17, blob.size - 17, blob.size - 1) // nonce first/last, ct first/last, tag last
        for (i in positions) assertEquals(CryptoException.Reason.AUTH, rejected(r, flip(i)).reason)
        for (i in 5 until blob.size) assertEquals(CryptoException.Reason.AUTH, rejected(r, flip(i)).reason)
    }

    @Test fun truncationsEmptyAndTrailingByteFailClosed() {
        val r = ring(1)
        val blob = RowEnvelope.seal(r, 1, id, 1, plain, rng)
        val lengths = (0..minOf(blob.size - 1, RowEnvelope.HEADER_BYTES + 16)) + (blob.size - 1)
        for (len in lengths) {
            val want = if (len < RowEnvelope.MIN_BYTES) CryptoException.Reason.FORMAT else CryptoException.Reason.AUTH
            assertEquals(want, rejected(r, blob.copyOf(len)).reason, "len=$len")
        }
        assertEquals(CryptoException.Reason.FORMAT, rejected(r, ByteArray(0)).reason)
        assertEquals(CryptoException.Reason.AUTH, rejected(r, blob + byteArrayOf(0)).reason)
    }

    @Test fun unknownVersionIsVersionError() {
        val r = ring(1)
        val blob = RowEnvelope.seal(r, 1, id, 1, plain, rng)
        for (v in listOf(0, 2, 255)) {
            val b = blob.copyOf().also { it[0] = v.toByte() }
            assertEquals(CryptoException.Reason.VERSION, rejected(r, b).reason)
        }
        assertEquals(CryptoException.Reason.VERSION, rejected(r, byteArrayOf(2)).reason)
    }

    @Test fun rowMovedOrRelabelledFailsAuth() {
        val r = ring(1)
        val blob = RowEnvelope.seal(r, 1, id, 1, plain, rng)
        val otherId = id.copyOf().also { it[15] = 0 }
        assertEquals(CryptoException.Reason.AUTH, rejected(r, blob, rowId = otherId).reason)
        assertEquals(CryptoException.Reason.AUTH, rejected(r, blob, tbl = 2).reason)
        assertEquals(CryptoException.Reason.AUTH, rejected(r, blob, ver = 2).reason)
        // keyId relabelled to another generation that exists in the ring
        r.put(2, key(), makeCurrent = false)
        val relabelled = blob.copyOf().also { Bytes.putU32(it, 1, 2L) }
        assertEquals(CryptoException.Reason.AUTH, rejected(r, relabelled).reason)
        // out-of-range clear columns read from a tampered DB are FORMAT, never a crash
        assertEquals(CryptoException.Reason.FORMAT, rejected(r, blob, tbl = 9).reason)
        assertEquals(CryptoException.Reason.FORMAT, rejected(r, blob, ver = 70_000).reason)
        assertEquals(CryptoException.Reason.FORMAT, rejected(r, blob, rowId = ByteArray(15)).reason)
        val zeroKeyId = blob.copyOf().also { Bytes.putU32(it, 1, 0L) }
        assertEquals(CryptoException.Reason.FORMAT, rejected(r, zeroKeyId).reason)
    }

    @Test fun rotationKeepsOldRowsReadable() {
        val r = ring(1)
        val old = RowEnvelope.seal(r, 2, id, 1, plain, rng)
        r.put(2, key(), makeCurrent = true)
        val fresh = RowEnvelope.seal(r, 2, id, 1, plain, rng)
        assertEquals(1, RowEnvelope.keyIdOf(old))
        assertEquals(2, RowEnvelope.keyIdOf(fresh))
        assertContentEquals(plain, RowEnvelope.open(r, 2, id, 1, old))
        r.remove(1)
        assertEquals(CryptoException.Reason.AUTH, rejected(r, old, tbl = 2).reason)
        assertContentEquals(plain, RowEnvelope.open(r, 2, id, 1, fresh))
    }

    @Test fun dekRingWipesAndRedacts() {
        val r = MutableDekRing()
        assertFailsWith<IllegalStateException> { r.currentId }
        val k1 = key()
        val k2 = key()
        r.put(1, k1, makeCurrent = true)
        r.put(2, k2, makeCurrent = true)
        assertEquals(setOf(1, 2), r.ids)
        assertFailsWith<IllegalStateException> { r.remove(2) }
        r.remove(1)
        assertTrue(k1.all { it == 0.toByte() })
        val replacement = key()
        r.put(2, replacement, makeCurrent = true)
        assertTrue(k2.all { it == 0.toByte() }, "replaced key wiped")
        assertEquals("DekRing(redacted)", r.toString())
        r.close()
        assertTrue(replacement.all { it == 0.toByte() })
        assertNull(r.key(2))
        assertFailsWith<IllegalStateException> { r.currentId }
        assertFailsWith<IllegalArgumentException> { r.put(0, key(), makeCurrent = true) }
        assertFailsWith<IllegalArgumentException> { r.put(1, ByteArray(16), makeCurrent = true) }
    }
}
