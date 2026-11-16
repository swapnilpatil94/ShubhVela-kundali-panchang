package app.parivaar.panchang.content.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/specs/M11.md section 4, `Ed25519Rfc8032Test`. The fixture is RFC 8032 section 7.1, extracted from the RFC
 * text. A vector is trusted only because the JDK agrees: it re-derives the public key from the secret key,
 * re-signs (Ed25519 is deterministic) and verifies, so a transcription error cannot pass.
 */
class Ed25519Rfc8032Test {
    private class Vector(val name: String, val sk: ByteArray, val pk: ByteArray, val msg: ByteArray, val sig: ByteArray)

    private val vectors: List<Vector> = run {
        val text = checkNotNull(javaClass.getResource("/rfc8032_ed25519.json")).readText()
        Json.parseToJsonElement(text).jsonObject.getValue("vectors").jsonArray.map {
            val o = it.jsonObject
            fun h(k: String) = JdkEd25519.hex(o.getValue(k).jsonPrimitive.content)
            val name = o.getValue("name").jsonPrimitive.content
            val v = Vector(name, h("secretKey"), h("publicKey"), h("message"), h("signature"))
            assertEquals(o.getValue("messageLength").jsonPrimitive.int, v.msg.size, v.name)
            v
        }
    }

    @Test
    fun fixtureHoldsTheFiveSection71Vectors() {
        assertEquals(listOf("TEST 1", "TEST 2", "TEST 3", "TEST 1024", "TEST SHA(abc)"), vectors.map { it.name })
    }

    @Test
    fun jdkAgreesWithEveryVector() {
        for (v in vectors) {
            val kp = JdkEd25519.keyPair(seed = v.sk)
            assertContentEquals(v.pk, JdkEd25519.encode(kp), "${v.name} public key")
            assertContentEquals(v.sig, JdkEd25519.sign(kp.private, v.msg), "${v.name} signature")
            assertTrue(JdkEd25519.verify(v.pk, v.msg, v.sig), "${v.name} JDK verify")
        }
    }

    @Test
    fun everyVectorVerifies() {
        for (v in vectors) assertTrue(Ed25519.verify(v.pk, v.msg, v.sig), v.name)
    }

    @Test
    fun everyVectorFailsWithAnotherMessage() {
        for (v in vectors) {
            val other = v.msg + byteArrayOf(0)
            assertFalse(Ed25519.verify(v.pk, other, v.sig), v.name)
        }
    }

    @Test
    fun theShaAbcMessageIsSha512OfAbc() {
        val v = vectors.single { it.name == "TEST SHA(abc)" }
        assertContentEquals(Sha512.digest("abc".encodeToByteArray()), v.msg)
    }
}
