package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.GoldenLoader
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MASTER section 8 / C15 (docs/specs/M03.md section 4): Delhi, +05:30, default settings. A year of
 * `days(2026-01-01, 365)` must take < 400 ms (5 warm-ups, median of 10) and `day()` on 100 distinct dates with a
 * fresh calculator per date must take <= 2 ms median, on each backend.
 */
@Tag("benchmark")
class PanchangBenchmarkTest {
    private val delhi = Place(28.6139, 77.2090, 0.0)
    private val first = LocalDate(2026, 1, 1)

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun yearMillis(eph: Ephemeris): Double {
        val calc = PanchangCalculator(eph, M03Golden.tables)
        var sink = 0
        fun once(): Double {
            val t0 = System.nanoTime()
            val days = calc.days(first, YEAR, delhi) { OFFSET }
            sink += days.size
            return (System.nanoTime() - t0) / NS_PER_MS
        }
        repeat(WARMUPS) { once() }
        val runs = List(RUNS) { once() }
        assertEquals(YEAR * (WARMUPS + RUNS), sink, "days computed")
        return median(runs)
    }

    private fun dayMillis(eph: Ephemeris): Double {
        var sink = 0
        fun once(k: Int): Double {
            val date = first.plus(DAY_STEP * k, DateTimeUnit.DAY)
            val calc = PanchangCalculator(eph, M03Golden.tables)
            val t0 = System.nanoTime()
            sink += calc.day(date, OFFSET, delhi).windows.size
            return (System.nanoTime() - t0) / NS_PER_MS
        }
        repeat(WARMUPS) { once(DAYS + it) }
        val runs = List(DAYS) { once(it) }
        assertTrue(sink > 0, "windows computed")
        return median(runs)
    }

    private fun measure(name: String, open: () -> Ephemeris) = Triple(name, yearMillis(open()), dayMillis(open()))

    @Test
    fun yearUnder400msAndDayUnder2msOnBothBackends() {
        val results = listOf(
            measure("Precision (DE440)") { GoldenLoader.openDe440() },
            measure("Base (Astronomy Engine)") { AstronomyEngineEphemeris() },
        )
        for ((name, year, day) in results) {
            println("BENCHMARK M03 $name: year median = $year ms (< $YEAR_LIMIT_MS); day median = $day ms")
        }
        for ((name, year, day) in results) {
            assertTrue(year < YEAR_LIMIT_MS, "$name year median $year ms")
            assertTrue(day <= DAY_LIMIT_MS, "$name day median $day ms")
        }
    }

    private companion object {
        const val OFFSET = 19_800
        const val YEAR = 365
        const val WARMUPS = 5
        const val RUNS = 10
        const val DAYS = 100
        const val DAY_STEP = 3
        const val NS_PER_MS = 1e6
        const val YEAR_LIMIT_MS = 400.0
        const val DAY_LIMIT_MS = 2.0
    }
}
