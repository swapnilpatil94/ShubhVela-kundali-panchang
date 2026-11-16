package app.parivaar.panchang.content.pack

import app.parivaar.panchang.content.crypto.JdkEd25519
import app.parivaar.panchang.content.text.TermKind
import kotlinx.serialization.json.Json
import java.security.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * docs/specs/M11.md section 4, `PackLoaderTest`. Packs are signed in-test with throwaway JDK key pairs that exist
 * only in memory (M11 D3); no key is read from or written to disk.
 */
class PackLoaderTest {
    private val bundledKp = JdkEd25519.keyPair()
    private val remoteKp = JdkEd25519.keyPair()
    private val bundledPk = JdkEd25519.encode(bundledKp)
    private val remotePk = JdkEd25519.encode(remoteKp)
    private val keys = TrustedKeys(bundled = bundledPk, remote = listOf(remotePk))

    private fun payload(lang: String = "hi", extra: String = ""): ByteArray {
        val rashi = (1..12).joinToString(",") { "\"r$it\"" }
        return """{"schema":1,"lang":"$lang","version":3,"entries":{"rf.retro":"text {graha}","gochar.mo.h1.v1":"a"},
            |"terms":{"rashi":[$rashi]}$extra}""".trimMargin().encodeToByteArray()
    }

    private fun pack(payload: ByteArray, kp: KeyPair = bundledKp): ByteArray {
        val unsigned = PackEnvelope.unsigned(JdkEd25519.encode(kp), payload)
        return PackEnvelope.seal(unsigned, JdkEd25519.sign(kp.private, unsigned))
    }

    private fun load(bytes: ByteArray, lang: String = "hi", source: PackSource = PackSource.BUNDLED) =
        PackLoader(keys).load(bytes, lang, source)

