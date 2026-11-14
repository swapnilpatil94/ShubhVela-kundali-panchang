package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.crypto.AesGcm
import app.parivaar.panchang.core.data.crypto.Bytes
import app.parivaar.panchang.core.data.crypto.CryptoException
import app.parivaar.panchang.core.data.crypto.Pbkdf2
import java.security.SecureRandom

/**
 * Passphrase export `.ppx` (spec section 4, D6): "PPEX" | u8 ver=1 | u8 kdf=1 (PBKDF2-HMAC-SHA256) |
 * u32 iterations | u8 saltLen=16 | salt[16] | nonce[12] | ct+tag; AAD = the 39 header bytes.
 * Written with 600 000 iterations; read with iterations in [600 000, 5 000 000], total 55 B..8 MiB.
 * Key = PBKDF2(UTF-8(NFC(passphrase)), salt, iterations, 32). CPU-bound: run on Dispatchers.Default.
 * Header problems throw [CryptoException] FORMAT/VERSION; a wrong passphrase or damaged body throws AUTH.
 */
internal object ExportFormat {
    const val VERSION = 1
    const val KDF_PBKDF2_SHA256 = 1
    const val ITERATIONS = 600_000
    const val MIN_ITERATIONS = 600_000
    const val MAX_ITERATIONS = 5_000_000
    const val SALT_BYTES = 16
    const val HEADER_BYTES = 39
    const val MIN_BYTES = HEADER_BYTES + AesGcm.TAG_BYTES
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_PLAINTEXT = MAX_BYTES - MIN_BYTES
    const val MIN_PASSPHRASE_CODE_POINTS = 10
    private const val OFF_VER = 4
    private const val OFF_KDF = 5
    private const val OFF_ITER = 6
    private const val OFF_SALT_LEN = 10
    private const val OFF_SALT = 11
    private const val OFF_NONCE = OFF_SALT + SALT_BYTES
    private const val UTF8_CONT_MASK = 0xC0
    private const val UTF8_CONT = 0x80
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'P'.code.toByte(), 'E'.code.toByte(), 'X'.code.toByte())

    fun header(iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "bad iterations" }
        require(salt.size == SALT_BYTES && nonce.size == AesGcm.NONCE_BYTES) { "bad salt or nonce" }
        val h = ByteArray(HEADER_BYTES)
        MAGIC.copyInto(h)
        h[OFF_VER] = VERSION.toByte()
        h[OFF_KDF] = KDF_PBKDF2_SHA256.toByte()
        Bytes.putU32(h, OFF_ITER, iterations.toLong())
        h[OFF_SALT_LEN] = SALT_BYTES.toByte()
        salt.copyInto(h, OFF_SALT)
        nonce.copyInto(h, OFF_NONCE)
        return h
    }

    /** Policy: at least 10 code points after NFC; text that is not valid Unicode fails the policy. */
    fun meetsPolicy(passphrase: CharArray): Boolean {
        val p = try {
            Pbkdf2.nfcUtf8(passphrase)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            return false
        }
        val codePoints = p.count { (it.toInt() and UTF8_CONT_MASK) != UTF8_CONT }
        Bytes.wipe(p)
        return codePoints >= MIN_PASSPHRASE_CODE_POINTS
    }

    /** Seals with a fresh random salt and nonce and 600 000 iterations. [passphrase] is not modified. */
    fun seal(passphrase: CharArray, plaintext: ByteArray, rng: SecureRandom): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        return sealWithFixedParams(passphrase, plaintext, salt, AesGcm.newNonce(rng), ITERATIONS)
    }

    /** Explicit salt/nonce/iterations: for [seal], the frozen fixture and tamper tests only. */
    fun sealWithFixedParams(
        passphrase: CharArray, plaintext: ByteArray, salt: ByteArray, nonce: ByteArray, iterations: Int,
    ): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT) { "export too large" }
        val h = header(iterations, salt, nonce)
        val key = Pbkdf2.deriveFromPassphrase(passphrase, salt, iterations, AesGcm.KEY_BYTES)
        try {
            return h + AesGcm.seal(key, nonce, plaintext, h)
        } finally {
            Bytes.wipe(key)
        }
    }

    /** Opens an export file; the caller wipes the result. [passphrase] is not modified. */
    fun open(passphrase: CharArray, bytes: ByteArray): ByteArray {
        val iterations = checkHeader(bytes)
        val h = bytes.copyOf(HEADER_BYTES)
        val salt = h.copyOfRange(OFF_SALT, OFF_NONCE)
        val key = try {
            Pbkdf2.deriveFromPassphrase(passphrase, salt, iterations, AesGcm.KEY_BYTES)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            reject(CryptoException.Reason.AUTH) // passphrase is not valid text: it cannot have made this file
        }
        try {
            val nonce = h.copyOfRange(OFF_NONCE, HEADER_BYTES)
            return AesGcm.open(key, nonce, bytes.copyOfRange(HEADER_BYTES, bytes.size), h)
        } finally {
            Bytes.wipe(key)
        }
    }

    /** Validates the header; returns the iteration count. */
    fun checkHeader(bytes: ByteArray): Int {
        if (bytes.size <= OFF_VER || !Bytes.constantTimeEquals(bytes.copyOf(MAGIC.size), MAGIC)) {
            reject(CryptoException.Reason.FORMAT)
        }
        if (Bytes.u8(bytes, OFF_VER) != VERSION) reject(CryptoException.Reason.VERSION)
        if (bytes.size <= OFF_KDF) reject(CryptoException.Reason.FORMAT)
        if (Bytes.u8(bytes, OFF_KDF) != KDF_PBKDF2_SHA256) reject(CryptoException.Reason.VERSION)
        if (bytes.size !in MIN_BYTES..MAX_BYTES) reject(CryptoException.Reason.FORMAT)
        val iterations = Bytes.getU32(bytes, OFF_ITER)
        val fieldsOk = iterations in MIN_ITERATIONS.toLong()..MAX_ITERATIONS.toLong() &&
            Bytes.u8(bytes, OFF_SALT_LEN) == SALT_BYTES
        if (!fieldsOk) reject(CryptoException.Reason.FORMAT)
        return iterations.toInt()
    }

    private fun reject(reason: CryptoException.Reason): Nothing = throw CryptoException(reason)
}
