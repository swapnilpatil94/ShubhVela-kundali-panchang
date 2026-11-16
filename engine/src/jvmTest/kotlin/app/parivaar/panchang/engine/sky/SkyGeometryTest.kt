package app.parivaar.panchang.engine.sky

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.EarthOrientation
import app.parivaar.panchang.engine.astro.EclipticPos
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.TimeScales
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** M07R B5 contract tests of [SkyGeometry] (the accuracy gates are in [SkyGeometryGoldenTest]). */
class SkyGeometryTest {
    private val deg = PI / 180.0

    @Test
    fun illuminatedFractionEndpoints() {
        assertEquals(1.0, SkyGeometry.illuminatedFraction(0.0), 1e-15)
        assertEquals(0.5, SkyGeometry.illuminatedFraction(90.0), 1e-15)
        assertEquals(0.0, SkyGeometry.illuminatedFraction(180.0), 1e-15)
    }

    /** Fable review: literal book values next to the polynomial comparison below. */
    @Test
    fun meeusWorkedExamplesLiterally() {
        val eph = GoldenLoader.openDe440()
        // Meeus example 45.a, 1992 December 16 0h TD: B = 16.442 degrees.
        val tt45 = JulianDay(2448972.5)
        val b = SkyGeometry.saturnRingTiltDeg(eph.geoApparent(Body.SATURN, tt45), eph.geoApparent(Body.SUN, tt45), tt45)
        assertEquals(16.442, b, 0.01, "Meeus 45.a B")
        // Meeus example 48.a, Moon 1992 April 12 0h TD: k = 0.6786, chi = 285.0 degrees.
        val tt48 = JulianDay(2448724.5)
        val moon = eph.geoApparent(Body.MOON, tt48)
        val sun = eph.geoApparent(Body.SUN, tt48)
        val k = SkyGeometry.illuminatedFraction(SkyGeometry.phaseAngleDeg(moon, sun, tt48))
        assertEquals(0.6786, k, 0.0005, "Meeus 48.a k")
        assertEquals(285.0, SkyGeometry.brightLimbAngleDeg(moon, sun, tt48), 0.1, "Meeus 48.a chi")
    }

    @Test
    fun ringTiltAgreesWithMeeusChapter45Polynomials() {
        // Meeus 45: sin B = sin i cos b sin(l - Om) - cos i sin b, i and Om of the ring plane on the ecliptic and
        // mean equinox of date (constants as in docs/specs/M07R.md). The engine uses the IAU 2009 pole, ~90" from
        // the pole these polynomials imply (docs/ENGINE_DIFFS.md M07R), so the two agree to 0.04 degree.
        val eph = GoldenLoader.openDe440()
        var worst = 0.0
        var day = 2415100.5
        while (day < 2488000.0) {
            val tt = JulianDay(day)
            val sat = eph.geoApparent(Body.SATURN, tt)
            val sun = eph.geoApparent(Body.SUN, tt)
            val t = (day - JulianDay.J2000) / JulianDay.DAYS_PER_CENTURY
            val inc = (28.075216 - 0.012998 * t + 0.000004 * t * t) * deg
            val node = (169.508470 + 1.394681 * t + 0.000412 * t * t) * deg
            val lon = sat.lonDeg * deg - EarthOrientation.nutation(tt).dpsiRad
            val lat = sat.latDeg * deg
            val meeus = asin(sin(inc) * cos(lat) * sin(lon - node) - cos(inc) * sin(lat)) / deg
            worst = maxOf(worst, abs(SkyGeometry.saturnRingTiltDeg(sat, sun, tt) - meeus))
            day += 97.3
        }
        assertTrue(worst <= 0.04, "ring tilt vs Meeus 45: $worst deg")
        assertTrue(worst >= 0.005, "IAU 2009 pole expected to differ from the Meeus polynomials: $worst deg")
    }

