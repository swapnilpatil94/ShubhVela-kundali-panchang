package app.parivaar.panchang.engine

import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.AyanamsaUnavailableException
import app.parivaar.panchang.engine.jyotish.HouseAngles
import app.parivaar.panchang.engine.jyotish.HouseError
import app.parivaar.panchang.engine.jyotish.HouseResult
import app.parivaar.panchang.engine.jyotish.HouseSystem
import app.parivaar.panchang.engine.jyotish.Houses
import app.parivaar.panchang.engine.jyotish.Zodiac
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** docs/specs/M02.md section 4, `HousesTest`. */
class HousesTest {
    private val eps = 23.4392911
    private val rad = PI / 180.0

    private fun cusps(theta: Double, phi: Double, system: HouseSystem, epsDeg: Double = eps): HouseResult.Cusps =
        assertIs<HouseResult.Cusps>(Houses.tropical(HouseAngles(Zodiac.wrap360(theta), epsDeg), phi, system))

    private fun assertAngle(expected: Double, actual: Double, tol: Double = 1e-9) {
        assertTrue(actual >= 0.0 && actual < 360.0, "angle outside [0, 360)")
        assertTrue(abs(Zodiac.wrap180(actual - expected)) <= tol, "angle differs by more than the tolerance")
    }

    @Test
    fun cardinalPointsOnTheEquator() {
        for (system in HouseSystem.entries) {
            assertAngle(90.0, cusps(0.0, 0.0, system).ascDeg)
            assertAngle(0.0, cusps(0.0, 0.0, system).mcDeg)
            assertAngle(180.0, cusps(90.0, 0.0, system).ascDeg)
            assertAngle(90.0, cusps(90.0, 0.0, system).mcDeg)
            assertAngle(0.0, cusps(270.0, 0.0, system).ascDeg)
            assertAngle(270.0, cusps(270.0, 0.0, system).mcDeg)
        }
    }

    @Test
    fun ascendantOfTheAntipodeIsOpposite() {
        val rng = Random(20261114)
        repeat(100) {
            val theta = rng.nextDouble() * 360.0
            val phi = -89.0 + rng.nextDouble() * 178.0
            val here = cusps(theta, phi, HouseSystem.EQUAL).ascDeg
            val there = cusps(theta + 180.0, -phi, HouseSystem.EQUAL).ascDeg
            assertAngle(here + 180.0, there)
        }
    }

    @Test
    fun ascendantIsOnTheHorizonAndEastOfTheMeridian() {
        val rng = Random(7)
        repeat(500) {
            val theta = rng.nextDouble() * 360.0
            val phi = -89.9 + rng.nextDouble() * 179.8
            val lam = cusps(theta, phi, HouseSystem.EQUAL).ascDeg * rad
            val alpha = atan2(sin(lam) * cos(eps * rad), cos(lam))
            val delta = asin(sin(eps * rad) * sin(lam))
            val h = theta * rad - alpha
            val sinAlt = sin(phi * rad) * sin(delta) + cos(phi * rad) * cos(delta) * cos(h)
            assertTrue(abs(sinAlt) < 1e-12, "the ascendant is not on the horizon")
            assertTrue(sin(h) <= 1e-12, "the ascendant is west of the meridian")
        }
    }

    @Test
    fun polesAreFinite() {
        for (phi in listOf(90.0, -90.0)) {
            var theta = 0.0
            while (theta < 360.0) {
                for (system in listOf(HouseSystem.WHOLE_SIGN, HouseSystem.EQUAL, HouseSystem.SRIPATI)) {
                    val c = cusps(theta, phi, system)
                    for (x in c.cuspsDeg + c.startsDeg + c.ascDeg + c.mcDeg) assertTrue(x >= 0.0 && x < 360.0)
                }
                val placidus = Houses.tropical(HouseAngles(theta, eps), phi, HouseSystem.PLACIDUS)
                assertEquals(HouseResult.Unavailable(HouseError.PLACIDUS_HIGH_LATITUDE), placidus)
                theta += 7.5
            }
        }
    }

    @Test
    fun wholeSignStartsAtTheSignOfTheAscendant() {
        val c = cusps(200.0, 28.6, HouseSystem.WHOLE_SIGN)
        val first = 30.0 * floor(c.ascDeg / 30.0)
        assertEquals(List(12) { Zodiac.wrap360(first + 30.0 * it) }, c.startsDeg)
        assertEquals(c.startsDeg, c.cuspsDeg)
        assertEquals(HouseSystem.WHOLE_SIGN, c.system)
    }

