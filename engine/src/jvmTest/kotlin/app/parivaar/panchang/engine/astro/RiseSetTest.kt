package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.HighLatitudeRule
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.SunriseConvention
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant
import io.github.cosinekitty.astronomy.Body as AeBody

/** docs/specs/M01.md section 4, `RiseSetTest` (Precision backend), plus geometry cross-checks. */
class RiseSetTest {
    private val riseSet = RiseSet(GoldenLoader.openDe440())
    private val equinox = Instant.parse("2026-03-20T00:00:00Z")
    private val mumbai = Place(19.0760, 72.8777)
    private val tromso = Place(69.6492, 18.9553)
    private val defaults = CalcSettings()

    private fun events(
        body: Body,
        place: Place,
        start: Instant,
        settings: CalcSettings = defaults,
    ): List<RiseSetEvent> = assertIs<RiseSetResult.Events>(riseSet.find(body, place, start, settings)).list

    private fun first(kind: RiseSetKind, place: Place, start: Instant, convention: SunriseConvention): Instant =
        events(Body.SUN, place, start, CalcSettings(sunrise = convention)).first { it.kind == kind }.at

    /** f = altitude - h0 changes sign within +/- 0.5 s of [event], in the direction of its kind. */
    private fun assertCrossing(body: Body, place: Place, event: RiseSetEvent, settings: CalcSettings) {
        val before = riseSet.crossingDeg(body, place, event.at - HALF_SECOND, settings)
        val after = riseSet.crossingDeg(body, place, event.at + HALF_SECOND, settings)
        val label = "$body ${event.kind} at ${event.at} (${settings.sunrise}): f = $before -> $after deg"
        if (event.kind == RiseSetKind.RISE) {
            assertTrue(before < 0.0 && after >= 0.0, label)
        } else {
            assertTrue(before >= 0.0 && after < 0.0, label)
        }
    }

    @Test
    fun equatorAtTheEquinoxHasOneRiseThenOneSet() {
        val list = events(Body.SUN, Place(0.0, 0.0), equinox)
        assertEquals(listOf(RiseSetKind.RISE, RiseSetKind.SET), list.map { it.kind })
        assertTrue(list.none { it.isFallback })
        val dayLength = list[1].at - list[0].at
        assertTrue(dayLength >= 12.hours + 5.minutes && dayLength <= 12.hours + 9.minutes, "day length $dayLength")
        // Sunrise at longitude 0 on the equinox: 06:00 local apparent time less about 4 min, plus the
        // equation of time (about +7.5 min on 20 March).
        assertTrue(list[0].at - equinox in (6.hours)..(6.hours + 8.minutes), "sunrise ${list[0].at}")
    }

    @Test
    fun conventionsShiftMumbaiSunriseAsExpected() {
        val upperLimb = first(RiseSetKind.RISE, mumbai, equinox, SunriseConvention.UPPER_LIMB_REFRACTED)
        val centre = first(RiseSetKind.RISE, mumbai, equinox, SunriseConvention.CENTER_REFRACTED)
        val geometric = first(RiseSetKind.RISE, mumbai, equinox, SunriseConvention.CENTER_GEOMETRIC)
        val limbToCentre = (centre - upperLimb).toDouble(DurationUnit.SECONDS)
        val refraction = (geometric - centre).toDouble(DurationUnit.SECONDS)
        assertTrue(limbToCentre in 60.0..90.0, "centre - upper limb = $limbToCentre s")
        assertTrue(refraction in 135.0..190.0, "geometric - refracted centre = $refraction s")
        // Sunset moves the other way.
        val setUpperLimb = first(RiseSetKind.SET, mumbai, equinox, SunriseConvention.UPPER_LIMB_REFRACTED)
        val setGeometric = first(RiseSetKind.SET, mumbai, equinox, SunriseConvention.CENTER_GEOMETRIC)
        assertTrue(setUpperLimb - setGeometric in 195.seconds..280.seconds)
    }

    @Test
    fun tromsoMidnightSunAndPolarNightHaveNoEvent() {
        val summer = riseSet.find(Body.SUN, tromso, Instant.parse("2026-06-21T00:00:00Z"))
        assertEquals(RiseSetResult.NoEvent(alwaysAbove = true), summer)
        val winter = riseSet.find(Body.SUN, tromso, Instant.parse("2026-12-21T00:00:00Z"))
        assertEquals(RiseSetResult.NoEvent(alwaysAbove = false), winter)
    }

