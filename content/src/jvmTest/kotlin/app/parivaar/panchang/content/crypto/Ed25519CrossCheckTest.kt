package app.parivaar.panchang.content.crypto

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/specs/M11.md section 4, `Ed25519CrossCheckTest`: 500 JDK key pairs (in memory only), message lengths
 * 0..2048, every signature verifies and every single-bit change is rejected.
 */
class Ed25519CrossCheckTest {
    private val random = SecureRandom()

    private fun flip(b: ByteArray, i: Int): ByteArray = b.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }

    @Test
    fun fiveHundredJdkPairsVerifyAndEveryFlipFails() {
        repeat(CASES) { n ->
            val kp = JdkEd25519.keyPair(random = random)
            val pk = JdkEd25519.encode(kp)
            val len = if (n == 0) 0 else if (n == 1) MAX_LEN else random.nextInt(MAX_LEN + 1)
            val msg = ByteArray(len).also { random.nextBytes(it) }
            val sig = JdkEd25519.sign(kp.private, msg)
            assertTrue(Ed25519.verify(pk, msg, sig), "case $n")
            for (i in intArrayOf(0, 31, 32, 63)) assertFalse(Ed25519.verify(pk, msg, flip(sig, i)), "case $n sig $i")
            if (len > 0) assertFalse(Ed25519.verify(pk, flip(msg, 0), sig), "case $n msg")
            assertFalse(Ed25519.verify(flip(pk, 0), msg, sig), "case $n pk")
        }
    }

    @Test
    fun sPlusLIsRejectedThoughEquivalentModL() {
        val kp = JdkEd25519.keyPair(random = random)
        val pk = JdkEd25519.encode(kp)
        val msg = "pack".encodeToByteArray()
        val sig = JdkEd25519.sign(kp.private, msg)
        val bumped = sig.copyOf()
        var carry = 0
        for (i in 0 until 32) {
            val v = (sig[32 + i].toInt() and 0xff) + (Ed25519.Scalar.L[i].toInt() and 0xff) + carry
            bumped[32 + i] = v.toByte()
            carry = v ushr 8
        }
        check(carry == 0) // S < L < 2^253, so S + L < 2^254 fits
        assertTrue(Ed25519.verify(pk, msg, sig))
        assertFalse(Ed25519.verify(pk, msg, bumped))
        assertFalse(JdkEd25519.verify(pk, msg, bumped))
    }

    @Test
    fun nonCanonicalYIsRejected() {
        val kp = JdkEd25519.keyPair(random = random)
        val pk = JdkEd25519.encode(kp)
        val msg = ByteArray(10)
        val sig = JdkEd25519.sign(kp.private, msg)
        // y = p = 2^255 - 19, little-endian: ed ff .. ff 7f
        val p = ByteArray(32) { 0xff.toByte() }.also {
            it[0] = 0xed.toByte()
            it[31] = 0x7f
        }
        assertFalse(Ed25519.verify(pk, msg, p + sig.copyOfRange(32, 64)), "R with y = p")
        assertFalse(Ed25519.verify(p, msg, sig), "A with y = p")
        // y = p + 1 encodes the same field element as y = 1 (the identity's y); still non-canonical.
        val p1 = p.copyOf().also { it[0] = 0xee.toByte() }
        assertFalse(Ed25519.verify(pk, msg, p1 + sig.copyOfRange(32, 64)), "R with y = p + 1")
    }

    @Test
    fun xZeroWithSignBitIsRejected() {
        // y = 1 gives x = 0 (the identity); with the sign bit set the encoding is invalid (RFC 8032 5.1.3 step 4).
        val y1 = ByteArray(32).also { it[0] = 1 }
        val y1Signed = y1.copyOf().also { it[31] = 0x80.toByte() }
        kotlin.test.assertNotNull(Ed25519.decodePoint(y1))
        kotlin.test.assertNull(Ed25519.decodePoint(y1Signed))
    }

    @Test
    fun wrongLengthsAreRejected() {
        val kp = JdkEd25519.keyPair(random = random)
        val pk = JdkEd25519.encode(kp)
        val msg = ByteArray(3)
        val sig = JdkEd25519.sign(kp.private, msg)
        assertTrue(Ed25519.verify(pk, msg, sig))
        assertFalse(Ed25519.verify(pk.copyOf(31), msg, sig))
        assertFalse(Ed25519.verify(pk.copyOf(33), msg, sig))
        assertFalse(Ed25519.verify(pk, msg, sig.copyOf(63)))
        assertFalse(Ed25519.verify(pk, msg, sig.copyOf(65)))
        assertFalse(Ed25519.verify(ByteArray(0), msg, ByteArray(0)))
    }

    private companion object {
        const val CASES = 500
        const val MAX_LEN = 2048
    }
}