    @Test
    fun equalHousesTakeTheAscendantAsTheMiddleOfTheFirstHouse() {
        val c = cusps(200.0, 28.6, HouseSystem.EQUAL)
        for (i in 0 until 12) {
            assertAngle(c.ascDeg + 30.0 * i, c.cuspsDeg[i], 1e-12)
            assertAngle(c.ascDeg + 30.0 * i - 15.0, c.startsDeg[i], 1e-12)
        }
        assertEquals(c.ascDeg, c.cuspsDeg[0])
    }

    @Test
    fun sripatiOnTheEquatorAtZeroSiderealTimeIsEverySignStart() {
        val c = cusps(0.0, 0.0, HouseSystem.SRIPATI)
        // c10 = mc = 0, c11 = 30, c12 = 60, c1 = asc = 90, ...
        for (house in 1..12) assertAngle(30.0 * ((house + 2) % 12), c.cuspsDeg[house - 1])
        for (house in 1..12) assertAngle(30.0 * ((house + 2) % 12) - 15.0, c.startsDeg[house - 1])
    }

    @Test
    fun sripatiTrisectsTheQuadrants() {
        val c = cusps(123.0, 51.5, HouseSystem.SRIPATI)
        assertEquals(c.ascDeg, c.cuspsDeg[0])
        assertEquals(c.mcDeg, c.cuspsDeg[9])
        val a = Zodiac.wrap360(c.ascDeg - c.mcDeg)
        assertAngle(c.mcDeg + a / 3.0, c.cuspsDeg[10])
        assertAngle(c.mcDeg + 2.0 * a / 3.0, c.cuspsDeg[11])
        assertAngle(c.ascDeg + (180.0 - a) / 3.0, c.cuspsDeg[1])
        assertAngle(c.ascDeg + 2.0 * (180.0 - a) / 3.0, c.cuspsDeg[2])
        for (i in 0 until 6) assertAngle(c.cuspsDeg[i] + 180.0, c.cuspsDeg[i + 6])
        for (i in 0 until 12) {
            val prev = c.cuspsDeg[(i + 11) % 12]
            assertAngle(prev + Zodiac.wrap360(c.cuspsDeg[i] - prev) / 2.0, c.startsDeg[i])
        }
    }

    @Test
    fun placidusOnTheEquatorIsTheRightAscensionTrisection() {
        var theta = 0.0
        while (theta < 360.0) {
            val c = cusps(theta, 0.0, HouseSystem.PLACIDUS)
            val offsets = mapOf(11 to 30.0, 12 to 60.0, 2 to 120.0, 3 to 150.0)
            for ((house, offset) in offsets) {
                val a = (theta + offset) * rad
                assertAngle(atan2(sin(a), cos(a) * cos(eps * rad)) / rad, c.cuspsDeg[house - 1])
            }
            theta += 10.0
        }
    }

    @Test
    fun placidusCuspsSatisfyTheSemiArcDefinition() {
        for (phi in listOf(-66.0, -45.0, -10.0, 19.0, 28.6, 51.5, 60.0, 66.0)) {
            var theta = 0.25
            while (theta < 360.0) {
                val c = cusps(theta, phi, HouseSystem.PLACIDUS)
                assertEquals(c.cuspsDeg, c.startsDeg)
                assertEquals(c.ascDeg, c.cuspsDeg[0])
                assertEquals(c.mcDeg, c.cuspsDeg[9])
                for (house in listOf(11, 12, 2, 3)) {
                    val lam = c.cuspsDeg[house - 1] * rad
                    val alpha = atan2(sin(lam) * cos(eps * rad), cos(lam)) / rad
                    val delta = asin(sin(eps * rad) * sin(lam))
                    val d = acos(-tan(phi * rad) * tan(delta)) / rad
                    val n = 180.0 - d
                    val expected = when (house) {
                        11 -> theta + d / 3.0
                        12 -> theta + 2.0 * d / 3.0
                        2 -> theta + 180.0 - 2.0 * n / 3.0
                        else -> theta + 180.0 - n / 3.0
                    }
                    assertTrue(abs(Zodiac.wrap180(alpha - expected)) < 1e-9, "semi-arc rule not met")
                }
                for (i in 0 until 6) assertAngle(c.cuspsDeg[i] + 180.0, c.cuspsDeg[i + 6], 1e-12)
                theta += 3.5
            }
        }
    }

    @Test
    fun placidusStopsAboveSixtySixDegrees() {
        for (phi in listOf(66.0, -66.0)) {
            var theta = 0.0
            while (theta < 360.0) {
                assertIs<HouseResult.Cusps>(Houses.tropical(HouseAngles(theta, 23.45), phi, HouseSystem.PLACIDUS))
                theta += 1.0
            }
        }
        for (phi in listOf(66.0001, -66.0001, 70.0, -89.0)) {
            assertEquals(
                HouseResult.Unavailable(HouseError.PLACIDUS_HIGH_LATITUDE),
                Houses.tropical(HouseAngles(10.0, eps), phi, HouseSystem.PLACIDUS),
            )
        }
    }

