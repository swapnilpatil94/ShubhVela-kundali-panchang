package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.crypto.AesGcm
import app.parivaar.panchang.core.data.crypto.Bytes
import app.parivaar.panchang.core.data.crypto.CryptoException
import java.security.SecureRandom

/**
 * Snapshot `filesDir/backup/snapshot.bin` (spec section 4): "PPSN" | u8 ver=1 | u32 K_b g | nonce[12] | ct+tag;
 * AAD = the 21 header bytes; plaintext = FamilyDocument JSON; at most 8 MiB. Fails closed with [CryptoException].
 */
internal object SnapshotFormat {
    const val VERSION = 1
    const val HEADER_BYTES = 21
    const val MIN_BYTES = HEADER_BYTES + AesGcm.TAG_BYTES
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_PLAINTEXT = MAX_BYTES - MIN_BYTES
    private const val OFF_VER = 4
    private const val OFF_GEN = 5
    private const val OFF_NONCE = 9
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'P'.code.toByte(), 'S'.code.toByte(), 'N'.code.toByte())

    fun header(g: Int, nonce: ByteArray): ByteArray {
        require(g >= 1 && nonce.size == AesGcm.NONCE_BYTES) { "bad snapshot header" }
        val h = ByteArray(HEADER_BYTES)
        MAGIC.copyInto(h)
        h[OFF_VER] = VERSION.toByte()
        Bytes.putU32(h, OFF_GEN, g.toLong())
        nonce.copyInto(h, OFF_NONCE)
        return h
    }

    /** Seals [plaintext] under K_b generation [g] with a fresh random nonce. */
    fun seal(kb: ByteArray, g: Int, plaintext: ByteArray, rng: SecureRandom): ByteArray =
        sealWithNonce(kb, g, AesGcm.newNonce(rng), plaintext)

    /** Fixed-nonce variant for the frozen test fixture only; production uses [seal]. */
    fun sealWithNonce(kb: ByteArray, g: Int, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT) { "snapshot too large" }
        val h = header(g, nonce)
        return h + AesGcm.seal(kb, nonce, plaintext, h)
    }

    /** K_b generation named by [bytes]; throws [CryptoException] on a malformed header. */
    fun generationOf(bytes: ByteArray): Int {
        if (bytes.size <= OFF_VER || !Bytes.constantTimeEquals(bytes.copyOf(MAGIC.size), MAGIC)) {
            reject(CryptoException.Reason.FORMAT)
        }
        if (Bytes.u8(bytes, OFF_VER) != VERSION) reject(CryptoException.Reason.VERSION)
        if (bytes.size !in MIN_BYTES..MAX_BYTES) reject(CryptoException.Reason.FORMAT)
        val g = Bytes.getU32(bytes, OFF_GEN)
        if (g < 1 || g > Int.MAX_VALUE) reject(CryptoException.Reason.FORMAT)
        return g.toInt()
    }

    /** Opens [bytes] that must be sealed under K_b generation [g]; the caller wipes the result. */
    fun open(kb: ByteArray, g: Int, bytes: ByteArray): ByteArray {
        if (generationOf(bytes) != g) reject(CryptoException.Reason.FORMAT)
        val h = bytes.copyOf(HEADER_BYTES)
        return AesGcm.open(kb, h.copyOfRange(OFF_NONCE, HEADER_BYTES), bytes.copyOfRange(HEADER_BYTES, bytes.size), h)
    }

    private fun reject(reason: CryptoException.Reason): Nothing = throw CryptoException(reason)
}
