package app.parivaar.panchang.core.data.crypto

/**
 * Byte helpers for the crypto formats (docs/specs/M06.md section 4): big-endian integers, wiping and
 * constant-time comparison. Wiping is best effort: copies made by the platform (SecretKeySpec, Mac and
 * Cipher internals) and Strings cannot be wiped (accepted, spec section 4 "Zeroing").
 */
internal object Bytes {
    private const val BYTE_MASK = 0xFF
    private const val U16_MAX = 0xFFFF
    private const val U32_MAX = 0xFFFF_FFFFL
    private const val U16_BYTES = 2
    private const val U32_BYTES = 4
    private const val BITS = 8

    fun wipe(b: ByteArray?) {
        b?.fill(0)
    }

    fun wipe(c: CharArray?) {
        c?.fill('\u0000')
    }

    /** Constant-time equality for equal-length arrays; lengths are not secret. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun putU16(dst: ByteArray, off: Int, v: Int) {
        require(v in 0..U16_MAX) { "u16 out of range" }
        putBigEndian(dst, off, v.toLong(), U16_BYTES)
    }

    fun putU32(dst: ByteArray, off: Int, v: Long) {
        require(v in 0..U32_MAX) { "u32 out of range" }
        putBigEndian(dst, off, v, U32_BYTES)
    }

    fun getU16(src: ByteArray, off: Int): Int = getBigEndian(src, off, U16_BYTES).toInt()

    fun getU32(src: ByteArray, off: Int): Long = getBigEndian(src, off, U32_BYTES)

    fun u8(src: ByteArray, off: Int): Int = src[off].toInt() and BYTE_MASK

    private fun putBigEndian(dst: ByteArray, off: Int, v: Long, n: Int) {
        for (i in 0 until n) dst[off + i] = (v ushr (BITS * (n - 1 - i))).toByte()
    }

    private fun getBigEndian(src: ByteArray, off: Int, n: Int): Long {
        var v = 0L
        for (i in 0 until n) v = (v shl BITS) or (src[off + i].toLong() and BYTE_MASK.toLong())
        return v
    }
}