    private fun ByteArray.flipped(i: Int): ByteArray = copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }

    @Test
    fun aGoodPackLoads() {
        val ok = assertIs<PackResult.Ok>(load(pack(payload())))
        assertEquals("hi", ok.pack.lang)
        assertEquals(3, ok.pack.version)
        assertEquals("text {graha}", ok.pack.entries["rf.retro"])
        assertEquals("r4", ok.pack.terms.of(TermKind.RASHI, 4))
        assertEquals(null, ok.pack.terms.of(TermKind.GRAHA, 1))
        assertEquals(1, ok.pack.variantCount("gochar.mo.h1"))
    }

    @Test
    fun aRemotePackLoadsOnlyWithARemoteKey() {
        assertIs<PackResult.Ok>(load(pack(payload(), remoteKp), source = PackSource.REMOTE))
        assertEquals(PackResult.UntrustedKey, load(pack(payload(), remoteKp), source = PackSource.BUNDLED))
    }

    @Test
    fun anyChangedByteIsRejected() {
        val good = pack(payload())
        // A payload byte: the signature no longer matches.
        assertEquals(PackResult.SignatureInvalid, load(good.flipped(PackEnvelope.HEADER + 5)))
        // A signature byte, first and last.
        assertEquals(PackResult.SignatureInvalid, load(good.flipped(good.size - PackEnvelope.SIGNATURE)))
        assertEquals(PackResult.SignatureInvalid, load(good.flipped(good.size - 1)))
        // Header bytes: magic, key id, length.
        assertEquals(PackResult.Malformed, load(good.flipped(0)))
        assertEquals(PackResult.UntrustedKey, load(good.flipped(6)))
        assertEquals(PackResult.Malformed, load(good.flipped(17)))
    }

    @Test
    fun everySingleByteFlipFailsBeforeParsing() {
        val good = pack(payload())
        val spy = Spy()
        val loader = PackLoader(keys, spy::decode)
        for (i in good.indices) {
            val r = loader.load(good.flipped(i), "hi", PackSource.BUNDLED)
            check(r !is PackResult.Ok) { "flip at $i accepted" }
        }
        assertEquals(0, spy.calls)
    }

    @Test
    fun codecTwoIsUnsupported() {
        val bad = pack(payload()).also { it[5] = 2 }
        assertEquals(PackResult.UnsupportedVersion, load(bad))
        val badVersion = pack(payload()).also { it[4] = 2 }
        assertEquals(PackResult.UnsupportedVersion, load(badVersion))
    }

    @Test
    fun truncatedTrailingAndGarbageAreMalformed() {
        val good = pack(payload())
        assertEquals(PackResult.Malformed, load(good.copyOf(good.size - 1)))
        assertEquals(PackResult.Malformed, load(good + byteArrayOf(0)))
        assertEquals(PackResult.Malformed, load(ByteArray(0)))
        assertEquals(PackResult.Malformed, load(ByteArray(81)))
        assertEquals(PackResult.Malformed, load(kotlin.random.Random(1).nextBytes(500)))
        // A length field near 2^32 must not overflow into a match.
        val huge = good.copyOf().also { for (i in 14..17) it[i] = 0xff.toByte() }
        assertEquals(PackResult.Malformed, load(huge))
    }

    @Test
    fun overTwentyFourMibIsTooLargeBeforeAnything() {
        assertEquals(PackResult.TooLarge, load(ByteArray(PackEnvelope.MAX_BYTES + 1)))
        assertEquals(24 * 1024 * 1024, PackEnvelope.MAX_BYTES)
    }

    @Test
    fun unknownKeyIsUntrusted() {
        val stranger = JdkEd25519.keyPair()
        assertEquals(PackResult.UntrustedKey, load(pack(payload(), stranger)))
        assertEquals(PackResult.UntrustedKey, load(pack(payload(), stranger), source = PackSource.REMOTE))
        val none = PackLoader(TrustedKeys(null, emptyList()))
        assertEquals(PackResult.UntrustedKey, none.load(pack(payload()), "hi", PackSource.BUNDLED))
    }

    @Test
    fun theBundledKeyIsNotTrustedForRemote() {
        assertEquals(PackResult.UntrustedKey, load(pack(payload()), source = PackSource.REMOTE))
    }

    @Test
    fun prodKeysAreEmptyUntilTheOwnerAddsOne() {
        assertEquals(0, TrustedKeys.PROD.size)
    }

    @Test
    fun wrongLanguageIsSchemaInvalid() {
        assertIs<PackResult.SchemaInvalid>(load(pack(payload(lang = "hi")), lang = "mr"))
    }

    @Test
    fun schemaViolationsAreRejectedWithStaticReasons() {
        val cases = listOf(
            """{"schema":2,"lang":"hi","version":1,"entries":{}}""",
            """{"schema":"1","lang":"hi","version":1,"entries":{}}""",
            """{"schema":1,"lang":"hi","version":0,"entries":{}}""",
            """{"schema":1,"lang":"hi","version":1}""",
            """{"schema":1,"lang":"hi","version":1,"entries":{"Bad.id":"x"}}""",
            """{"schema":1,"lang":"hi","version":1,"entries":{"noddot":"x"}}""",
            """{"schema":1,"lang":"hi","version":1,"entries":{"rf.retro":""}}""",
            """{"schema":1,"lang":"hi","version":1,"entries":{"rf.retro":"${"x".repeat(4097)}"}}""",
            """{"schema":1,"lang":"hi","version":1,"entries":{"rf.retro":5}}""",
            """{"schema":1,"lang":"hi","version":1,"entries":{},"terms":{"rashi":["a"]}}""",
            """{"schema":1,"lang":"hi","version":1,"entries":{},"terms":{"planet":[]}}""",
            """{"schema":1,"lang":"hi","version":1,"entries":{},"extra":1}""",
            """[1,2]""",
            """not json""",
        )
        for (c in cases) {
            val r = assertIs<PackResult.SchemaInvalid>(load(pack(c.encodeToByteArray())), c.take(60))
            check("secret" !in r.reason)
        }
        // Exactly 4096 chars is allowed.
        val max = """{"schema":1,"lang":"hi","version":1,"entries":{"rf.retro":"${"x".repeat(4096)}"}}"""
        assertIs<PackResult.Ok>(load(pack(max.encodeToByteArray())))
    }

    @Test
    fun invalidJsonWithBadSignatureIsNeverDecoded() {
        val spy = Spy()
        val loader = PackLoader(keys, spy::decode)
        val bad = pack("{{{ not json".encodeToByteArray()).let { it.flipped(it.size - 1) }
        assertEquals(PackResult.SignatureInvalid, loader.load(bad, "hi", PackSource.BUNDLED))
        assertEquals(0, spy.calls)
        // The same payload correctly signed reaches the decoder exactly once and fails as schema.
        val signed = pack("{{{ not json".encodeToByteArray())
        assertIs<PackResult.SchemaInvalid>(loader.load(signed, "hi", PackSource.BUNDLED))
        assertEquals(1, spy.calls)
    }

    @Test
    fun failureResultsAreStatic() {
        // Data objects print their name only; nothing from the input can appear.
        assertEquals("SignatureInvalid", PackResult.SignatureInvalid.toString())
        assertEquals(PackResult.SchemaInvalid("lang does not match"), load(pack(payload()), lang = "gu"))
    }

    private class Spy {
        var calls = 0

        fun decode(b: ByteArray): kotlinx.serialization.json.JsonElement {
            calls++
            return Json.parseToJsonElement(b.decodeToString())
        }
    }
}