    @Test
    fun localNoonFallbackIsFlaggedAndCentredOnCulmination() {
        val settings = CalcSettings(highLatitudeRule = HighLatitudeRule.LOCAL_NOON_PM_6H)
        for (date in listOf("2026-06-21", "2026-12-21")) {
            val start = Instant.parse(date + "T00:00:00Z")
            val list = riseSet.sunWithFallback(tromso, start, settings)
            assertEquals(listOf(RiseSetKind.RISE, RiseSetKind.SET), list.map { it.kind }, date)
            assertTrue(list.all { it.isFallback }, date)
            assertEquals(12.hours, list[1].at - list[0].at, date)
            val noon = list[0].at + 6.hours
            // Tromso (18.9553 E): mean noon 10:44:11 UTC; equation of time within +/- 3 min on both dates.
            assertTrue(noon - start in (10.hours + 40.minutes)..(10.hours + 48.minutes), "$date culmination $noon")
            val top = riseSet.altitudeDeg(Body.SUN, tromso, noon)
            assertTrue(top >= riseSet.altitudeDeg(Body.SUN, tromso, noon - 3.seconds), "$date not a maximum")
            assertTrue(top >= riseSet.altitudeDeg(Body.SUN, tromso, noon + 3.seconds), "$date not a maximum")
        }
    }

    @Test
    fun nearestDayFallbackShiftsTheNearestDayWithBothEvents() {
        val settings = CalcSettings(highLatitudeRule = HighLatitudeRule.NEAREST_DAY)
        for (date in listOf("2026-06-21", "2026-12-21")) {
            val start = Instant.parse(date + "T00:00:00Z")
            val list = riseSet.sunWithFallback(tromso, start, settings)
            assertTrue(list.isNotEmpty() && list.all { it.isFallback }, date)
            assertTrue(list.any { it.kind == RiseSetKind.RISE } && list.any { it.kind == RiseSetKind.SET }, date)
            assertTrue(list.all { it.at >= start && it.at <= start + 24.hours }, "$date: outside the window")
            assertEquals(expectedNearestDay(start, settings), list, date)
        }
    }

    /** Spec 3.8 written out naively: window -k days, then +k days, first one with both kinds. */
    private fun expectedNearestDay(start: Instant, settings: CalcSettings): List<RiseSetEvent> {
        for (k in 1..183) {
            for (shift in listOf(-k, k)) {
                val r = riseSet.find(Body.SUN, tromso, start + shift.days, settings)
                if (r is RiseSetResult.Events && r.list.map { it.kind }.toSet().size == 2) {
                    assertTrue(k in 20..60, "nearest day with both events is $k days away")
                    return r.list.map { RiseSetEvent(it.kind, it.at - shift.days, isFallback = true) }
                }
            }
        }
        return emptyList()
    }

    @Test
    fun fallbackLeavesAnOrdinaryDayUntouched() {
        for (rule in HighLatitudeRule.entries) {
            val settings = CalcSettings(highLatitudeRule = rule)
            val list = riseSet.sunWithFallback(mumbai, equinox, settings)
            assertEquals(events(Body.SUN, mumbai, equinox, settings), list)
            assertTrue(list.none { it.isFallback })
        }
    }

    @Test
    fun everyReturnedInstantIsASignChangeWithinHalfASecond() {
        val places = listOf(
            Place(0.0, 0.0), mumbai, Place(51.5074, -0.1278), Place(-36.8485, 174.7633), Place(40.7128, -74.0060),
            Place(64.1466, -21.9426, 50.0), tromso,
        )
        var checked = 0
        for (place in places) {
            for (day in 0 until 12) {
                val start = equinox + (day * 31).days
                for (body in listOf(Body.SUN, Body.MOON)) {
                    checked += SunriseConvention.entries.sumOf { checkWindow(body, place, start, it) }
                }
            }
        }
        assertTrue(checked > 800, "only $checked events checked")
    }

    /** Checks every event of one window and returns how many there were (0 for NoEvent). */
    private fun checkWindow(body: Body, place: Place, start: Instant, convention: SunriseConvention): Int {
        val settings = CalcSettings(sunrise = convention)
        val result = riseSet.find(body, place, start, settings) as? RiseSetResult.Events ?: return 0
        assertTrue(result.list.isNotEmpty())
        assertEquals(result.list.sortedBy { it.at }, result.list)
        assertTrue(result.list.all { it.at >= start && it.at <= start + 24.hours })
        // Kinds alternate: the same horizon cannot be crossed upwards twice in a row.
        assertTrue(result.list.zipWithNext().all { (a, b) -> a.kind != b.kind })
        for (event in result.list) assertCrossing(body, place, event, settings)
        return result.list.size
    }

