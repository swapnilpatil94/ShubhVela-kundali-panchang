package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.astro.internal.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/specs/M01.md section 4, `EarthOrientationTest`. Expected values are the ERFA `t_erfa_c.c` vectors quoted
 * in the spec (eraObl06, eraPfw06, eraNut00b, eraEra00, eraGmst06).
 */
class EarthOrientationTest {
    @Test
    fun meanObliquityIau2006() {
        assertEquals(0.4090749229387258204, EarthOrientation.meanObliquityRad(JulianDay(2454388.5)), 1e-14)
    }

    @Test
    fun fukushimaWilliamsAngles() {
        val tt = JulianDay(2450124.4999)
        assertEquals(-0.2243387670997995690e-5, EarthOrientation.fwGammaBarRad(tt), 1e-12)
        assertEquals(0.4091014602391312808, EarthOrientation.fwPhiBarRad(tt), 1e-12)
        assertEquals(-0.9501954178013031895e-3, EarthOrientation.fwPsiBarRad(tt), 1e-12)
    }

    @Test
    fun nutationIau2000B() {
        val n = EarthOrientation.nutation(JulianDay(2453736.5))
        assertEquals(-0.9632552291148362783e-5, n.dpsiRad, 1e-13)
        assertEquals(0.4063197106621159367e-4, n.depsRad, 1e-13)
    }

    @Test
    fun earthRotationAngle() {
        assertEquals(0.4022837240028158102, EarthOrientation.eraRad(JulianDay(2454388.5)), 1e-12)
    }

    @Test
    fun greenwichMeanSiderealTime() {
        val jd = JulianDay(2453736.5)
        assertEquals(1.754174971870091203, EarthOrientation.gmstRad(jd, jd), 1e-12)
    }

    @Test
    fun apparentSiderealTimeAddsEquationOfEquinoxes() {
        val jd = JulianDay(2453736.5)
        val ee = EarthOrientation.nutation(jd).dpsiRad * cos(EarthOrientation.meanObliquityRad(jd))
        var d = EarthOrientation.gastRad(jd, jd) - EarthOrientation.gmstRad(jd, jd) - ee
        if (d > PI) d -= 2 * PI
        if (d < -PI) d += 2 * PI
        assertEquals(0.0, d, 1e-14)
        assertTrue(EarthOrientation.gastRad(jd, jd) in 0.0..(2 * PI))
    }

    @Test
    fun eclipticRotationIsOrthonormalAndPutsThePoleOfDateOnZ() {
        val tt = JulianDay(2460000.5)
        val n = EarthOrientation.nutation(tt)
        val m = EarthOrientation.gcrsToEclipticOfDate(tt, n.dpsiRad)
        val ex = m * Vec3(1.0, 0.0, 0.0)
        val ey = m * Vec3(0.0, 1.0, 0.0)
        val ez = m * Vec3(0.0, 0.0, 1.0)
        for (v in listOf(ex, ey, ez)) assertEquals(1.0, v.norm(), 1e-14)
        assertEquals(0.0, ex.dot(ey), 1e-14)
        assertEquals(0.0, ex.dot(ez), 1e-14)
        assertEquals(0.0, ey.dot(ez), 1e-14)
        // phi-bar is the angle between the GCRS pole and the ecliptic pole of date, whatever psi and gamma are.
        assertTrue(abs(ez.z - cos(EarthOrientation.fwPhiBarRad(tt))) < 1e-14)
        // Nutation in longitude is a pure rotation about the ecliptic pole of date.
        val mean = EarthOrientation.gcrsToEclipticOfDate(tt, 0.0)
        val p = Vec3(0.3, -0.5, 0.8)
        val a = mean * p
        val b = m * p
        assertEquals(a.z, b.z, 1e-14)
        var dLon = kotlin.math.atan2(b.y, b.x) - kotlin.math.atan2(a.y, a.x)
        if (dLon > PI) dLon -= 2 * PI
        if (dLon < -PI) dLon += 2 * PI
        assertEquals(n.dpsiRad, dLon, 1e-14)
    }
}
