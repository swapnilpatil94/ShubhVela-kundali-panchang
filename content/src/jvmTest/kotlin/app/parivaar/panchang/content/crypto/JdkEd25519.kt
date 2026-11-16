package app.parivaar.panchang.content.crypto

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.EdECPublicKey
import java.security.spec.EdECPoint
import java.security.spec.EdECPublicKeySpec
import java.security.spec.NamedParameterSpec

/**
 * The JDK's own Ed25519 (java.security, JDK 15+), the independent reference for the tests. Key pairs exist only in
 * memory for one test run and are never written anywhere (M11 D3).
 */
internal object JdkEd25519 {
    /** A fresh key pair; with [seed] the private key is exactly those 32 bytes (RFC 8032 "SECRET KEY"). */
    fun keyPair(seed: ByteArray? = null, random: SecureRandom = SecureRandom()): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val source = if (seed == null) random else FixedBytes(seed)
        kpg.initialize(NamedParameterSpec.ED25519, source)
        return kpg.generateKeyPair()
    }

    fun sign(key: PrivateKey, message: ByteArray): ByteArray =
        Signature.getInstance("Ed25519").run {
            initSign(key)
            update(message)
            sign()
        }

    /** The RFC 8032 32-byte encoding of a JDK public key. */
    fun encode(pub: KeyPair): ByteArray {
        val point = (pub.public as EdECPublicKey).point
        val le = point.y.toByteArray().reversedArray().copyOf(32)
        if (point.isXOdd) le[31] = (le[31].toInt() or 0x80).toByte()
        return le
    }

    /** The JDK verifier over a raw 32-byte public key; false when the JDK rejects the key or the signature. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = runCatching {
        val xOdd = (publicKey[31].toInt() and 0x80) != 0
        val be = publicKey.copyOf().also { it[31] = (it[31].toInt() and 0x7f).toByte() }.reversedArray()
        val spec = EdECPublicKeySpec(NamedParameterSpec.ED25519, EdECPoint(xOdd, BigInteger(1, be)))
        val key = KeyFactory.getInstance("Ed25519").generatePublic(spec)
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(message)
            verify(signature)
        }
    }.getOrDefault(false)

    /** A "random" source that hands out [bytes] once, so the JDK uses them as the private key. */
    private class FixedBytes(private val bytes: ByteArray) : SecureRandom() {
        private var used = false

        override fun nextBytes(out: ByteArray) {
            check(!used && out.size == bytes.size) { "fixed seed used once, 32 bytes" }
            bytes.copyInto(out)
            used = true
        }
    }

    fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}