    @Test
    fun everyCuspLiesInItsOwnHouse() {
        for (system in HouseSystem.entries) {
            for (phi in listOf(-66.0, -40.0, 0.0, 13.1, 28.6, 51.5, 66.0)) {
                var theta = 0.5
                while (theta < 360.0) {
                    val c = cusps(theta, phi, system)
                    for (i in 0 until 12) {
                        assertEquals(i + 1, c.houseOf(c.cuspsDeg[i]))
                        assertEquals(i + 1, c.houseOf(c.startsDeg[i]))
                    }
                    assertEquals(1, c.houseOf(c.ascDeg))
                    var lon = 0.0
                    while (lon < 360.0) {
                        assertTrue(c.houseOf(lon) in 1..12)
                        lon += 1.7
                    }
                    theta += 4.0
                }
            }
        }
    }

    @Test
    fun latitudeMustBeARealLatitude() {
        for (bad in listOf(90.0001, -91.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val e = assertFailsWith<IllegalArgumentException> {
                Houses.tropical(HouseAngles(10.0, eps), bad, HouseSystem.EQUAL)
            }
            assertFalse(e.message.orEmpty().any { it.isDigit() })
        }
    }

    @Test
    fun anglesAtJ2000OnTheGreenwichMeridian() {
        val at = Instant.parse("2000-01-01T12:00:00Z")
        val a = Houses.angles(at, Place(0.0, 0.0))
        assertEquals(280.4606, a.lastDeg, 0.01) // GMST 18h 41m 50.5s, equation of the equinoxes under 0.005 deg
        assertEquals(23.4377, a.epsTrueDeg, 0.001) // mean obliquity 23.4393 deg, nutation about -5.8 arcsec
        val east = Houses.angles(at, Place(10.0, 90.0))
        assertAngle(a.lastDeg + 90.0, east.lastDeg, 1e-12)
        assertEquals(a.epsTrueDeg, east.epsTrueDeg)
        assertFalse(a.toString().any { it.isDigit() })
    }

    @Test
    fun siderealIsTropicalMinusTheTrueAyanamsa() {
        val at = Instant.parse("1987-06-15T03:20:00Z")
        val place = Place(51.5074, -0.1278)
        val tt = TimeScales.utcToTt(at)
        for (id in listOf(AyanamsaId.LAHIRI, AyanamsaId.TRUE_CHITRA)) {
            val a = Ayanamsa.trueDeg(id, tt)
            for (system in HouseSystem.entries) {
                val trop = assertIs<HouseResult.Cusps>(Houses.tropical(Houses.angles(at, place), place.latDeg, system))
                val settings = CalcSettings(ayanamsa = id, houseSystem = system)
                val sid = assertIs<HouseResult.Cusps>(Houses.sidereal(at, place, settings))
                assertEquals(system, sid.system)
                assertEquals(Zodiac.wrap360(trop.ascDeg - a), sid.ascDeg)
                assertEquals(Zodiac.wrap360(trop.mcDeg - a), sid.mcDeg)
                if (system == HouseSystem.WHOLE_SIGN) {
                    val first = 30.0 * floor(sid.ascDeg / 30.0)
                    assertEquals(List(12) { Zodiac.wrap360(first + 30.0 * it) }, sid.startsDeg)
                    assertEquals(sid.startsDeg, sid.cuspsDeg)
                } else {
                    assertEquals(trop.cuspsDeg.map { Zodiac.wrap360(it - a) }, sid.cuspsDeg)
                    assertEquals(trop.startsDeg.map { Zodiac.wrap360(it - a) }, sid.startsDeg)
                }
            }
        }
    }

    @Test
    fun siderealPassesUnavailableThroughAndRejectsDisabledAyanamsas() {
        val at = Instant.parse("1987-06-15T03:20:00Z")
        val tromso = Place(69.6492, 18.9553)
        assertEquals(
            HouseResult.Unavailable(HouseError.PLACIDUS_HIGH_LATITUDE),
            Houses.sidereal(at, tromso, CalcSettings(houseSystem = HouseSystem.PLACIDUS)),
        )
        assertIs<HouseResult.Cusps>(Houses.sidereal(at, tromso, CalcSettings(houseSystem = HouseSystem.SRIPATI)))
        assertFailsWith<AyanamsaUnavailableException> {
            Houses.sidereal(at, tromso, CalcSettings(ayanamsa = AyanamsaId.RAMAN))
        }
    }
}
