package app.parivaar.panchang.engine.jyotish.internal

import app.parivaar.panchang.engine.astro.EarthOrientation
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.internal.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A star by its ICRS catalogue place at epoch J2000.0 and its proper motion, carried linearly in space
 * (docs/specs/M02.md section 3.2). No parallax, radial velocity, aberration or light deflection.
 *
 * @param raDeg right ascension at J2000.0, degrees
 * @param decDeg declination at J2000.0, degrees
 * @param pmRaCosDecMasPerYear proper motion in right ascension times cos(declination), mas per Julian year
 * @param pmDecMasPerYear proper motion in declination, mas per Julian year
 */
internal class FixedStar(raDeg: Double, decDeg: Double, pmRaCosDecMasPerYear: Double, pmDecMasPerYear: Double) {
    private val p0: Vec3
    private val motionPerYear: Vec3

    init {
        val ra = raDeg * DEG_TO_RAD
        val dec = decDeg * DEG_TO_RAD
        p0 = Vec3(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec))
        val eRa = Vec3(-sin(ra), cos(ra), 0.0)
        val eDec = Vec3(-sin(dec) * cos(ra), -sin(dec) * sin(ra), cos(dec))
        motionPerYear = eRa * (pmRaCosDecMasPerYear * MAS_TO_RAD) + eDec * (pmDecMasPerYear * MAS_TO_RAD)
    }

    /** Longitude on the mean ecliptic and mean equinox of date, degrees in (-180, 180]. */
    fun meanEclipticLonDeg(tt: JulianDay): Double {
        val years = (tt.value - JulianDay.J2000) / DAYS_PER_JULIAN_YEAR
        val p = (p0 + motionPerYear * years).unit()
        val v = EarthOrientation.gcrsToEclipticOfDate(tt, dpsiRad = 0.0) * p
        return atan2(v.y, v.x) / DEG_TO_RAD
    }

    companion object {
        private const val DEG_TO_RAD = PI / 180.0
        private const val MAS_TO_RAD = DEG_TO_RAD / 3_600_000.0
        private const val DAYS_PER_JULIAN_YEAR = 365.25

        // Spica (alpha Virginis, HIP 65474), Hipparcos new reduction (van Leeuwen 2007), ICRS, epoch J2000.0.
        // TODO(owner): typed without catalogue access; confirm against the catalogue (docs/specs/M02.md).
        private const val SPICA_RA_DEG = 201.2982474
        private const val SPICA_DEC_DEG = -11.1613195
        private const val SPICA_PM_RA_COS_DEC_MAS = -42.35
        private const val SPICA_PM_DEC_MAS = -30.67

        /** Spica, the reference star of the TRUE_CHITRA ayanamsa. */
        val SPICA: FixedStar = FixedStar(SPICA_RA_DEG, SPICA_DEC_DEG, SPICA_PM_RA_COS_DEC_MAS, SPICA_PM_DEC_MAS)
    }
}
