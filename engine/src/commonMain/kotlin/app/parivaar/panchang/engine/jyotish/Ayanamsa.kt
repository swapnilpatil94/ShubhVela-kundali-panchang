package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.astro.EarthOrientation
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.jyotish.internal.FixedStar
import kotlin.math.PI

/** Thrown for an [AyanamsaId] that has no constants yet (see [Ayanamsa.isEnabled]). */
class AyanamsaUnavailableException(val id: AyanamsaId) : IllegalStateException("ayanamsa $id is not available")

/**
 * Ayanamsa: the tropical longitude of the sidereal zero point (docs/specs/M02.md section 3.2).
 *
 * "Mean" is measured from the mean equinox of date, "true" from the true equinox: true = mean + nutation in
 * longitude (IAU 2000B). Apparent tropical longitudes take the true value.
 *
 * - LAHIRI: the Calendar Reform Committee definition, true ayanamsa 23 deg 15' 00.658" on 1956-03-21 0h TT,
 *   carried with the IAU 1976 general precession in longitude (Lieske et al. 1977), as the Indian Astronomical
 *   Ephemeris does. The nutation at the epoch comes from this engine's own series, not a typed number.
 * - TRUE_CHITRA: Spica at sidereal longitude 180 deg exactly, from its catalogue place and proper motion on
 *   the mean ecliptic of date. VERIFY the definition (astrometric or apparent place) and the constants.
 * - KP_NEW, RAMAN, YUKTESHWAR: no constants in M02; they throw [AyanamsaUnavailableException].
 */
object Ayanamsa {
    private const val ARCSEC_PER_DEG = 3600.0
    private const val RAD_TO_DEG = 180.0 / PI
    private const val HALF_CIRCLE_DEG = 180.0

    private const val LAHIRI_EPOCH_TT = 2435553.5
    private const val LAHIRI_TRUE_AT_EPOCH_ARCSEC = 83700.658

    // IAU 1976 general precession in longitude from J2000.0, arcsec, T in Julian centuries of TT.
    private const val PA_T1 = 5029.0966
    private const val PA_T2 = 1.11113
    private const val PA_T3 = -0.000006

    private const val RATE_STEP_DAYS = 0.005

    private val LAHIRI_EPOCH = JulianDay(LAHIRI_EPOCH_TT)

    /** Mean Lahiri ayanamsa at its epoch minus the accumulated precession there, arcsec. */
    private val LAHIRI_MEAN_OFFSET_ARCSEC: Double =
        LAHIRI_TRUE_AT_EPOCH_ARCSEC - dpsiDeg(LAHIRI_EPOCH) * ARCSEC_PER_DEG - precessionArcsec(LAHIRI_EPOCH)

    /** True for the ids that have constants: LAHIRI and TRUE_CHITRA. */
    fun isEnabled(id: AyanamsaId): Boolean = id == AyanamsaId.LAHIRI || id == AyanamsaId.TRUE_CHITRA

    /** Ayanamsa from the mean equinox of date, degrees. */
    fun meanDeg(id: AyanamsaId, tt: JulianDay): Double = when (id) {
        AyanamsaId.LAHIRI -> (LAHIRI_MEAN_OFFSET_ARCSEC + precessionArcsec(tt)) / ARCSEC_PER_DEG
        AyanamsaId.TRUE_CHITRA -> Zodiac.wrap360(FixedStar.SPICA.meanEclipticLonDeg(tt) - HALF_CIRCLE_DEG)
        AyanamsaId.KP_NEW, AyanamsaId.RAMAN, AyanamsaId.YUKTESHWAR -> throw AyanamsaUnavailableException(id)
    }

    /** Ayanamsa from the true equinox of date, degrees: [meanDeg] + nutation in longitude. */
    fun trueDeg(id: AyanamsaId, tt: JulianDay): Double = meanDeg(id, tt) + dpsiDeg(tt)

    /** Sidereal longitude of an apparent tropical longitude, degrees in [0, 360). */
    fun siderealDeg(tropicalLon: Double, id: AyanamsaId, tt: JulianDay): Double =
        Zodiac.wrap360(tropicalLon - trueDeg(id, tt))

    /**
     * Rate of [trueDeg] in degrees per day (central difference over +/- 0.005 d).
     * Sidereal speed = tropical speed - this.
     */
    internal fun trueRateDegPerDay(id: AyanamsaId, tt: JulianDay): Double =
        (trueDeg(id, JulianDay(tt.value + RATE_STEP_DAYS)) - trueDeg(id, JulianDay(tt.value - RATE_STEP_DAYS))) /
            (2.0 * RATE_STEP_DAYS)

    private fun dpsiDeg(tt: JulianDay): Double = EarthOrientation.nutation(tt).dpsiRad * RAD_TO_DEG

    private fun precessionArcsec(tt: JulianDay): Double {
        val t = (tt.value - JulianDay.J2000) / JulianDay.DAYS_PER_CENTURY
        return (PA_T1 + (PA_T2 + PA_T3 * t) * t) * t
    }
}
