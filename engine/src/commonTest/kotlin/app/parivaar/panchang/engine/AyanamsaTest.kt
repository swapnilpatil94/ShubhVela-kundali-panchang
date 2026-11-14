package app.parivaar.panchang.engine

import app.parivaar.panchang.engine.astro.EarthOrientation
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.AyanamsaUnavailableException
import app.parivaar.panchang.engine.jyotish.Zodiac
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** docs/specs/M02.md section 4, `AyanamsaTest`. */
class AyanamsaTest {
    private val j2000 = JulianDay(2451545.0)
    private val arcsec = 1.0 / 3600.0

    private fun dms(d: Int, m: Int, s: Double): Double = d + m / 60.0 + s / 3600.0

    private fun dpsiDeg(tt: JulianDay): Double = EarthOrientation.nutation(tt).dpsiRad * 180.0 / PI

    @Test
    fun lahiriMatchesTheFourPublishedValues() {
        // Indian Astronomical Ephemeris values quoted in MASTER (spec section 4): within 0.1 arcsec.
        assertEquals(dms(24, 7, 5.986), Ayanamsa.trueDeg(AyanamsaId.LAHIRI, JulianDay(2458484.5)), 0.1 * arcsec)
        assertEquals(dms(24, 7, 54.841), Ayanamsa.trueDeg(AyanamsaId.LAHIRI, JulianDay(2458849.5)), 0.1 * arcsec)
        assertEquals(dms(23, 42, 12.34), Ayanamsa.meanDeg(AyanamsaId.LAHIRI, JulianDay(2447527.5)), 0.1 * arcsec)
        assertEquals(dms(23, 43, 2.62), Ayanamsa.meanDeg(AyanamsaId.LAHIRI, JulianDay(2447892.5)), 0.1 * arcsec)
    }

    @Test
    fun nutationAtTheLahiriEpochIsTheValueMasterQuotes() {
        assertEquals(16.79, dpsiDeg(JulianDay(2435553.5)) * 3600.0, 0.02)
    }

    @Test
    fun lahiriIsItsDefinitionAtTheEpoch() {
        // True ayanamsa 23 deg 15' 00.658" on 1956-03-21 0h TT.
        assertEquals(dms(23, 15, 0.658), Ayanamsa.trueDeg(AyanamsaId.LAHIRI, JulianDay(2435553.5)), 1e-12)
    }

    @Test
    fun trueMinusMeanIsNutationInLongitude() {
        for (id in listOf(AyanamsaId.LAHIRI, AyanamsaId.TRUE_CHITRA)) {
            for (jd in listOf(2415020.5, 2435553.5, 2451545.0, 2458849.5, 2488069.5)) {
                val tt = JulianDay(jd)
                val mean = Ayanamsa.meanDeg(id, tt)
                // Bit-equal to mean + dpsi, hence equal to dpsi within the rounding of one addition.
                assertEquals(mean + dpsiDeg(tt), Ayanamsa.trueDeg(id, tt), 0.0)
                assertEquals(dpsiDeg(tt), Ayanamsa.trueDeg(id, tt) - mean, 1e-14)
            }
        }
    }

    @Test
    fun trueChitraIsCloseToLahiriAtJ2000() {
        val chitra = Ayanamsa.meanDeg(AyanamsaId.TRUE_CHITRA, j2000)
        val lahiri = Ayanamsa.meanDeg(AyanamsaId.LAHIRI, j2000)
        val diffArcsec = (chitra - lahiri) * 3600.0
        assertTrue(diffArcsec >= -70.0 && diffArcsec <= -40.0, "difference out of the sanity band")
    }

    @Test
    fun onlyLahiriAndTrueChitraAreEnabled() {
        assertTrue(Ayanamsa.isEnabled(AyanamsaId.LAHIRI))
        assertTrue(Ayanamsa.isEnabled(AyanamsaId.TRUE_CHITRA))
        for (id in listOf(AyanamsaId.KP_NEW, AyanamsaId.RAMAN, AyanamsaId.YUKTESHWAR)) {
            assertFalse(Ayanamsa.isEnabled(id))
            assertEquals(id, assertFailsWith<AyanamsaUnavailableException> { Ayanamsa.meanDeg(id, j2000) }.id)
            assertEquals(id, assertFailsWith<AyanamsaUnavailableException> { Ayanamsa.trueDeg(id, j2000) }.id)
            val e = assertFailsWith<AyanamsaUnavailableException> { Ayanamsa.siderealDeg(100.0, id, j2000) }
            assertFalse(e.message.orEmpty().any { it.isDigit() })
        }
    }

    @Test
    fun lahiriMeanRateAtJ2000() {
        val h = 36.525 // days = 0.001 Julian century
        val perDay = (Ayanamsa.meanDeg(AyanamsaId.LAHIRI, JulianDay(j2000.value + h)) -
            Ayanamsa.meanDeg(AyanamsaId.LAHIRI, JulianDay(j2000.value - h))) / (2.0 * h)
        assertEquals(50.29, perDay * 365.25 * 3600.0, 0.01)
    }

    @Test
    fun siderealIsTropicalMinusTheTrueAyanamsa() {
        val tt = JulianDay(2460000.25)
        for (id in listOf(AyanamsaId.LAHIRI, AyanamsaId.TRUE_CHITRA)) {
            val a = Ayanamsa.trueDeg(id, tt)
            assertTrue(a > 24.0 && a < 24.4)
            for (lon in listOf(0.0, 10.0, a, 123.456, 359.999999)) {
                val s = Ayanamsa.siderealDeg(lon, id, tt)
                assertEquals(Zodiac.wrap360(lon - a), s, 0.0)
                assertTrue(s >= 0.0 && s < 360.0)
            }
            assertEquals(0.0, Ayanamsa.siderealDeg(a, id, tt), 0.0)
            assertTrue(abs(Ayanamsa.siderealDeg(0.0, id, tt) - (360.0 - a)) < 1e-12)
        }
    }

    @Test
    fun ayanamsaRateIsTheCentralDifferenceOfTheTrueValue() {
        val tt = JulianDay(2460000.25)
        for (id in listOf(AyanamsaId.LAHIRI, AyanamsaId.TRUE_CHITRA)) {
            val expected = (Ayanamsa.trueDeg(id, JulianDay(tt.value + 0.005)) -
                Ayanamsa.trueDeg(id, JulianDay(tt.value - 0.005))) / 0.01
            assertEquals(expected, Ayanamsa.trueRateDegPerDay(id, tt), 0.0)
            // Precession is about 50.3 arcsec a year; nutation adds at most a few times that for a while.
            assertTrue(abs(Ayanamsa.trueRateDegPerDay(id, tt)) < 1e-3)
        }
    }
}
