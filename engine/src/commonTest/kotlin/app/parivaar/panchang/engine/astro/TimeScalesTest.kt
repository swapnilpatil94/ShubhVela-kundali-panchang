package app.parivaar.panchang.engine.astro

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** docs/specs/M01.md section 4, `TimeScalesTest`. */
class TimeScalesTest {
    private fun at(iso: String) = Instant.parse(iso)

    private fun ttMinusUtcViaJd(i: Instant): Double =
        (TimeScales.utcToTt(i).value - TimeScales.jdUtc(i).value) * SECONDS_PER_DAY

    @Test
    fun jdUtcOfKnownEpochs() {
        assertEquals(2440587.5, TimeScales.jdUtc(at("1970-01-01T00:00:00Z")).value, 0.0)
        assertEquals(2451545.0, TimeScales.jdUtc(at("2000-01-01T12:00:00Z")).value, 0.0)
        assertEquals(2451545.0, TimeScales.ut1(at("2000-01-01T12:00:00Z")).value, 0.0)
    }

    @Test
    fun ttMinusUtcFromLeapSecondTable() {
        val cases = listOf(
            "1972-01-01T00:00:00Z" to 42.184,
            "2016-12-31T23:59:59Z" to 68.184,
            "2017-01-01T00:00:00Z" to 69.184,
        )
        for ((iso, expected) in cases) {
            // Exact rule value, and the same through the (40 microsecond resolution) Julian Day.
            assertEquals(expected, TimeScales.ttMinusUtcSeconds(at(iso), TimeTables.DEFAULT), 1e-9, iso)
            assertEquals(expected, ttMinusUtcViaJd(at(iso)), 1e-4, iso)
        }
    }

    @Test
    fun utcToTtAtJ2000Noon() {
        assertEquals(2451545.000742870, TimeScales.utcToTt(at("2000-01-01T12:00:00Z")).value, 2e-9)
    }

    @Test
    fun deltaTPolynomialValues() {
        val cases = listOf(
            1900.0 to -2.79, 1920.0 to 21.20, 1950.0 to 29.07, 1975.0 to 45.45, 2000.0 to 63.86,
            2026.0 to 75.074584, 2050.0 to 93.00, 2100.0 to 202.74,
        )
        for ((y, expected) in cases) assertEquals(expected, TimeScales.deltaTSeconds(y), 1e-6, "deltaT($y)")
    }

    @Test
    fun before1972InstantIsUt1AndUsesDeltaT() {
        assertEquals(29.07, ttMinusUtcViaJd(at("1950-01-01T00:00:00Z")), 0.01)
    }

    @Test
    fun defaultTablesMatchTheOracleHorizon() {
        // Must stay equal to tools/oracle/timescale.py VALID_THROUGH (placeholder; TODO(owner)).
        assertEquals(at("2027-01-01T00:00:00Z"), TimeTables.DEFAULT.validThrough)
        assertEquals(28, TimeTables.DEFAULT.leapSeconds.size)
        assertEquals(at("1972-01-01T00:00:00Z") to 10, TimeTables.DEFAULT.leapSeconds.first())
        assertEquals(at("2017-01-01T00:00:00Z") to 37, TimeTables.DEFAULT.leapSeconds.last())
    }

    @Test
    fun afterValidThroughHoldsTheTableValueWithASmallSlope() {
        // Fable review M2: 69.184 s held at the horizon, then +0.1 s per Julian year (no polynomial step).
        val v = TimeTables.DEFAULT.validThrough
        assertEquals(69.184, TimeScales.ttMinusUtcSeconds(v, TimeTables.DEFAULT), 1e-9)
        val oneYear = Instant.fromEpochSeconds(v.epochSeconds + 31_557_600L)
        assertEquals(69.284, TimeScales.ttMinusUtcSeconds(oneYear, TimeTables.DEFAULT), 1e-9)
        val y2100 = at("2100-01-01T00:00:00Z")
        val years = (y2100.epochSeconds - v.epochSeconds) / 31_557_600.0
        assertEquals(69.184 + 0.1 * years, TimeScales.ttMinusUtcSeconds(y2100, TimeTables.DEFAULT), 1e-9)
    }

    @Test
    fun noStepAtValidThrough() {
        val v = TimeTables.DEFAULT.validThrough
        val before = TimeScales.ttMinusUtcSeconds(v - 1.seconds, TimeTables.DEFAULT)
        val after = TimeScales.ttMinusUtcSeconds(v + 1.seconds, TimeTables.DEFAULT)
        assertTrue(abs(after - before) < 0.01, "step at validThrough: ${after - before} s")
        // The TT Julian Day is continuous too (40 microsecond resolution of the JD).
        val jump = (TimeScales.utcToTt(v + 1.seconds).value - TimeScales.utcToTt(v - 1.seconds).value) * 86400.0
        assertEquals(2.0, jump, 0.01)
        // And the inverse is unique around it.
        val tt = TimeScales.utcToTt(v + 1.seconds)
        assertTrue((TimeScales.ttToUtc(tt) - (v + 1.seconds)).absoluteValue < 1.milliseconds)
    }

    @Test
    fun tdbMinusTtIsBoundedPeriodicTerm() {
        // g = 357.53 deg at J2000: 0.001657 sin g + 0.000014 sin 2g.
        assertEquals(-7.26e-5, TimeScales.tdbMinusTtSeconds(JulianDay(2451545.0)), 1e-6)
        for (k in 0 until 400) {
            assertTrue(abs(TimeScales.tdbMinusTtSeconds(JulianDay(2451545.0 + k))) <= 0.001672)
        }
    }

    @Test
    fun roundTripWithinOneMillisecond() {
        val rng = Random(20261113)
        val lo = at("1900-01-01T00:00:00Z").epochSeconds
        val hi = at("2100-01-01T00:00:00Z").epochSeconds
        val steps = TimeTables.DEFAULT.leapSeconds.map { it.first } + TimeTables.DEFAULT.validThrough
        var checked = 0
        while (checked < 1000) {
            val i = Instant.fromEpochSeconds(rng.nextLong(lo, hi), rng.nextInt(0, 1_000_000_000))
            if (steps.any { (i - it).absoluteValue <= 1.days }) continue
            val back = TimeScales.ttToUtc(TimeScales.utcToTt(i))
            val errorMs = (back - i).inWholeMicroseconds / 1000.0
            assertTrue(abs(errorMs) <= 1.0, "round trip of $i is off by $errorMs ms")
            checked++
        }
    }

    private companion object {
        const val SECONDS_PER_DAY = 86400.0
    }
}
