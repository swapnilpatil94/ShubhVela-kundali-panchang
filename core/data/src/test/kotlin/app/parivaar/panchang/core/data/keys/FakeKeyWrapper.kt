package app.parivaar.panchang.core.data.keys

import app.parivaar.panchang.core.data.crypto.AesGcm
import app.parivaar.panchang.core.data.crypto.CryptoException
import java.security.SecureRandom

/**
 * JVM stand-in for the Keystore KEK (D12): an in-memory AES-256 key used through javax AES-GCM.
 * [lost] makes every call throw [KeyLostException] (invalidated key); [unavailableCalls] makes that many
 * next calls throw [KeyUnavailableException] (transient keystore failure).
 */
internal class FakeKeyWrapper(private val rng: SecureRandom = SecureRandom()) : KeyWrapper {
    private var kek: ByteArray? = null
    var lost = false
    var unavailableCalls = 0
    var createCount = 0
        private set

    private fun gate() {
        if (unavailableCalls > 0) {
            unavailableCalls--
            throw KeyUnavailableException()
        }
        if (lost) throw KeyLostException()
    }

    override fun exists(): Boolean {
        gate()
        return kek != null
    }

    override fun create() {
        gate()
        kek = ByteArray(AesGcm.KEY_BYTES).also { rng.nextBytes(it) }
        createCount++
    }

    override fun delete() {
        gate()
        kek = null
    }

    override fun wrap(plain: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        gate()
        val k = kek ?: throw KeyLostException()
        val s = AesGcm.encrypt(k, plain, aad, rng)
        return s.nonce to s.ciphertext
    }

    override fun unwrap(iv: ByteArray, ct: ByteArray, aad: ByteArray): ByteArray {
        gate()
        val k = kek ?: throw KeyLostException()
        return try {
            AesGcm.open(k, iv, ct, aad)
        } catch (@Suppress("SwallowedException") e: CryptoException) {
            throw KeyLostException()
        }
    }
}
