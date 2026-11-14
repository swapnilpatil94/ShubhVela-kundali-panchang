@file:Suppress("MagicNumber") // FIPS 180-4 constants and shifts

package app.parivaar.panchang.engine.astro.internal

/**
 * SHA-256 per FIPS 180-4 (own implementation; pure Kotlin, no platform APIs).
 * Incremental: call [update] any number of times, then [digest] once.
 */
internal class Sha256 {
    private val h = H0.copyOf()
    private val block = ByteArray(64)
    private var blockLen = 0
    private var totalBytes = 0L
    private val w = IntArray(64)

    fun update(data: ByteArray, off: Int = 0, len: Int = data.size - off) {
        require(off >= 0 && len >= 0 && off + len <= data.size)
        var i = off
        val end = off + len
        totalBytes += len
        while (i < end) {
            val n = minOf(64 - blockLen, end - i)
            data.copyInto(block, blockLen, i, i + n)
            blockLen += n
            i += n
            if (blockLen == 64) {
                compress()
                blockLen = 0
            }
        }
    }

    fun digest(): ByteArray {
        val bitLen = totalBytes * 8
        block[blockLen++] = 0x80.toByte()
        if (blockLen > 56) {
            block.fill(0, blockLen, 64)
            compress()
            blockLen = 0
        }
        block.fill(0, blockLen, 56)
        for (k in 0 until 8) block[56 + k] = (bitLen ushr (56 - 8 * k)).toByte()
        compress()
        val out = ByteArray(32)
        for (k in 0 until 8) for (b in 0 until 4) out[4 * k + b] = (h[k] ushr (24 - 8 * b)).toByte()
        return out
    }

    private fun compress() {
        for (t in 0 until 16) {
            w[t] = ((block[4 * t].toInt() and 0xFF) shl 24) or
                ((block[4 * t + 1].toInt() and 0xFF) shl 16) or
                ((block[4 * t + 2].toInt() and 0xFF) shl 8) or
                (block[4 * t + 3].toInt() and 0xFF)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (t in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[t] + w[t]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }

    companion object {
        private val H0 = intArrayOf(
            0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
            0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
        )
        private val K = intArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1,
            0x923f82a4.toInt(), 0xab1c5ed5.toInt(), 0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(), 0xe49b69c1.toInt(),
            0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(),
            0xd5a79147.toInt(), 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(), 0xa2bfe8a1.toInt(),
            0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(),
            0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c,
            0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f,
            0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(),
            0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
        )

        fun hash(data: ByteArray): ByteArray = Sha256().also { it.update(data) }.digest()

        fun hex(bytes: ByteArray): String {
            val digits = "0123456789abcdef"
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(digits[(b.toInt() ushr 4) and 0xF]).append(digits[b.toInt() and 0xF])
            }
            return sb.toString()
        }

        fun hashHex(data: ByteArray): String = hex(hash(data))
    }
}
