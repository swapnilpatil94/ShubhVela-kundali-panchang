package app.parivaar.panchang.core.data.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Binding oracle: vectors/pbkdf2_sha256.json from tools/oracle/gen_pbkdf2.py (Python hashlib). */
class Pbkdf2Test {
    private class Case(
        val name: String, val password: String, val pHex: String, val saltHex: String,
        val iterations: Int, val dkLen: Int, val dkHex: String,
    )

    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val cases: List<Case> by lazy {
        val text = checkNotNull(javaClass.getResource("/vectors/pbkdf2_sha256.json")) { "fixture missing" }.readText()
        Json.parseToJsonElement(text).jsonObject.getValue("cases").jsonArray.map {
            val o = it.jsonObject
            fun s(k: String) = o.getValue(k).jsonPrimitive.content
            Case(s("name"), s("password"), s("passwordNfcUtf8Hex"), s("saltHex"),
                o.getValue("iterations").jsonPrimitive.int, o.getValue("dkLen").jsonPrimitive.int, s("dkHex"))
        }
    }

    private fun case(name: String) = cases.single { it.name == name }

    @Test fun fixtureHasAllSpecCases() {
        assertEquals(
            listOf("rfc7914_1", "rfc7914_2", "pp_export", "nfc_precomposed", "nfc_decomposed"),
            cases.map { it.name },
        )
        assertEquals(600_000, case("pp_export").iterations)
    }

    @Test fun matchesHashlibFixtureExactly() {
        for (c in cases) {
            val pass = c.password.toCharArray()
            assertEquals(c.pHex, Pbkdf2.nfcUtf8(pass).toHex(), c.name)
            val dk = Pbkdf2.deriveFromPassphrase(pass, hex(c.saltHex), c.iterations, c.dkLen)
            assertEquals(c.dkHex, dk.toHex(), c.name)
            assertEquals(c.password, String(pass), "input must not be modified")
        }
    }

    /** RFC 7914 section 11 PBKDF2-HMAC-SHA256 prefixes quoted in the spec (VERIFY sanity, not the oracle). */
    @Test fun rfc7914SanityPrefixes() {
        assertTrue(case("rfc7914_1").dkHex.startsWith("55ac046e56e3089f"))
        assertTrue(case("rfc7914_2").dkHex.startsWith("4ddcd8f60b98be21"))
    }

    @Test fun nfcPairDiffersRawButDerivesEqualKeys() {
        val a = case("nfc_precomposed")
        val b = case("nfc_decomposed")
        assertFalse(a.password == b.password)
        val ka = Pbkdf2.deriveFromPassphrase(a.password.toCharArray(), hex(a.saltHex), a.iterations, a.dkLen)
        val kb = Pbkdf2.deriveFromPassphrase(b.password.toCharArray(), hex(b.saltHex), b.iterations, b.dkLen)
        assertContentEquals(ka, kb)
    }

    /** PBKDF2 output for a shorter dkLen is a prefix of the longer one; covers the partial last block. */
    @Test fun partialBlockIsPrefix() {
        val c = case("rfc7914_1")
        for (len in listOf(1, 31, 32, 33, 63)) {
            val dk = Pbkdf2.derive(hex(c.pHex), hex(c.saltHex), c.iterations, len)
            assertEquals(c.dkHex.substring(0, 2 * len), dk.toHex())
        }
    }

    @Test fun rejectsBadParametersAndInvalidText() {
        assertFailsWith<IllegalArgumentException> { Pbkdf2.derive(ByteArray(0), ByteArray(16), 1, 32) }
        assertFailsWith<IllegalArgumentException> { Pbkdf2.derive(ByteArray(1), ByteArray(16), 0, 32) }
        assertFailsWith<IllegalArgumentException> { Pbkdf2.derive(ByteArray(1), ByteArray(16), 1, 0) }
        val e = assertFailsWith<IllegalArgumentException> { Pbkdf2.nfcUtf8(charArrayOf('a', '\uD800', 'b')) }
        assertEquals("passphrase is not valid text", e.message)
    }
}
