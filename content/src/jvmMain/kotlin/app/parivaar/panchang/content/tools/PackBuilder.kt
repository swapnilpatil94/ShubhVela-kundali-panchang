package app.parivaar.panchang.content.tools

import app.parivaar.panchang.content.pack.PackEnvelope
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

/**
 * Build-time packer (docs/specs/M11.md section 3 "Envelope", D3). Build/CI only: JDK APIs, no library.
 *
 * Layout (big-endian): `PPCK`, envelopeVersion 1, codec 1 (UTF-8 JSON), keyId (first 8 bytes of SHA-512 of the raw
 * 32-byte public key), payloadLen u32, payload, then the 64-byte Ed25519 signature over bytes `[0, 18 + len)`.
 */
object PackBuilder {
    private const val RAW_KEY = 32
    const val SIGNATURE_LEN = 64

    /** A fresh key pair, in memory only (never serialised). */
    fun ephemeralKeyPair(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    /** The raw 32-byte public key (the last 32 bytes of the X.509 encoding). */
    fun rawPublicKey(kp: KeyPair): ByteArray = kp.public.encoded.let { it.copyOfRange(it.size - RAW_KEY, it.size) }

    fun keyId(rawPublicKey: ByteArray): ByteArray = PackEnvelope.keyIdOf(rawPublicKey)

    /** Signs [payload] into an envelope (the loader's own layout: [PackEnvelope.unsigned] then [PackEnvelope.seal]). */
    fun envelope(payload: ByteArray, kp: KeyPair): ByteArray {
        val head = PackEnvelope.unsigned(rawPublicKey(kp), payload)
        val sig = Signature.getInstance("Ed25519").run {
            initSign(kp.private)
            update(head)
            sign()
        }
        check(sig.size == SIGNATURE_LEN) { "unexpected signature length" }
        return PackEnvelope.seal(head, sig)
    }

    /**
     * Packs every bundled source pack under [out]`/content/<lang>/pack.ppc` and writes `content/bundled.pub` (the raw
     * public key). The key pair lives only inside this call.
     */
    fun buildAll(src: File, out: File) {
        val packs = SourcePacks.read(src)
        require(packs.keys.containsAll(SourcePacks.BUNDLED)) { "a bundled source pack is missing" }
        // Never ship a pack that fails lint (the same rules as `contentLint`).
        val issues = ContentLint.lint(packs, SourcePacks.banned(src))
        require(issues.isEmpty()) { "content lint failed: " + issues.joinToString("; ") }
        val kp = ephemeralKeyPair()
        val dir = File(out, "content")
        dir.deleteRecursively()
        for ((lang, p) in packs) {
            require(p.lang == lang) { "pack language does not match its directory" }
            val f = File(dir, "$lang/pack.ppc")
            f.parentFile.mkdirs()
            f.writeBytes(envelope(SourcePacks.payloadJson(p).encodeToByteArray(), kp))
        }
        File(dir, "bundled.pub").writeBytes(rawPublicKey(kp))
    }
}

/** `buildPacks` entry point: args `<content-src> <out dir>`. */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: PackBuilder <content-src> <out>" }
    PackBuilder.buildAll(File(args[0]), File(args[1]))
}
