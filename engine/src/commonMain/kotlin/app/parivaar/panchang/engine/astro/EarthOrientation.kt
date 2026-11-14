@file:Suppress("MagicNumber") // IAU 2006 / P03 polynomial coefficients

package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.astro.internal.Mat3
import app.parivaar.panchang.engine.astro.internal.Nutation2000B
import kotlin.math.PI
import kotlin.math.cos

/** Nutation in longitude and obliquity (radians), IAU 2000B. */
data class Nutation(val dpsiRad: Double, val depsRad: Double)

/**
 * Precession (IAU 2006, P03), nutation (IAU 2000B) and Earth rotation (docs/specs/M01.md section 3.3).
 * Polynomials are in arcseconds of t = Julian centuries of TT since J2000.0 and follow ERFA `obl06`, `pfw06`,
 * `era00` and `gmst06` (the formulas only; no ERFA code besides the nutation series port).
 */
object EarthOrientation {
    private const val ARCSEC_TO_RAD = Nutation2000B.ARCSEC_TO_RAD

    /** Mean obliquity of the ecliptic, IAU 2006 (epsilon_A), radians. */
    fun meanObliquityRad(tt: JulianDay): Double {
        val t = tt.centuriesSinceJ2000()
        return poly(t, 84381.406, -46.836769, -0.0001831, 0.00200340, -0.000000576, -0.0000000434) * ARCSEC_TO_RAD
    }

    /** Nutation, IAU 2000B (77 terms + fixed planetary offsets; port of ERFA `nut00b`). */
    fun nutation(tt: JulianDay): Nutation {
        val out = DoubleArray(2)
        Nutation2000B.compute(tt.centuriesSinceJ2000(), out)
        return Nutation(out[0], out[1])
    }

    /** Earth rotation angle (IAU 2000), radians in [0, 2 pi). */
    fun eraRad(ut1: JulianDay): Double {
        val d = ut1.value - JulianDay.J2000
        val f = ut1.value % 1.0
        return normalizeRad(TWO_PI * (f + 0.7790572732640 + 0.00273781191135448 * d))
    }

    /** Greenwich mean sidereal time, IAU 2006 (ERA-based), radians in [0, 2 pi). */
    fun gmstRad(ut1: JulianDay, tt: JulianDay): Double {
        val t = tt.centuriesSinceJ2000()
        val p = poly(t, 0.014506, 4612.156534, 1.3915817, -0.00000044, -0.000029956, -0.0000000368)
        return normalizeRad(eraRad(ut1) + p * ARCSEC_TO_RAD)
    }

    /**
     * Greenwich apparent sidereal time: GMST + dpsi cos(epsilon_A). The complementary terms of the equation
     * of the equinoxes (under 0.003 arcsec) are omitted (spec 3.3).
     */
    fun gastRad(ut1: JulianDay, tt: JulianDay): Double = gastRad(ut1, tt, nutation(tt).dpsiRad)

    /** [gastRad] for a nutation in longitude the caller already has (rise/set evaluates it once per instant). */
    internal fun gastRad(ut1: JulianDay, tt: JulianDay, dpsiRad: Double): Double =
        normalizeRad(gmstRad(ut1, tt) + dpsiRad * cos(meanObliquityRad(tt)))

    /** Fukushima-Williams gamma-bar (frame bias included), radians. */
    internal fun fwGammaBarRad(tt: JulianDay): Double = poly(
        tt.centuriesSinceJ2000(), -0.052928, 10.556378, 0.4932044, -0.00031238, -0.000002788, 0.0000000260,
    ) * ARCSEC_TO_RAD

    /** Fukushima-Williams phi-bar (frame bias included), radians. */
    internal fun fwPhiBarRad(tt: JulianDay): Double = poly(
        tt.centuriesSinceJ2000(), 84381.412819, -46.811016, 0.0511268, 0.00053289, -0.000000440, -0.0000000176,
    ) * ARCSEC_TO_RAD

    /** Fukushima-Williams psi-bar (frame bias included), radians. */
    internal fun fwPsiBarRad(tt: JulianDay): Double = poly(
        tt.centuriesSinceJ2000(), -0.041775, 5038.481484, 1.5584175, -0.00018522, -0.000026452, -0.0000000148,
    ) * ARCSEC_TO_RAD

    /**
     * GCRS -> ecliptic and equinox of date: R3(-(psi-bar + dpsi)) R1(phi-bar) R3(gamma-bar).
     * `dpsiRad` = nutation in longitude gives the true equinox of date; 0.0 gives the mean equinox of date.
     */
    internal fun gcrsToEclipticOfDate(tt: JulianDay, dpsiRad: Double): Mat3 =
        Mat3.r3(-(fwPsiBarRad(tt) + dpsiRad)) * Mat3.r1(fwPhiBarRad(tt)) * Mat3.r3(fwGammaBarRad(tt))

    /** Ecliptic of date -> true equator and equinox of date: R1(-(epsilon_A + deps)). */
    internal fun eclipticToTrueEquator(tt: JulianDay, depsRad: Double): Mat3 =
        Mat3.r1(-(meanObliquityRad(tt) + depsRad))
}

private const val TWO_PI = 2.0 * PI

/** c0 + c1 x + ... + c5 x^5, nested as in ERFA. */
private fun poly(x: Double, c0: Double, c1: Double, c2: Double, c3: Double, c4: Double, c5: Double): Double =
    c0 + (c1 + (c2 + (c3 + (c4 + c5 * x) * x) * x) * x) * x

/** Reduces an angle to [0, 2 pi). */
private fun normalizeRad(a: Double): Double {
    var w = a % TWO_PI
    if (w < 0.0) w += TWO_PI
    return if (w >= TWO_PI) 0.0 else w
}
