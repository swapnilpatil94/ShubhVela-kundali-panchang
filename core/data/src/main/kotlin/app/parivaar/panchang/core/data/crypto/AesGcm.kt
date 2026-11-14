package app.parivaar.panchang.core.data.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Rejection of an encrypted blob. The message is static and never carries data, lengths, ids or key
 * material (Security tests, CLAUDE.md non-negotiable 2); no cause is attached for the same reason.
 * Callers map [reason] to DataError FILE_FORMAT / FILE_VERSION / FILE_AUTH (spec section 4 "Errors").
 */
internal class CryptoException(val reason: Reason) : Exception(MESSAGES.getValue(reason)) {
    enum class Reason { FORMAT, VERSION, AUTH }

    private companion object {
        val MESSAGES = mapOf(
            Reason.FORMAT to "encrypted data rejected: format",
            Reason.VERSION to "encrypted data rejected: version",
            Reason.AUTH to "encrypted data rejected: authentication",
        )
    }
}

/** Ciphertext with tag, plus the nonce it was sealed under. */
internal class Sealed(val nonce: ByteArray, val ciphertext: ByteArray) {
    override fun toString(): String = "Sealed(redacted)"
}

/**
 * AES-256-GCM over `javax.crypto` `AES/GCM/NoPadding`, 128-bit tag, 96-bit nonce (spec section 4).
 * A fresh Cipher per call; production code must use [encrypt], which draws a new random nonce from
 * [SecureRandom] for every encryption (random-nonce bound 2^32 per key, SP 800-38D). Fails closed:
 * any decryption problem throws [CryptoException], never returns partial plaintext.
 */
internal object AesGcm {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun newNonce(rng: SecureRandom): ByteArray = ByteArray(NONCE_BYTES).also { rng.nextBytes(it) }

    /** Encrypts under a fresh random nonce. */
    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray, rng: SecureRandom): Sealed {
        val nonce = newNonce(rng)
        return Sealed(nonce, seal(key, nonce, plaintext, aad))
    }

    /**
     * Encrypts with a caller-supplied nonce. Only for [encrypt], known-answer tests and frozen test
     * fixtures: a key+nonce pair must never be used twice.
     */
    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "bad key length" }
        require(nonce.size == NONCE_BYTES) { "bad nonce length" }
        return try {
            cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("encryption failed", e)
        }
    }

    /** Decrypts and verifies `ciphertext || tag`; throws [CryptoException] on any failure. */
    fun open(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        if (key.size != KEY_BYTES || nonce.size != NONCE_BYTES || ciphertextAndTag.size < TAG_BYTES) {
            throw CryptoException(CryptoException.Reason.FORMAT)
        }
        return try {
            cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(ciphertextAndTag)
        } catch (@Suppress("SwallowedException") e: GeneralSecurityException) {
            // AEADBadTagException and any other provider failure: fail closed, no cause attached.
            throw CryptoException(CryptoException.Reason.AUTH)
        }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            // SecretKeySpec copies the key; that copy cannot be wiped (spec section 4 "Zeroing").
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            if (aad.isNotEmpty()) updateAAD(aad)
        }
}
