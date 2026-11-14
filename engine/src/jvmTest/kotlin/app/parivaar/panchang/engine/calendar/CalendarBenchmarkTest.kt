package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.GoldenLoader
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * MASTER section 8 year budget (docs/specs/M04.md section 4): Delhi, +05:30, default settings. `FestivalEngine.year`
 * of all 45 definitions for 2026 and `CalendarCalculator.days(2026-01-01, 365)` must each take <= 400 ms (5 warm-ups,
 * median of 10, a fresh engine per measured run so no result cache is reused) on each backend. The eclipse year is
 * printed for information.
 */
@Tag("benchmark")
class CalendarBenchmarkTest {
    private val delhi = Place(28.6139, 77.2090, 0.0)
    private val first = LocalDate(2026, 1, 1)

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun time(block: () -> Int): Pair<Double, Int> {
        val t0 = System.nanoTime()
        val n = block()
        return (System.nanoTime() - t0) / NS_PER_MS to n
    }

    private fun measure(block: () -> Int): Double {
        repeat(WARMUPS) { block() }
        val runs = List(RUNS) { time(block) }
        assertTrue(runs.all { it.second > 0 }, "work was done")
        return median(runs.map { it.first })
    }

    private fun festivals(open: () -> Ephemeris): Double = measure {
        FestivalEngine(open()).year(M04Festivals.defs, 2026, delhi) { OFFSET }.size
    }

    private fun days(open: () -> Ephemeris): Double = measure {
        CalendarCalculator(open()).days(first, YEAR, delhi) { OFFSET }.size
    }

    private fun eclipseYear(open: () -> Ephemeris): Double = time {
        EclipseSearch(open()).global(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")).size
    }.first

    @Test
    fun yearOfFestivalsAndDaysUnder400msOnBothBackends() {
        val backends = listOf<Pair<String, () -> Ephemeris>>(
            "Precision (DE440)" to { GoldenLoader.openDe440() },
            "Base (Astronomy Engine)" to { AstronomyEngineEphemeris() },
        )
        val results = backends.map { (name, open) -> Triple(name, festivals(open), days(open)) }
        for ((name, fest, day) in results) {
            println("BENCHMARK M04 $name: festivals year median = $fest ms, calendar days = $day ms (limit $LIMIT_MS)")
        }
        for ((name, open) in backends) {
            println("BENCHMARK M04 $name: eclipse year (2026) = ${eclipseYear(open)} ms (information)")
        }
        for ((name, fest, day) in results) {
            assertTrue(fest <= LIMIT_MS, "$name festivals year median $fest ms")
            assertTrue(day <= LIMIT_MS, "$name calendar days year median $day ms")
        }
    }

    private companion object {
        const val OFFSET = 19_800
        const val YEAR = 365
        const val WARMUPS = 5
        const val RUNS = 10
        const val LIMIT_MS = 400.0
        const val NS_PER_MS = 1_000_000.0
    }
}
