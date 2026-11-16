@file:Suppress("MagicNumber") // field limb layout, curve constants and bit positions

package app.parivaar.panchang.content.crypto

/**
 * Ed25519 signature verification per RFC 8032 section 5.1.7 (own implementation, pure Kotlin; M11 D1).
 *
 * Field elements are 16 limbs of 16 bits in a [LongArray] (the TweetNaCl layout). The curve constants d, sqrt(-1)
 * and the base point are derived at load time from their definitions (d = -121665/121666, sqrt(-1) =
 * 2^((p-1)/4), B = the point with y = 4/5 and even x), not typed in.
 *
 * Checks: wrong key or signature length, non-canonical y (y >= p), y with no square root, x = 0 with the sign bit
 * set, and S >= L are rejected. The group equation is the cofactored one, [8][S]B == [8]R + [8][k]A.
 *
 * Not constant-time: every input (public key, message, signature) is public, so timing leaks nothing secret.
 * There is deliberately no signing here; packs are signed at build time with the JDK (M11 D1, D3).
 */
object Ed25519 {
    /** True only when [signature] is a valid Ed25519 signature of [message] under [publicKey]. */
    @Suppress("ReturnCount") // guard clauses mirror the RFC/spec rejection steps
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != KEY_BYTES || signature.size != SIG_BYTES) return false
        val a = decodePoint(publicKey) ?: return false
        val rBytes = signature.copyOfRange(0, 32)
        val r = decodePoint(rBytes) ?: return false
        val s = signature.copyOfRange(32, 64)
        if (!Scalar.lessThanL(s)) return false
        val k = Scalar.reduce(Sha512.digest(rBytes, publicKey, message))
        // [S]B - [k]A - R, then times the cofactor 8; valid iff that is the identity.
        var p = Point.doubleScalarMul(s, Point.BASE, k, a.negate())
        p = p.add(r.negate())
        repeat(3) { p = p.add(p) }
        return p.isIdentity()
    }

    /** RFC 8032 section 5.1.3 point decoding; null when the encoding is rejected. */
    @Suppress("ReturnCount") // guard clauses mirror the RFC/spec rejection steps
    internal fun decodePoint(bytes: ByteArray): Point? {
        if (bytes.size != 32) return null
        val xSign = (bytes[31].toInt() ushr 7) and 1
        val yBytes = bytes.copyOf().also { it[31] = (it[31].toInt() and 0x7f).toByte() }
        val y = Fe.unpack(yBytes)
        if (!constantTimeEquals(Fe.pack(y), yBytes)) return null // y >= p
        val yy = Fe.mul(y, y)
        val u = Fe.sub(yy, Fe.ONE)
        val v = Fe.add(Fe.mul(Curve.D, yy), Fe.ONE)
        val v3 = Fe.mul(Fe.mul(v, v), v)
        val v7 = Fe.mul(Fe.mul(v3, v3), v)
        var x = Fe.mul(Fe.mul(u, v3), Fe.pow2523(Fe.mul(u, v7)))
        val vxx = Fe.mul(v, Fe.mul(x, x))
        when {
            Fe.equal(vxx, u) -> Unit
            Fe.equal(vxx, Fe.sub(Fe.ZERO, u)) -> x = Fe.mul(x, Curve.SQRT_M1)
            else -> return null
        }
        val xIsZero = Fe.equal(x, Fe.ZERO)
        if (xIsZero && xSign == 1) return null
        if (Fe.parity(x) != xSign) x = Fe.sub(Fe.ZERO, x)
        return Point(x, y, Fe.ONE.copyOf(), Fe.mul(x, y))
    }

    /** Equality of two byte arrays without an early exit (used for encodings and key ids). */
    internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private const val KEY_BYTES = 32
    private const val SIG_BYTES = 64

    /** Arithmetic mod p = 2^255 - 19 on 16 x 16-bit limbs (limbs may be out of range between carries). */
    internal object Fe {
        val ZERO = LongArray(16)
        val ONE = LongArray(16).also { it[0] = 1 }

        fun of(n: Long): LongArray {
            require(n in 0..0xffff_ffffL)
            return LongArray(16).also {
                it[0] = n and 0xffff
                it[1] = n ushr 16
            }
        }

        fun add(a: LongArray, b: LongArray): LongArray = LongArray(16) { a[it] + b[it] }

        fun sub(a: LongArray, b: LongArray): LongArray = LongArray(16) { a[it] - b[it] }

        fun mul(a: LongArray, b: LongArray): LongArray {
            val t = LongArray(31)
            for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
            // 2^256 = 38 (mod p)
            for (i in 0 until 15) t[i] += 38 * t[i + 16]
            val o = t.copyOf(16)
            carry(o)
            carry(o)
            return o
        }

        private fun carry(o: LongArray) {
            for (i in 0 until 16) {
                val c = o[i] shr 16
                o[i] -= c shl 16
                if (i < 15) o[i + 1] += c else o[0] += 38 * c
            }
        }

        /** The canonical 32-byte little-endian encoding (fully reduced mod p). */
        fun pack(n: LongArray): ByteArray {
            val t = n.copyOf()
            carry(t)
            carry(t)
            carry(t)
            // Now 0 <= t < 2^256 with every limb in [0, 2^16); subtract p up to twice.
            repeat(2) {
                val m = LongArray(16)
                m[0] = t[0] - 0xffed
                for (i in 1 until 15) {
                    m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                    m[i - 1] = m[i - 1] and 0xffff
                }
                m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
                m[14] = m[14] and 0xffff
                val borrow = (m[15] shr 16) and 1
                if (borrow == 0L) m.copyInto(t)
            }
            val o = ByteArray(32)
            for (i in 0 until 16) {
                o[2 * i] = (t[i] and 0xff).toByte()
                o[2 * i + 1] = (t[i] shr 8).toByte()
            }
            return o
        }

        /** Reads 32 little-endian bytes, ignoring bit 255. */
        fun unpack(b: ByteArray): LongArray {
            val o = LongArray(16) { (b[2 * it].toLong() and 0xff) or ((b[2 * it + 1].toLong() and 0xff) shl 8) }
            o[15] = o[15] and 0x7fff
            return o
        }

        fun equal(a: LongArray, b: LongArray): Boolean = constantTimeEquals(pack(a), pack(b))

        fun parity(a: LongArray): Int = pack(a)[0].toInt() and 1

        /** a^(p-2) = 1/a. */
        fun inv(a: LongArray): LongArray {
            var c = a.copyOf()
            for (bit in 253 downTo 0) {
                c = mul(c, c)
                if (bit != 2 && bit != 4) c = mul(c, a)
            }
            return c
        }

        /** a^((p-5)/8) = a^(2^252 - 3). */
        fun pow2523(a: LongArray): LongArray {
            var c = a.copyOf()
            for (bit in 250 downTo 0) {
                c = mul(c, c)
                if (bit != 1) c = mul(c, a)
            }
            return c
        }
    }

    /** Curve constants, derived rather than transcribed. */
    internal object Curve {
        /** d = -121665 / 121666 mod p. */
        val D: LongArray = Fe.mul(Fe.sub(Fe.ZERO, Fe.of(121_665)), Fe.inv(Fe.of(121_666)))

        /** sqrt(-1) = 2^((p-1)/4) mod p. */
        val SQRT_M1: LongArray = powerOfTwo()

        /** 2 * d, used by the addition law. */
        val D2: LongArray = Fe.add(D, D)

        /** 2^((p-1)/4) with (p-1)/4 = 2^253 - 5, as 2^(2^253) / 2^5 = (2^(2^253)) * inv(32). */
        private fun powerOfTwo(): LongArray {
            // x = 2^(2^253): square 2 repeatedly, 253 times.
            var x = Fe.of(2)
            repeat(253) { x = Fe.mul(x, x) }
            return Fe.mul(x, Fe.inv(Fe.of(32)))
        }
    }

    /** A point in extended twisted Edwards coordinates (X:Y:Z:T), x = X/Z, y = Y/Z, xy = T/Z. */
    internal class Point(val x: LongArray, val y: LongArray, val z: LongArray, val t: LongArray) {
        /** The unified addition law (add-2008-hwcd-3); also valid for doubling. */
        fun add(q: Point): Point {
            val a = Fe.mul(Fe.sub(y, x), Fe.sub(q.y, q.x))
            val b = Fe.mul(Fe.add(y, x), Fe.add(q.y, q.x))
            val c = Fe.mul(Fe.mul(t, q.t), Curve.D2)
            val d = Fe.mul(z, q.z).let { Fe.add(it, it) }
            val e = Fe.sub(b, a)
            val f = Fe.sub(d, c)
            val g = Fe.add(d, c)
            val h = Fe.add(b, a)
            return Point(Fe.mul(e, f), Fe.mul(h, g), Fe.mul(g, f), Fe.mul(e, h))
        }

        fun negate(): Point = Point(Fe.sub(Fe.ZERO, x), y, z, Fe.sub(Fe.ZERO, t))

        fun isIdentity(): Boolean = Fe.equal(x, Fe.ZERO) && Fe.equal(y, z)

        companion object {
            val IDENTITY = Point(Fe.ZERO, Fe.ONE, Fe.ONE, Fe.ZERO)

            /** B: y = 4/5, x even (RFC 8032 section 5.1). */
            val BASE: Point = run {
                val y = Fe.mul(Fe.of(4), Fe.inv(Fe.of(5)))
                val enc = Fe.pack(y) // sign bit 0 selects the even x
                checkNotNull(decodePoint(enc)) { "base point" }
            }

            /** [s]P + [k]Q for 32-byte little-endian scalars (Straus, left to right). */
            fun doubleScalarMul(s: ByteArray, p: Point, k: ByteArray, q: Point): Point {
                val pq = p.add(q)
                var acc = IDENTITY
                for (i in 255 downTo 0) {
                    acc = acc.add(acc)
                    val bs = (s[i ushr 3].toInt() ushr (i and 7)) and 1
                    val bk = (k[i ushr 3].toInt() ushr (i and 7)) and 1
                    when {
                        bs == 1 && bk == 1 -> acc = acc.add(pq)
                        bs == 1 -> acc = acc.add(p)
                        bk == 1 -> acc = acc.add(q)
                    }
                }
                return acc
            }
        }
    }

    /** Scalars mod L = 2^252 + 27742317777372353535851937790883648493, little-endian bytes. */
    internal object Scalar {
        /** L, little-endian. */
        val L: ByteArray = hexLe("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed")

        /** True when the 32-byte little-endian [s] is < L. */
        fun lessThanL(s: ByteArray): Boolean {
            for (i in 31 downTo 0) {
                val a = s[i].toInt() and 0xff
                val b = L[i].toInt() and 0xff
                if (a != b) return a < b
            }
            return false
        }

        /** [x] (little-endian, any length) mod L, as 32 little-endian bytes. Bitwise long division. */
        fun reduce(x: ByteArray): ByteArray {
            val r = IntArray(33) // little-endian bytes; r < 2L < 2^254 fits in 32 bytes
            for (bit in x.size * 8 - 1 downTo 0) {
                // r = 2r + bit
                var c = (x[bit ushr 3].toInt() ushr (bit and 7)) and 1
                for (i in 0 until 33) {
                    val v = (r[i] shl 1) or c
                    r[i] = v and 0xff
                    c = v ushr 8
                }
                if (!lessThanL33(r)) subtractL(r)
            }
            return ByteArray(32) { r[it].toByte() }
        }

        @Suppress("ReturnCount") // guard clauses mirror the RFC/spec rejection steps
        private fun lessThanL33(r: IntArray): Boolean {
            if (r[32] != 0) return false
            for (i in 31 downTo 0) {
                val b = L[i].toInt() and 0xff
                if (r[i] != b) return r[i] < b
            }
            return false
        }

        private fun subtractL(r: IntArray) {
            var borrow = 0
            for (i in 0 until 33) {
                val li = if (i < 32) L[i].toInt() and 0xff else 0
                var v = r[i] - li - borrow
                borrow = if (v < 0) 1 else 0
                if (v < 0) v += 256
                r[i] = v
            }
        }

        private fun hexLe(beHex: String): ByteArray {
            val be = ByteArray(beHex.length / 2) { beHex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
            return be.reversedArray()
        }
    }
}
