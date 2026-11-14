package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.Zodiac
import app.parivaar.panchang.engine.panchang.internal.arcIndex
import app.parivaar.panchang.engine.panchang.internal.reportedIndex
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** docs/specs/M03.md section 4, `LimbSolverTest` (Precision backend). */
class LimbSolverTest {
    private val eph = GoldenLoader.openDe440()
    private val solver = LimbSolver(eph)
    private val yearStart = Instant.parse("2026-01-01T00:00:00Z")
    private val yearEnd = Instant.parse("2027-01-01T00:00:00Z")

    /** F recomputed here from the spec formulas, independent of the solver's code path. */
    private fun f(k: LimbKind, at: Instant, id: AyanamsaId = AyanamsaId.LAHIRI): Double {
        val tt = TimeScales.utcToTt(at)
        val sun = eph.geoApparent(Body.SUN, tt).lonDeg
        val moon = eph.geoApparent(Body.MOON, tt).lonDeg
        return when (k) {
            LimbKind.TITHI, LimbKind.KARANA -> Zodiac.wrap360(moon - sun)
            LimbKind.NAKSHATRA, LimbKind.MOON_RASHI -> Zodiac.wrap360(moon - Ayanamsa.trueDeg(id, tt))
            LimbKind.YOGA -> Zodiac.wrap360(moon + sun - 2.0 * Ayanamsa.trueDeg(id, tt))
        }
    }

    private fun floorIndex(k: LimbKind, fDeg: Double): Int =
        min(k.divisions - 1, floor(fDeg * k.divisions / 360.0).toInt()) + k.firstIndex

    private fun next(k: LimbKind, index: Int): Int =
        if (index == k.lastIndex) k.firstIndex else index + 1

    @Test
    fun `index consistent with F at 1000 seeded instants and the span contains the instant`() {
        val rnd = Random(20261116)
        val lo = Instant.parse("1900-02-01T00:00:00Z").epochSeconds
        val hi = Instant.parse("2099-11-30T00:00:00Z").epochSeconds
        repeat(1000) {
            val at = Instant.fromEpochSeconds(rnd.nextLong(lo, hi), rnd.nextInt(1_000_000_000))
            for (k in LimbKind.entries) {
                val fDeg = f(k, at)
                assertEquals(floorIndex(k, fDeg), solver.indexAt(k, at), "$k index")
                val span = solver.spanAt(k, at)
                assertTrue(span.start <= at && at < span.end, "$k span contains the query")
                assertFalse(span.kshaya || span.adhika)
                val clear = at - span.start > 1.milliseconds && span.end - at > 1.milliseconds
                if (clear) assertEquals(solver.indexAt(k, at), span.index, "$k span index")
                val arc = 360.0 / k.divisions
                val j0 = span.index - k.firstIndex
                assertTrue(abs(Zodiac.wrap180(f(k, span.start) - j0 * arc)) < 1e-6, "$k start on boundary")
                assertTrue(abs(Zodiac.wrap180(f(k, span.end) - (j0 + 1) * arc)) < 1e-6, "$k end on boundary")
            }
        }
    }

    @Test
    fun `spans over 2026 tile bit-equal with consecutive indices`() {
        for (k in LimbKind.entries) {
            val list = solver.spans(k, yearStart, yearEnd)
            assertTrue(list.first().start <= yearStart && yearStart < list.first().end, "$k first")
            assertTrue(list.last().start < yearEnd && list.last().end >= yearEnd, "$k last")
            for (i in 1 until list.size) {
                assertEquals(list[i - 1].end, list[i].start, "$k tiles")
                assertEquals(next(k, list[i - 1].index), list[i].index, "$k consecutive")
            }
            val expected = 365.0 / mapOf(
                LimbKind.TITHI to 29.530589 / 30, LimbKind.KARANA to 29.530589 / 60,
                LimbKind.NAKSHATRA to 27.321582 / 27, LimbKind.YOGA to 1.0 / (1.0 / 27.321582 + 1.0 / 365.2422) / 27,
                LimbKind.MOON_RASHI to 27.321582 / 12,
            ).getValue(k)
            assertTrue(abs(list.size - expected) < 3.0, "$k count ${list.size} vs $expected")
        }
    }

