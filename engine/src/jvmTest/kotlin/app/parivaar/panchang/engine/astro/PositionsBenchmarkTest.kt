package app.parivaar.panchang.engine.astro

import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

/** B6: one year of daily positions for every Body must take < 300 ms per backend (docs/specs/M01.md section 4). */
@Tag("benchmark")
class PositionsBenchmarkTest {
    private fun medianMillis(eph: Ephemeris): Double {
        val epochs = (0 until 366).map { JulianDay(2461041.5 + it) }
        var sink = 0.0
        fun once(): Double {
            val t0 = System.nanoTime()
            for (tt in epochs) for (b in Body.entries) sink += eph.geoApparent(b, tt).lonDeg
            return (System.nanoTime() - t0) / 1e6
        }
        repeat(5) { once() }
        val runs = List(10) { once() }.sorted()
        check(sink.isFinite())
        return (runs[4] + runs[5]) / 2.0
    }

    @Test
    fun oneYearOfDailyPositionsUnder300ms() {
        val precision = medianMillis(GoldenLoader.openDe440())
        val base = medianMillis(AstronomyEngineEphemeris())
        println("BENCHMARK median Precision (DE440) = $precision ms; Base (Astronomy Engine) = $base ms")
        assertTrue(precision < 300.0, "Precision median $precision ms")
        assertTrue(base < 300.0, "Base median $base ms")
    }
}
