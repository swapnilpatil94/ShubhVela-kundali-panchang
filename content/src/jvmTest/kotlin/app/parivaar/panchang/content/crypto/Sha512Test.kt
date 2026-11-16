package app.parivaar.panchang.content.crypto

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** docs/specs/M11.md section 4, `Sha512Test`: every digest equals the JDK's SHA-512. */
class Sha512Test {
    private fun jdk(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)

    @Test
    fun emptyAbcAndMillionA() {
        for (data in listOf(ByteArray(0), "abc".encodeToByteArray(), ByteArray(1_000_000) { 'a'.code.toByte() })) {
            assertContentEquals(jdk(data), Sha512.digest(data))
        }
    }

    @Test
    fun randomLengthsZeroTo300() {
        val rnd = Random(42)
        for (len in 0..300) {
            val data = rnd.nextBytes(len)
            assertContentEquals(jdk(data), Sha512.digest(data), "length $len")
        }
    }

    @Test
    fun partsEqualTheConcatenation() {
        val rnd = Random(7)
        val a = rnd.nextBytes(100)
        val b = rnd.nextBytes(29)
        val c = rnd.nextBytes(200)
        assertContentEquals(jdk(a + b + c), Sha512.digest(a, b, c))
    }
}