    @Test
    fun brightLimbFacesTheSun() {
        // East of the Sun (evening) -> the Sun lies to the west of the body: chi in (180, 360); west -> (0, 180).
        val eph = AstronomyEngineEphemeris()
        var at = Instant.parse("2026-01-01T00:00:00Z")
        repeat(400) {
            val tt = TimeScales.utcToTt(at)
            for (body in listOf(Body.MOON, Body.VENUS, Body.JUPITER)) {
                val pos = eph.geoApparent(body, tt)
                val sun = eph.geoApparent(Body.SUN, tt)
                val psi = SkyGeometry.elongationDeg(pos, sun)
                if (psi in 30.0..150.0) {
                    val chi = SkyGeometry.brightLimbAngleDeg(pos, sun, tt)
                    val east = SkyGeometry.isEastOfSun(pos, sun)
                    assertEquals(east, chi > 180.0, "chi $chi east $east")
                }
            }
            at += 1.days
        }
    }

    @Test
    fun parallacticAngleSignAndPole() {
        val eph = AstronomyEngineEphemeris()
        val pune = Place(18.5204, 73.8567)
        val pole = Place(90.0, 0.0)
        var at = Instant.parse("2026-11-15T00:00:00Z")
        repeat(96) {
            val tt = TimeScales.utcToTt(at)
            val moon = eph.geoApparent(Body.MOON, tt)
            assertEquals(0.0, SkyGeometry.parallacticAngleDeg(moon, pole, at), 1e-9)
            val q = SkyGeometry.parallacticAngleDeg(moon, pune, at)
            assertTrue(q > -180.0 && q <= 180.0)
            // Hour angle from the same conversion: q has the sign of sin H (west of the meridian positive).
            val ra = SkyGeometry.equatorial(moon, tt).raDeg
            val h = EarthOrientation.gastRad(TimeScales.ut1(at), tt) / deg + pune.lonDeg - ra
            val sinH = sin(h * deg)
            if (abs(sinH) > 0.05) assertEquals(sinH > 0.0, q > 0.0, "q $q sinH $sinH")
            at += kotlin.time.Duration.parse("15m")
        }
    }

    @Test
    fun lightBundleIsConsistentAndRedacted() {
        val eph = AstronomyEngineEphemeris()
        val at = Instant.parse("2026-11-15T14:00:00Z")
        val place = Place(18.5204, 73.8567)
        val moon = SkyGeometry.light(eph, Body.MOON, at, place)
        assertEquals(SkyGeometry.illuminatedFraction(moon.phaseAngleDeg), moon.illuminatedFraction, 0.0)
        assertTrue(moon.parallacticDeg != null)
        assertNull(moon.ringTiltDeg)
        assertEquals("BodyLight(redacted)", moon.toString())
        assertFalse(moon.toString().contains("18.52"))
        val saturn = SkyGeometry.light(eph, Body.SATURN, at, null)
        assertNull(saturn.parallacticDeg)
        assertTrue(saturn.ringTiltDeg != null)
        assertTrue(saturn.phaseAngleDeg in 0.0..7.0)
    }

    @Test
    fun rejectsBodiesWithoutAPhaseWithStaticMessages() {
        val eph = AstronomyEngineEphemeris()
        val at = Instant.parse("2026-11-15T00:00:00Z")
        for (body in listOf(Body.SUN, Body.RAHU_MEAN, Body.KETU_TRUE)) {
            val e = assertFailsWith<IllegalArgumentException> { SkyGeometry.light(eph, body, at, null) }
            assertEquals("light needs the Moon or a planet", e.message)
        }
        val node = EclipticPos(10.0, 0.0, 0.0, 0.0)
        val sun = EclipticPos(100.0, 0.0, 0.98, 1.0)
        val tt = JulianDay(2461360.5)
        assertEquals(
            "phase angle needs geocentric distances",
            assertFailsWith<IllegalArgumentException> { SkyGeometry.phaseAngleDeg(node, sun, tt) }.message,
        )
        assertEquals(
            "ring tilt needs geocentric distances",
            assertFailsWith<IllegalArgumentException> { SkyGeometry.saturnRingTiltDeg(node, sun, tt) }.message,
        )
    }
}
