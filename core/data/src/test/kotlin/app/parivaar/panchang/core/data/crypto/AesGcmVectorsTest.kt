package app.parivaar.panchang.core.data.crypto

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Known answers from the NIST GCM spec (McGrew-Viega, "The Galois/Counter Mode of Operation"), as
 * quoted in docs/specs/M06.md section 6. TC16 (with AAD) is left to the owner (VERIFY); AAD binding is
 * covered by the tamper cases below and in RowEnvelopeTest.
 */
class AesGcmVectorsTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val zeroKey = ByteArray(32)
    private val zeroNonce = ByteArray(12)

    @Test fun nistTestCase13() {
        val out = AesGcm.seal(zeroKey, zeroNonce, ByteArray(0), ByteArray(0))
        assertContentEquals(hex("530f8afbc74536b9a963b4f1c4cb738b"), out)
        assertContentEquals(ByteArray(0), AesGcm.open(zeroKey, zeroNonce, out, ByteArray(0)))
    }

    @Test fun nistTestCase14() {
        val out = AesGcm.seal(zeroKey, zeroNonce, ByteArray(16), ByteArray(0))
        val expected = hex("cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919")
        assertContentEquals(expected, out)
        assertContentEquals(ByteArray(16), AesGcm.open(zeroKey, zeroNonce, out, ByteArray(0)))
    }

    @Test fun roundTripWithAadAndFreshNonces() {
        val rng = SecureRandom()
        val key = ByteArray(32).also { rng.nextBytes(it) }
        val aad = "header".toByteArray()
        val msg = "family record".toByteArray()
        val a = AesGcm.encrypt(key, msg, aad, rng)
        val b = AesGcm.encrypt(key, msg, aad, rng)
        assertEquals(12, a.nonce.size)
        assertFalse(a.nonce.contentEquals(b.nonce))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
        assertContentEquals(msg, AesGcm.open(key, a.nonce, a.ciphertext, aad))
        assertEquals("Sealed(redacted)", a.toString())
    }

    @Test fun noncesAreUniqueOverManyDraws() {
        val rng = SecureRandom()
        val seen = HashSet<String>()
        repeat(10_000) { assertTrue(seen.add(AesGcm.newNonce(rng).joinToString(",") { b -> b.toString() })) }
    }

    @Test fun tamperFailsClosedWithStaticMessage() {
        val rng = SecureRandom()
        val key = ByteArray(32).also { rng.nextBytes(it) }
        val aad = "aad".toByteArray()
        val s = AesGcm.encrypt(key, "secret-name".toByteArray(), aad, rng)
        fun rejected(k: ByteArray, n: ByteArray, c: ByteArray, a: ByteArray): CryptoException =
            assertFailsWith { AesGcm.open(k, n, c, a) }
        for (i in s.ciphertext.indices) {
            val c = s.ciphertext.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertEquals(CryptoException.Reason.AUTH, rejected(key, s.nonce, c, aad).reason)
        }
        for (i in s.nonce.indices) {
            val n = s.nonce.copyOf().also { it[i] = (it[i].toInt() xor 0x80).toByte() }
            assertEquals(CryptoException.Reason.AUTH, rejected(key, n, s.ciphertext, aad).reason)
        }
        assertEquals(CryptoException.Reason.AUTH, rejected(key, s.nonce, s.ciphertext, "aaD".toByteArray()).reason)
        assertEquals(CryptoException.Reason.AUTH, rejected(key, s.nonce, s.ciphertext, ByteArray(0)).reason)
        val wrongKey = key.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertEquals(CryptoException.Reason.AUTH, rejected(wrongKey, s.nonce, s.ciphertext, aad).reason)
        assertEquals(
            CryptoException.Reason.AUTH,
            rejected(key, s.nonce, s.ciphertext + byteArrayOf(0), aad).reason,
        )
        for (len in 0 until s.ciphertext.size) {
            val e = rejected(key, s.nonce, s.ciphertext.copyOf(len), aad)
            val want = if (len < AesGcm.TAG_BYTES) CryptoException.Reason.FORMAT else CryptoException.Reason.AUTH
            assertEquals(want, e.reason)
            assertFalse(e.message!!.contains("secret") || e.message!!.any { it.isDigit() })
            assertEquals(null, e.cause)
        }
        assertEquals(CryptoException.Reason.FORMAT, rejected(ByteArray(16), s.nonce, s.ciphertext, aad).reason)
        assertEquals(CryptoException.Reason.FORMAT, rejected(key, ByteArray(16), s.ciphertext, aad).reason)
    }

    @Test fun sealRejectsBadKeyAndNonceLengths() {
        assertFailsWith<IllegalArgumentException> { AesGcm.seal(ByteArray(16), zeroNonce, ByteArray(1), ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { AesGcm.seal(zeroKey, ByteArray(16), ByteArray(1), ByteArray(0)) }
    }

    @Test fun bytesHelpers() {
        val b = ByteArray(6)
        Bytes.putU32(b, 0, 0xFFFF_FFFEL)
        Bytes.putU16(b, 4, 0xABCD)
        assertEquals(0xFFFF_FFFEL, Bytes.getU32(b, 0))
        assertEquals(0xABCD, Bytes.getU16(b, 4))
        assertFailsWith<IllegalArgumentException> { Bytes.putU16(b, 0, 0x10000) }
        assertFailsWith<IllegalArgumentException> { Bytes.putU32(b, 0, -1L) }
        assertTrue(Bytes.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertFalse(Bytes.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 3)))
        assertFalse(Bytes.constantTimeEquals(byteArrayOf(1), byteArrayOf(1, 2)))
        Bytes.wipe(b)
        assertContentEquals(ByteArray(6), b)
        val c = "pass".toCharArray().also { Bytes.wipe(it) }
        assertTrue(c.all { it == '\u0000' })
    }
}