    @Test
    fun sunCrossingMatchesThePublicAltitude() {
        // Independent of the internal f: default h0 for the Sun is -(34' + 16') = -50'.
        for (event in events(Body.SUN, mumbai, equinox)) {
            val before = riseSet.altitudeDeg(Body.SUN, mumbai, event.at - HALF_SECOND) + 50.0 / 60.0
            val after = riseSet.altitudeDeg(Body.SUN, mumbai, event.at + HALF_SECOND) + 50.0 / 60.0
            assertTrue(before * after <= 0.0, "${event.kind}: $before -> $after")
            assertTrue((after > before) == (event.kind == RiseSetKind.RISE))
        }
        val geometric = CalcSettings(sunrise = SunriseConvention.CENTER_GEOMETRIC)
        for (event in events(Body.MOON, mumbai, equinox, geometric)) {
            val before = riseSet.altitudeDeg(Body.MOON, mumbai, event.at - HALF_SECOND)
            val after = riseSet.altitudeDeg(Body.MOON, mumbai, event.at + HALF_SECOND)
            assertTrue(before * after <= 0.0, "Moon ${event.kind}: $before -> $after")
        }
    }

    @Test
    fun refractionScalesWithPressureAndTemperature() {
        // R = 34' (P / 1010) (283 / (273 + T)): zero pressure removes refraction entirely.
        val airless = CalcSettings(sunrise = SunriseConvention.CENTER_REFRACTED, pressureHPa = 0.0)
        val geometric = CalcSettings(sunrise = SunriseConvention.CENTER_GEOMETRIC)
        assertEquals(events(Body.SUN, mumbai, equinox, geometric), events(Body.SUN, mumbai, equinox, airless))
        // Half the pressure at 10 C is 17': f differs from the geometric one by exactly that.
        val half = CalcSettings(sunrise = SunriseConvention.CENTER_REFRACTED, pressureHPa = 505.0)
        val at = equinox + 1.hours
        val base = riseSet.crossingDeg(Body.SUN, mumbai, at, geometric)
        assertEquals(17.0 / 60.0, riseSet.crossingDeg(Body.SUN, mumbai, at, half) - base, 1e-12)
        val cold = CalcSettings(sunrise = SunriseConvention.CENTER_REFRACTED, temperatureC = -10.0)
        assertEquals(34.0 / 60.0 * 283.0 / 263.0, riseSet.crossingDeg(Body.SUN, mumbai, at, cold) - base, 1e-12)
    }

    @Test
    fun moonUpperLimbUsesItsTopocentricSemidiameter() {
        val at = equinox + 5.hours
        val limb = riseSet.crossingDeg(Body.MOON, mumbai, at, defaults)
        val centreSettings = CalcSettings(sunrise = SunriseConvention.CENTER_REFRACTED)
        val centre = riseSet.crossingDeg(Body.MOON, mumbai, at, centreSettings)
        // Semidiameter asin(1737.4 km / d) lies between 14.6' (apogee) and 17.1' (perigee, Moon at the zenith).
        assertTrue((limb - centre) * 60.0 in 14.6..17.1, "Moon semidiameter ${(limb - centre) * 60.0} arcmin")
    }

    @Test
    fun moonriseComesLaterEachDay() {
        var previous: Instant? = null
        for (day in 0 until 20) {
            val start = equinox + day.days
            val result = riseSet.find(Body.MOON, mumbai, start)
            val rise = (result as? RiseSetResult.Events)?.list?.firstOrNull { it.kind == RiseSetKind.RISE }?.at
            if (rise != null && previous != null && rise - previous < 30.hours) {
                val lag = rise - previous - 24.hours
                assertTrue(lag in 20.minutes..80.minutes, "moonrise lag $lag on day $day")
            }
            if (rise != null) previous = rise
        }
    }

