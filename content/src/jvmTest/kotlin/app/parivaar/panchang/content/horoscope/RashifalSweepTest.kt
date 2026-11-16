package app.parivaar.panchang.content.horoscope

import app.parivaar.panchang.content.ContentIds
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.jyotish.ChartCalculator
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * docs/specs/M11.md section 4, `RashifalSweepTest`: EngineTransitSource at Delhi with default CalcSettings.
 * Windows start at 00:30 UTC (about Delhi sunrise); the real panchang-day anchor is the caller's (V1). Engine
 * accuracy is covered by the M02 goldens; no new tolerance here.
 */
class RashifalSweepTest {
    private val eph = AstronomyEngineEphemeris()
    private val calc = ChartCalculator(eph)
    private val delhi = Place(28.6139, 77.209)
    private val settings = CalcSettings()
    private val src = EngineTransitSource(calc, Gochar(eph), delhi, settings)
    private val rules = RashifalRules(src) { 1 }
    private val firstDay = 20454L // 2026-01-01
    private val ingressIds = setOf(ContentIds.MOON_SHIFT, ContentIds.INGRESS)

    private fun startOf(epochDay: Long): Instant =
        Instant.fromEpochSeconds(epochDay * 86_400 + 1_800)

    @Test
    fun everyDayAndWeekUsesRequiredIdsOnly() {
        var shifts = 0
        for (d in firstDay until firstDay + 366) {
            val w = PeriodWindow(startOf(d), startOf(d + 1), d)
            for (s in Rashi.entries) shifts += check(rules.h1(s, Period.DAY, w))
        }
        // The Moon changes sign about 160 times a year; each shows for all 12 signs.
        assertTrue(shifts in 1_800..2_100, "moon shifts $shifts")
        // Weeks from Monday 2025-12-29 (epochDay 20451).
        for (k in 0 until 53) {
            val monday = 20451L + 7 * k
            val w = PeriodWindow(startOf(monday), startOf(monday + 7), Math.floorDiv(monday, 7L))
            for (s in Rashi.entries) check(rules.h1(s, Period.WEEK, w))
        }
    }

    private fun check(r: Reading): Int {
        for (l in r.lines) {
            val base = l.id.replace(Regex("\\.v\\d+$"), ".v1")
            assertTrue(base in ContentIds.REQUIRED, "id not required")
        }
        val n = r.lines.count { it.id in ingressIds }
        assertTrue(n <= 2, "too many ingress lines")
        return n
    }

    @Test
    fun sourceMatchesChartCalculator() {
        for (i in 0 until 50) {
            val at = startOf(firstDay) + (i * 7.3).days
            val chart = calc.compute(at, delhi, settings)
            for (g in Graha.entries) {
                assertEquals(chart.of(g).rashi, src.rashi(g, at))
                assertEquals(chart.of(g).retrograde, src.retrograde(g, at))
            }
            assertEquals(chart.of(Graha.MOON).nakshatra, src.nakshatra(at))
        }
    }
}
