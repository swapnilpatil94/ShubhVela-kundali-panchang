package app.parivaar.panchang.content.tools

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Envelope layout and the build output (keys exist only in memory; temp files hold public data only). */
class PackBuilderTest {
    private val x509Prefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

    private fun jdkVerify(pub: ByteArray, msg: ByteArray, sig: ByteArray): Boolean {
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(x509Prefix + pub))
        return Signature.getInstance("Ed25519").run { initVerify(key); update(msg); verify(sig) }
    }

    @Test fun envelopeLayout() {
        val kp = PackBuilder.ephemeralKeyPair()
        val payload = "{\"schema\":1}".encodeToByteArray()
        val env = PackBuilder.envelope(payload, kp)
        assertEquals(18 + payload.size + 64, env.size)
        assertEquals("PPCK", env.copyOf(4).decodeToString())
        assertEquals(1, env[4].toInt())
        assertEquals(1, env[5].toInt())
        assertContentEquals(PackBuilder.keyId(PackBuilder.rawPublicKey(kp)), env.copyOfRange(6, 14))
        assertEquals(payload.size, ByteBuffer.wrap(env, 14, 4).int)
        val signed = env.copyOf(18 + payload.size)
        assertTrue(jdkVerify(PackBuilder.rawPublicKey(kp), signed, env.copyOfRange(signed.size, env.size)))
    }

    @Test fun buildAllWritesSignedPacksAndOnlyThePublicKey() {
        val out = Files.createTempDirectory("ppc").toFile()
        try {
            PackBuilder.buildAll(ContentSource.root, out)
            val dir = File(out, "content")
            val pub = File(dir, "bundled.pub").readBytes()
            assertEquals(32, pub.size)
            val files = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.toSortedSet()
            assertEquals(sortedSetOf("bundled.pub", "en/pack.ppc", "gu/pack.ppc", "hi/pack.ppc", "mr/pack.ppc"), files)
            for (lang in SourcePacks.BUNDLED) {
                val env = File(dir, "$lang/pack.ppc").readBytes()
                val len = ByteBuffer.wrap(env, 14, 4).int
                assertTrue(jdkVerify(pub, env.copyOf(18 + len), env.copyOfRange(18 + len, env.size)), lang)
                val json = env.copyOfRange(18, 18 + len).decodeToString()
                assertTrue("\"lang\":\"$lang\"" in json, lang)
            }
        } finally {
            out.deleteRecursively()
        }
    }

    @Test fun everyBuildUsesAFreshKey() {
        val a = PackBuilder.rawPublicKey(PackBuilder.ephemeralKeyPair())
        val b = PackBuilder.rawPublicKey(PackBuilder.ephemeralKeyPair())
        assertTrue(!a.contentEquals(b))
    }
}
