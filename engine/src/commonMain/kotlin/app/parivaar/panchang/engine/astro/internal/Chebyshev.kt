package app.parivaar.panchang.engine.astro.internal

import app.parivaar.panchang.engine.astro.BinSource
import kotlin.math.floor

/** Chebyshev series of the first kind on s in [-1, 1] (own code; docs/specs/M01.md section 3.4). */
internal object Chebyshev {
    /** Sum of `c[off + k] * T_k(s)` for k in 0 until n, by Clenshaw's recurrence. */
    fun value(c: DoubleArray, off: Int, n: Int, s: Double): Double {
        var b1 = 0.0
        var b2 = 0.0
        val twoS = 2.0 * s
        for (k in n - 1 downTo 1) {
            val b0 = c[off + k] + twoS * b1 - b2
            b2 = b1
            b1 = b0
        }
        return c[off] + s * b1 - b2
    }

    /**
     * Sum of `c[off + k] * T'_k(s)` (derivative with respect to s) for k in 0 until n, with
     * T'_0 = 0, T'_1 = 1, T'_k = 2 T_(k-1) + 2 s T'_(k-1) - T'_(k-2).
     */
    fun derivative(c: DoubleArray, off: Int, n: Int, s: Double): Double {
        if (n < 2) return 0.0
        val twoS = 2.0 * s
        var tPrev = 1.0 // T_(k-2), starts as T_0
        var tCur = s // T_(k-1), starts as T_1
        var dPrev = 0.0 // T'_(k-2), starts as T'_0
        var dCur = 1.0 // T'_(k-1), starts as T'_1
        var sum = c[off + 1]
        for (k in 2 until n) {
            val d = 2.0 * tCur + twoS * dCur - dPrev
            val t = twoS * tCur - tPrev
            sum += c[off + k] * d
            tPrev = tCur
            tCur = t
            dPrev = dCur
            dCur = d
        }
        return sum
    }
}

/**
 * Reader of the `pp-de440-1900-2100.bin` layout (docs/specs/M01.md section 3.4), little-endian:
 * `0: "PPDE440\0"`, `8: u32 version = 1`, `12: u32 nSeg`, `16: f64 EMRAT`, `24: f64 jdStart`, `32: f64 jdEnd`
 * (TDB), `40: 128-byte ASCII attribution`, `168: nSeg x 40-byte entries {i32 target, i32 center, f64 jdInit,
 * f64 intervalDays, i32 nCoef, i32 nRec, i64 dataOffset}`, then per segment nRec records of 3 x nCoef f64
 * (X c0..c(n-1), then Y, then Z; km).
 *
 * Keeps the last decoded record of each segment; not thread-safe.
 */