    @Test
    fun `spanAt equals the spans element bit-equal from 20 query instants, fresh and warm solvers`() {
        val year = LimbKind.entries.associateWith { solver.spans(it, yearStart, yearEnd) }
        val rnd = Random(7)
        repeat(20) {
            val at = yearStart + (rnd.nextDouble() * 360.0).days
            for (k in LimbKind.entries) {
                val expected = year.getValue(k).single { at >= it.start && at < it.end }
                assertEquals(expected, LimbSolver(eph).spanAt(k, at), "$k fresh")
                assertEquals(expected, solver.spanAt(k, at), "$k warm")
                val mid = expected.start + (expected.end - expected.start) / 2
                assertEquals(expected, LimbSolver(eph).spanAt(k, mid), "$k mid")
            }
        }
    }

    @Test
    fun `TITHI boundaries are the even KARANA boundaries bit-equal`() {
        val tithi = solver.spans(LimbKind.TITHI, yearStart, yearEnd)
        val karanaEnds = LimbSolver(eph).spans(LimbKind.KARANA, yearStart, yearEnd)
            .filter { it.index % 2 == 0 }.map { it.end }.toSet()
        for (t in tithi) assertTrue(t.end in karanaEnds, "tithi end is a karana end")
        assertEquals(tithi.size, tithi.count { it.end in karanaEnds })
    }

    @Test
    fun `new moon of 2024-04-08 within 120 s of the published minute`() {
        val near = Instant.parse("2024-04-08T18:21:00Z")
        val spans = solver.spans(LimbKind.TITHI, near - 2.days, near + 2.days)
        val newMoon = spans.zipWithNext().single { (a, b) -> a.index == 30 && b.index == 1 }.first.end
        assertTrue(abs((newMoon - near).inWholeMilliseconds) <= 120_000, "new moon off by ${newMoon - near}")
    }

    @Test
    fun `floor rule at the top of the circle`() {
        assertEquals(26, arcIndex(LimbKind.YOGA, 359.99))
        assertEquals(26, reportedIndex(LimbKind.YOGA, arcIndex(LimbKind.YOGA, 359.99)))
        assertEquals(26, arcIndex(LimbKind.YOGA, Math.nextDown(360.0)))
        assertEquals(30, reportedIndex(LimbKind.TITHI, arcIndex(LimbKind.TITHI, Math.nextDown(360.0))))
        assertEquals(1, reportedIndex(LimbKind.KARANA, arcIndex(LimbKind.KARANA, 0.0)))
        assertEquals(11, arcIndex(LimbKind.MOON_RASHI, 359.99))
    }

    @Test
    fun `TRUE_CHITRA shifts NAKSHATRA boundaries, not TITHI`() {
        val from = Instant.parse("2026-03-01T00:00:00Z")
        val to = from + 30.days
        val chitra = CalcSettings(ayanamsa = AyanamsaId.TRUE_CHITRA)
        assertEquals(solver.spans(LimbKind.TITHI, from, to), solver.spans(LimbKind.TITHI, from, to, chitra))
        val lahiri = solver.spans(LimbKind.NAKSHATRA, from, to).drop(1).map { it.start }
        val shifted = solver.spans(LimbKind.NAKSHATRA, from, to, chitra).drop(1).map { it.start }
        assertEquals(lahiri.size, shifted.size)
        for ((a, b) in lahiri.zip(shifted)) assertNotEquals(a, b)
        assertTrue(lahiri.zip(shifted).all { (a, b) -> abs((a - b).inWholeSeconds) < 6 * 3600 })
        assertTrue(abs((lahiri[0] - shifted[0]).inWholeMilliseconds) > 1.seconds.inWholeMilliseconds)
    }
}