    @Test
    fun longerWindowsAndRepeatCallsAreConsistent() {
        val twoDays = assertIs<RiseSetResult.Events>(riseSet.find(Body.SUN, mumbai, equinox, defaults, 48)).list
        val separate = events(Body.SUN, mumbai, equinox) + events(Body.SUN, mumbai, equinox + 1.days)
        assertEquals(separate, twoDays)
        // Caches must not change results: a fresh instance and a different call order agree bit for bit.
        val fresh = RiseSet(GoldenLoader.openDe440())
        for (convention in SunriseConvention.entries.reversed()) {
            for (body in listOf(Body.MOON, Body.SUN)) {
                val settings = CalcSettings(sunrise = convention)
                assertEquals(riseSet.find(body, mumbai, equinox, settings), fresh.find(body, mumbai, equinox, settings))
                assertEquals(riseSet.find(body, tromso, equinox, settings), fresh.find(body, tromso, equinox, settings))
            }
        }
    }

    @Test
    fun rejectsBodiesOtherThanSunAndMoonAndEmptyWindows() {
        assertFailsWith<IllegalArgumentException> { riseSet.find(Body.MARS, mumbai, equinox) }
        assertFailsWith<IllegalArgumentException> { riseSet.find(Body.RAHU_MEAN, mumbai, equinox) }
        assertFailsWith<IllegalArgumentException> { riseSet.find(Body.SUN, mumbai, equinox, defaults, 0) }
        assertFailsWith<IllegalArgumentException> { Place(90.5, 0.0) }
        assertFailsWith<IllegalArgumentException> { Place(0.0, Double.NaN) }
    }

    @Test
    fun outsideTheEphemerisRangeThrows() {
        val before = Instant.parse("1899-12-25T00:00:00Z")
        val after = Instant.parse("2100-01-02T00:00:00Z")
        assertFailsWith<EphemerisRangeException> { riseSet.find(Body.SUN, mumbai, before) }
        assertFailsWith<EphemerisRangeException> { riseSet.altitudeDeg(Body.SUN, mumbai, after) }
        // The nodes have no distance: their altitude is that of a direction, and it is finite.
        assertTrue(riseSet.altitudeDeg(Body.RAHU_MEAN, mumbai, equinox) in -90.0..90.0)
    }

    /**
     * Topocentric geometry against an independent implementation: our pipeline on the Base backend against
     * Astronomy Engine's own `equator` + `horizon` (airless). Both start from the same geocentric vectors, so
     * what is compared is precession/nutation, sidereal time, the WGS84 observer and parallax. Dates in 1960
     * are outside the leap-second table, where both sides derive TT from UT with the same deltaT polynomial
     * (inside the table the two TT values differ by the 5.9 s of docs/ENGINE_DIFFS.md).
     */
    @Test
    fun altitudeAgreesWithAstronomyEngineHorizon() {
        val ours = RiseSet(AstronomyEngineEphemeris())
        val places = listOf(
            mumbai, tromso, Place(-36.8485, 174.7633), Place(40.7128, -74.0060), Place(-54.8019, -68.3030),
            Place(27.9881, 86.9250, 8848.0), Place(0.0, 0.0),
        )
        var worstSun = 0.0
        var worstMoon = 0.0
        for (place in places) {
            val observer = Observer(place.latDeg, place.lonDeg, place.elevationM)
            for (k in 0 until 60) {
                val at = Instant.parse("1960-02-01T00:00:00Z") + (k * 149).hours + (k * 7).minutes
                val time = Time(TimeScales.ut1(at).value - JulianDay.J2000)
                for ((body, aeBody) in listOf(Body.SUN to AeBody.Sun, Body.MOON to AeBody.Moon)) {
                    val equ = equator(aeBody, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
                    val theirs = horizon(time, observer, equ.ra, equ.dec, Refraction.None).altitude
                    val diff = abs(ours.altitudeDeg(body, place, at) - theirs) * 3600.0
                    if (body == Body.SUN) worstSun = maxOf(worstSun, diff) else worstMoon = maxOf(worstMoon, diff)
                }
            }
        }
        println("RiseSetTest: worst altitude difference to Astronomy Engine: Sun $worstSun, Moon $worstMoon arcsec")
        assertTrue(worstSun <= ALTITUDE_CROSS_CHECK_ARCSEC, "Sun altitude differs by $worstSun arcsec")
        assertTrue(worstMoon <= ALTITUDE_CROSS_CHECK_ARCSEC, "Moon altitude differs by $worstMoon arcsec")
    }

    private companion object {
        val HALF_SECOND: Duration = 500.milliseconds

        /**
         * A wrong flattening or elevation moves the Moon by up to 12 arcsec, a sign or frame slip by degrees;
         * the two nutation series (77 terms here, a truncated one there) differ by a few tenths at most.
         */
        const val ALTITUDE_CROSS_CHECK_ARCSEC = 1.0
    }
}
