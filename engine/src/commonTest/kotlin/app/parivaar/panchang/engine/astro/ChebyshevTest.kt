package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.astro.internal.Chebyshev
import app.parivaar.panchang.engine.astro.internal.ChebyshevBin
import app.parivaar.panchang.engine.astro.internal.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** docs/specs/M01.md section 4, `ChebyshevTest`: synthetic bin in the section 3.4 layout. */
class ChebyshevTest {
    private class LeWriter {
        val out = ArrayList<Byte>()

        fun bytes(b: ByteArray) = b.forEach { out.add(it) }

        fun i32(v: Int) = repeat(4) { out.add((v ushr (8 * it)).toByte()) }

        fun i64(v: Long) = repeat(8) { out.add((v ushr (8 * it)).toByte()) }

        fun f64(v: Double) = i64(v.toRawBits())
    }

    private val jdInit = 2451545.0

    // Record 0 is the spec vector; record 1 exercises record selection.
    private val records = listOf(
        listOf(doubleArrayOf(0.0, 0.0, 0.0, 1.0), doubleArrayOf(1.0, 2.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
        listOf(doubleArrayOf(5.0, 1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0, 0.0), doubleArrayOf(7.0, 0.0, 0.0, 0.0)),
    )

    private fun syntheticBin(magic: String = "PPDE440\u0000", version: Int = 1): ByteArray {
        val w = LeWriter()
        w.bytes(magic.encodeToByteArray())
        w.i32(version)
        w.i32(1) // nSeg
        w.f64(81.3005682) // EMRAT
        w.f64(jdInit)
        w.f64(jdInit + 4.0)
        w.bytes("synthetic test data".encodeToByteArray().copyOf(128))
        // entry: target, center, jdInit, intervalDays, nCoef, nRec, dataOffset
        w.i32(1); w.i32(0); w.f64(jdInit); w.f64(2.0); w.i32(4); w.i32(records.size); w.i64(168L + 40L)
        for (rec in records) for (comp in rec) for (c in comp) w.f64(c)
        return w.out.toByteArray()
    }

    private fun open(bytes: ByteArray, sha: String = Sha256.hashHex(bytes)) =
        ChebyshevBin.open(ByteArrayBinSource(bytes), sha)

    private fun openDe440(bytes: ByteArray) = De440Ephemeris.open(ByteArrayBinSource(bytes), Sha256.hashHex(bytes))

    @Test
    fun clenshawAndDerivativeRecurrence() {
        val c = doubleArrayOf(9.0, 0.0, 0.0, 0.0, 1.0) // offset 1: T3
        assertEquals(-1.0, Chebyshev.value(c, 1, 4, 0.5), 1e-15)
        assertEquals(0.0, Chebyshev.derivative(c, 1, 4, 0.5), 1e-15)
        assertEquals(3.5, Chebyshev.value(doubleArrayOf(3.5), 0, 1, 0.3), 0.0)
        assertEquals(0.0, Chebyshev.derivative(doubleArrayOf(3.5), 0, 1, 0.3), 0.0)
    }

    @Test
    fun evaluatesTheSpecVector() {
        val bin = open(syntheticBin())
        val seg = bin.segmentIndex(center = 0, target = 1)
        val r = bin.position(seg, jdInit + 1.5, 0.0)
        val v = bin.velocity(seg, jdInit + 1.5, 0.0)
        assertEquals(-1.0, r.x, 1e-13); assertEquals(2.0, r.y, 1e-13); assertEquals(-0.5, r.z, 1e-13)
        assertEquals(0.0, v.x, 1e-13); assertEquals(2.0, v.y, 1e-13); assertEquals(2.0, v.z, 1e-13)
        // The same instant split as (jd, extra days) must agree.
        val r2 = bin.position(seg, jdInit + 1.0, 0.5)
        assertEquals(-1.0, r2.x, 1e-13); assertEquals(2.0, r2.y, 1e-13); assertEquals(-0.5, r2.z, 1e-13)
        assertEquals(81.3005682, bin.emrat, 0.0)
        assertEquals(-1, bin.segmentIndex(center = 3, target = 301))
    }

    @Test
    fun selectsAndClampsRecords() {
        val bin = open(syntheticBin())
        val seg = bin.segmentIndex(center = 0, target = 1)
        // Record 1 covers [jdInit + 2, jdInit + 4): s = 0.5 at jdInit + 3.5, X = 5 + s.
        assertEquals(5.5, bin.position(seg, jdInit + 3.5, 0.0).x, 1e-13)
        assertEquals(7.0, bin.position(seg, jdInit + 3.5, 0.0).z, 1e-13)
        assertEquals(1.0, bin.velocity(seg, jdInit + 3.5, 0.0).x, 1e-13)
        // Back to record 0 (cache replaced), then the exact end of coverage clamps to the last record (s = +1).
        assertEquals(-1.0, bin.position(seg, jdInit + 1.5, 0.0).x, 1e-13)
        assertEquals(6.0, bin.position(seg, jdInit + 4.0, 0.0).x, 1e-13)
    }

    @Test
    fun badMagicThrows() {
        val bytes = syntheticBin(magic = "PPDE441\u0000")
        assertFailsWith<IllegalArgumentException> { open(bytes) }
        assertFailsWith<IllegalArgumentException> { openDe440(bytes) }
    }

    @Test
    fun badVersionThrows() {
        val bytes = syntheticBin(version = 2)
        assertFailsWith<IllegalArgumentException> { open(bytes) }
        assertFailsWith<IllegalArgumentException> { openDe440(bytes) }
    }

    @Test
    fun badHashThrows() {
        val bytes = syntheticBin()
        val good = Sha256.hashHex(bytes)
        val wrong = (if (good[0] == '0') '1' else '0') + good.substring(1)
        assertFailsWith<IllegalArgumentException> { open(bytes, wrong) }
        assertFailsWith<IllegalArgumentException> { De440Ephemeris.open(ByteArrayBinSource(bytes), wrong) }
        // One flipped coefficient bit with the original hash is rejected too.
        val tampered = bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFailsWith<IllegalArgumentException> { open(tampered, good) }
    }

    @Test
    fun truncatedFileThrows() {
        val bytes = syntheticBin().copyOf(100)
        assertFailsWith<IllegalArgumentException> { open(bytes) }
    }

    @Test
    fun precisionBackendNeedsAllEightSegments() {
        val bytes = syntheticBin() // valid file, but only segment 0->1
        assertFailsWith<IllegalArgumentException> { openDe440(bytes) }
    }
}
