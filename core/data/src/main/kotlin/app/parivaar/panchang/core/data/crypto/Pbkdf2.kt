package app.parivaar.panchang.core.data.crypto

import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256 per RFC 8018 section 5.2 over the platform `Mac("HmacSHA256")`
 * (`PBKDF2WithHmacSHA256` is absent on API 24-25; spec section 4, D6). CPU-bound: callers run it on
 * `Dispatchers.Default`. Checked against `tools/oracle/gen_pbkdf2.py` (Python hashlib).
 */
internal object Pbkdf2 {
    private const val HMAC = "HmacSHA256"
    private const val H_LEN = 32
    private const val MAX_DK_LEN = 1024

    /**
     * Derives a key from [passphrase]: P = UTF-8 of NFC([passphrase]) (strict encoding; unpaired
     * surrogates are rejected). [passphrase] is not modified; the caller wipes it. The intermediate P
     * bytes are wiped here; the NFC String cannot be wiped (accepted, spec section 4 "Zeroing").
     */
    fun deriveFromPassphrase(passphrase: CharArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val p = nfcUtf8(passphrase)
        try {
            return derive(p, salt, iterations, dkLen)
        } finally {
            Bytes.wipe(p)
        }
    }

    /** UTF-8 bytes of NFC([passphrase]); the caller wipes the result. */
    fun nfcUtf8(passphrase: CharArray): ByteArray {
        val normalized = Normalizer.normalize(CharBuffer.wrap(passphrase), Normalizer.Form.NFC)
        val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buf = try {
            encoder.encode(CharBuffer.wrap(normalized))
        } catch (@Suppress("SwallowedException") e: CharacterCodingException) {
            throw IllegalArgumentException("passphrase is not valid text")
        }
        val out = ByteArray(buf.remaining())
        buf.get(out)
        if (buf.hasArray()) Bytes.wipe(buf.array())
        return out
    }

    /** DK = PBKDF2-HMAC-SHA256([password], [salt], [iterations], [dkLen]). */
    fun derive(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        require(password.isNotEmpty()) { "empty password" }
        require(iterations >= 1) { "bad iteration count" }
        require(dkLen in 1..MAX_DK_LEN) { "bad key length" }
        val mac = Mac.getInstance(HMAC)
        // SecretKeySpec copies the password; that copy cannot be wiped (spec section 4 "Zeroing").
        mac.init(SecretKeySpec(password, HMAC))
        val out = ByteArray(dkLen)
        val u = ByteArray(H_LEN)
        val t = ByteArray(H_LEN)
        val index = ByteArray(4)
        try {
            val blocks = (dkLen + H_LEN - 1) / H_LEN
            for (i in 1..blocks) {
                mac.update(salt)
                Bytes.putU32(index, 0, i.toLong())
                mac.update(index)
                mac.doFinal(u, 0)
                u.copyInto(t)
                for (c in 2..iterations) {
                    mac.update(u)
                    mac.doFinal(u, 0)
                    for (j in 0 until H_LEN) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
                val off = (i - 1) * H_LEN
                t.copyInto(out, off, 0, minOf(H_LEN, dkLen - off))
            }
            return out
        } finally {
            Bytes.wipe(u)
            Bytes.wipe(t)
            mac.reset()
        }
    }
}
