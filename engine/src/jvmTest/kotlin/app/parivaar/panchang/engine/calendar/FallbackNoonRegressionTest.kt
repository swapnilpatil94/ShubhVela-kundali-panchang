package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.RiseSet
import app.parivaar.panchang.engine.calendar.internal.DayLookup
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression (docs/ENGINE_DIFFS.md M04): Tromso polar-night fallback nights must grow smoothly day to day (the
 * culmination is found to 1 ms, not 1 s), so the MAX_COVERAGE nishita tie of masik_shivratri in December 2025
 * goes to the day with the longer nishita, 2025-12-18.
 */
@Tag("golden")
class FallbackNoonRegressionTest {
    private val tromso = Place(69.6492, 18.9553, 0.0)

    @Test
    fun fallbackNightsGrowMonotonically() {
        val days = DayLookup.of(RiseSet(M04Golden.PRECISION.eph()), tromso, CalcSettings()) { OFFSET }
        val nights = (16..19).map { days.sunDay(LocalDate(2025, 12, it)).let { d -> d.nextSunrise - d.sunset } }
        assertTrue(nights.zipWithNext().all { (a, b) -> a < b }, "fallback nights not increasing")
    }

    @Test
    fun masikShivratriDecember2025TakesTheLongerNishita() {
        val defs = M04Festivals.defs.filter { it.id == "masik_shivratri" }
        val occ = FestivalEngine(M04Golden.PRECISION.eph())
            .occurrences(defs, LocalDate(2025, 12, 1), LocalDate(2025, 12, 31), tromso) { OFFSET }
        assertEquals(listOf(LocalDate(2025, 12, 18)), occ.map { it.date })
    }

    private companion object {
        const val OFFSET = 3_600
    }
}
