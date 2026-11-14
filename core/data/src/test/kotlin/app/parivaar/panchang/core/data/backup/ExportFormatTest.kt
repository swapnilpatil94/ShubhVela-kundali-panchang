package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.codec.FamilyDocument
import app.parivaar.panchang.core.data.codec.FamilyFixture
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportFormatTest {
    private val rng = SecureRandom()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test fun headerMatchesSpecExactly() {
        val h = ExportFormat.header(600_000, ByteArray(16) { it.toByte() }, ByteArray(12) { (0x10 + it).toByte() })
        val spec = "50504558 01 01 000927c0 10 000102030405060708090a0b0c0d0e0f 101112131415161718191a1b"
        assertEquals(spec.replace(" ", ""), hex(h))
        assertEquals(39, h.size)
        assertEquals(55, ExportFormat.MIN_BYTES)
    }

    @Test fun sealUsesRandomSaltNonceAnd600kIterations() {
        val p = "correct horse battery".toCharArray()
        val a = ExportFormat.seal(p, "{}".toByteArray(), rng)
        val b = ExportFormat.seal(p, "{}".toByteArray(), rng)
        assertEquals(600_000, ExportFormat.checkHeader(a))
        assertFalse(a.copyOfRange(11, 27).contentEquals(b.copyOfRange(11, 27)), "fresh salt")
        assertFalse(a.copyOfRange(27, 39).contentEquals(b.copyOfRange(27, 39)), "fresh nonce")
        assertContentEquals("{}".toByteArray(), ExportFormat.open(p, a))
        assertEquals("correct horse battery", String(p), "format layer does not modify the passphrase")
    }

    @Test fun frozenExportDecodesToTheFixtureDocument() {
        FrozenFixtures.writeIfRequested()
        val frozen = FrozenFixtures.exportFile.readBytes()
        assertContentEquals(FrozenFixtures.export(), frozen, "format drift: export_v1.ppx no longer reproduces")
        val plain = ExportFormat.open(FrozenFixtures.EXPORT_PASSPHRASE.toCharArray(), frozen)
        assertContentEquals(FrozenFixtures.familyJson, plain)
        assertEquals(FamilyFixture.doc, FamilyDocument.fromBytes(plain))
    }

    @Test fun passphrasePolicyCountsCodePointsAfterNfc() {
        assertFalse(ExportFormat.meetsPolicy("abcdefghi".toCharArray()))
        assertTrue(ExportFormat.meetsPolicy("abcdefghij".toCharArray()))
        assertTrue(ExportFormat.meetsPolicy("\uD83D\uDE00".repeat(10).toCharArray()), "10 emoji = 10 code points")
        assertFalse(ExportFormat.meetsPolicy("\uD83D\uDE00".repeat(9).toCharArray()))
        // U+0958 normalises to two code points (KA + NUKTA): 8 + 2 = 10
        assertTrue(ExportFormat.meetsPolicy("abcdefgh\u0958".toCharArray()))
        assertFalse(ExportFormat.meetsPolicy("abcdefghij\uD800".toCharArray()), "invalid text fails the policy")
    }

    private fun ten() = "abcdefghij".toCharArray()

    @Test fun writerRefusesWeakParameters() {
        assertFailsWith<IllegalArgumentException> {
            ExportFormat.sealWithFixedParams(ten(), ByteArray(0), ByteArray(16), ByteArray(12), 599_999)
        }
        assertFailsWith<IllegalArgumentException> {
            ExportFormat.sealWithFixedParams(ten(), ByteArray(0), ByteArray(15), ByteArray(12), 600_000)
        }
    }
}