internal class ChebyshevBin private constructor(
    private val src: BinSource,
    val emrat: Double,
    val jdStart: Double,
    val jdEnd: Double,
    val attribution: String,
    private val segments: List<Segment>,
) {
    private class Segment(
        val target: Int,
        val center: Int,
        val jdInit: Double,
        val intervalDays: Double,
        val nCoef: Int,
        val nRec: Int,
        val dataOffset: Long,
    ) {
        val recordBytes = COMPONENTS * nCoef * F64_BYTES
        val raw = ByteArray(recordBytes)
        val coef = DoubleArray(COMPONENTS * nCoef)
        var loadedRecord = -1
        var s = 0.0 // Chebyshev argument of the last locate() call
    }

    val segmentCount: Int get() = segments.size

    /** Index of the segment `center -> target`, or -1 when the file has none. */
    fun segmentIndex(center: Int, target: Int): Int =
        segments.indexOfFirst { it.center == center && it.target == target }

    /** Position (km) of segment [seg] at Julian Day `jd + extraDays` (TDB). The sum is formed after `jd - jdInit`. */
    fun position(seg: Int, jd: Double, extraDays: Double): Vec3 {
        val g = locate(seg, jd, extraDays)
        val n = g.nCoef
        return Vec3(
            Chebyshev.value(g.coef, 0, n, g.s),
            Chebyshev.value(g.coef, n, n, g.s),
            Chebyshev.value(g.coef, 2 * n, n, g.s),
        )
    }

    /** Velocity (km/day) of segment [seg] at Julian Day `jd + extraDays` (TDB). */
    fun velocity(seg: Int, jd: Double, extraDays: Double): Vec3 {
        val g = locate(seg, jd, extraDays)
        val n = g.nCoef
        val k = 2.0 / g.intervalDays
        return Vec3(
            k * Chebyshev.derivative(g.coef, 0, n, g.s),
            k * Chebyshev.derivative(g.coef, n, n, g.s),
            k * Chebyshev.derivative(g.coef, 2 * n, n, g.s),
        )
    }

    /** Selects (and, when it changed, loads) the record for the instant and sets the Chebyshev argument. */
    private fun locate(seg: Int, jd: Double, extraDays: Double): Segment {
        val g = segments[seg]
        val dt = (jd - g.jdInit) + extraDays
        val r = floor(dt / g.intervalDays).toInt().coerceIn(0, g.nRec - 1)
        if (r != g.loadedRecord) {
            src.read(g.dataOffset + r.toLong() * g.recordBytes, g.raw, 0, g.recordBytes)
            for (i in g.coef.indices) g.coef[i] = Double.fromBits(i64(g.raw, i * F64_BYTES))
            g.loadedRecord = r
        }
        g.s = 2.0 * (dt - r * g.intervalDays) / g.intervalDays - 1.0
        return g
    }

    companion object {
        private const val MAGIC = "PPDE440\u0000"
        private const val VERSION = 1
        private const val HEADER_BYTES = 168
        private const val ENTRY_BYTES = 40
        private const val ATTRIBUTION_OFFSET = 40
        private const val ATTRIBUTION_BYTES = 128
        private const val MAX_SEGMENTS = 64
        private const val MAX_COEFFICIENTS = 64
        private const val COMPONENTS = 3
        private const val F64_BYTES = 8
        private const val I32_BYTES = 4
        private const val HASH_CHUNK_BYTES = 1 shl 16
        private const val BYTE_MASK = 0xFF
        private const val BYTE_MASK_LONG = 0xFFL
        private const val BITS_PER_BYTE = 8
        private const val OFF_VERSION = 8
        private const val OFF_SEGMENT_COUNT = 12
        private const val OFF_EMRAT = 16
        private const val OFF_JD_START = 24
        private const val OFF_JD_END = 32
        private const val ENTRY_OFF_CENTER = 4
        private const val ENTRY_OFF_JD_INIT = 8
        private const val ENTRY_OFF_INTERVAL = 16
        private const val ENTRY_OFF_COEF_COUNT = 24
        private const val ENTRY_OFF_RECORD_COUNT = 28
        private const val ENTRY_OFF_DATA = 32

        /**
         * Validates magic and version, then the SHA-256 of the whole source against [expectedSha256Hex]
         * (hex, either case), then the segment table. Throws [IllegalArgumentException] on any mismatch.
         */
        fun open(src: BinSource, expectedSha256Hex: String): ChebyshevBin {
            require(src.size >= HEADER_BYTES) { "ephemeris file too short: ${src.size} bytes" }
            val header = ByteArray(HEADER_BYTES)
            src.read(0, header, 0, HEADER_BYTES)
            val magic = MAGIC.encodeToByteArray()
            require(magic.indices.all { header[it] == magic[it] }) { "bad ephemeris magic" }
            val version = i32(header, OFF_VERSION)
            require(version == VERSION) { "unsupported ephemeris version $version" }
            val actual = sha256Hex(src)
            require(actual.equals(expectedSha256Hex.trim(), ignoreCase = true)) { "ephemeris SHA-256 mismatch" }

            val nSeg = i32(header, OFF_SEGMENT_COUNT)
            require(nSeg in 1..MAX_SEGMENTS) { "bad segment count $nSeg" }
            val tableBytes = nSeg * ENTRY_BYTES
            require(src.size >= HEADER_BYTES + tableBytes) { "ephemeris segment table truncated" }
            val table = ByteArray(tableBytes)
            src.read(HEADER_BYTES.toLong(), table, 0, tableBytes)
            val segments = List(nSeg) { k -> parseEntry(table, k * ENTRY_BYTES, src.size) }
            val attribution = header.decodeToString(ATTRIBUTION_OFFSET, ATTRIBUTION_OFFSET + ATTRIBUTION_BYTES)
                .trimEnd('\u0000')
            return ChebyshevBin(
                src, f64(header, OFF_EMRAT), f64(header, OFF_JD_START), f64(header, OFF_JD_END), attribution, segments,
            )
        }

        private fun parseEntry(table: ByteArray, o: Int, fileSize: Long): Segment {
            val g = Segment(
                target = i32(table, o),
                center = i32(table, o + ENTRY_OFF_CENTER),
                jdInit = f64(table, o + ENTRY_OFF_JD_INIT),
                intervalDays = f64(table, o + ENTRY_OFF_INTERVAL),
                nCoef = i32(table, o + ENTRY_OFF_COEF_COUNT).also {
                    require(it in 1..MAX_COEFFICIENTS) { "bad coefficient count $it" }
                },
                nRec = i32(table, o + ENTRY_OFF_RECORD_COUNT),
                dataOffset = i64(table, o + ENTRY_OFF_DATA),
            )
            require(g.nRec >= 1 && g.intervalDays > 0.0 && g.jdInit.isFinite()) { "bad segment entry" }
            require(g.dataOffset >= HEADER_BYTES && g.dataOffset + g.nRec.toLong() * g.recordBytes <= fileSize) {
                "segment data out of file bounds"
            }
            return g
        }

        private fun sha256Hex(src: BinSource): String {
            val sha = Sha256()
            val buf = ByteArray(HASH_CHUNK_BYTES)
            var pos = 0L
            while (pos < src.size) {
                val n = minOf(buf.size.toLong(), src.size - pos).toInt()
                src.read(pos, buf, 0, n)
                sha.update(buf, 0, n)
                pos += n
            }
            return Sha256.hex(sha.digest())
        }

        private fun i32(b: ByteArray, o: Int): Int {
            var v = 0
            for (k in I32_BYTES - 1 downTo 0) v = (v shl BITS_PER_BYTE) or (b[o + k].toInt() and BYTE_MASK)
            return v
        }

        private fun i64(b: ByteArray, o: Int): Long {
            var v = 0L
            for (k in F64_BYTES - 1 downTo 0) v = (v shl BITS_PER_BYTE) or (b[o + k].toLong() and BYTE_MASK_LONG)
            return v
        }

        private fun f64(b: ByteArray, o: Int): Double = Double.fromBits(i64(b, o))
    }
}
