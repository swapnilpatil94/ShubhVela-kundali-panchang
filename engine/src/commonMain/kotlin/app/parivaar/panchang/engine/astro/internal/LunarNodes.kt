@file:Suppress("MagicNumber") // IERS 2003 mean-node polynomial

package app.parivaar.panchang.engine.astro.internal

import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.EclipticPos
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.centuriesSinceJ2000
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Lunar nodes, docs/specs/M01.md section 3.6 (shared by both backends). Latitude 0, distance 0.0;
 * KETU = RAHU + 180 deg. Nutation in longitude is added to both node kinds.
 * VERIFY with owner/pandit: both definitions (spec TODO).
 */
internal object LunarNodes {
    private const val RAD_TO_DEG = 180.0 / PI
    private const val ARCSEC_PER_DEG = 3600.0

    /**
     * Mean longitude of the Moon's ascending node on the mean equinox of date, degrees (not reduced):
     * Omega = 125.04455501 deg - (6962890.5431 t - 7.4722 t^2 - 0.007702 t^3 + 0.00005939 t^4) arcsec.
     */
    fun meanAscendingNodeDeg(tt: JulianDay): Double {
        val t = tt.centuriesSinceJ2000()
        val arcsec = (6962890.5431 + (-7.4722 + (-0.007702 + 0.00005939 * t) * t) * t) * t
        return 125.04455501 - arcsec / ARCSEC_PER_DEG
    }

    /**
     * Osculating ascending node on the mean equinox of date, degrees: geometric geocentric Moon state (r, v)
     * in the GCRS/J2000 equatorial frame, both rotated by [meanEcliptic] (no frame-rate term), h = r x v,
     * longitude = atan2(h_x, -h_y).
     */
    fun osculatingAscendingNodeDeg(meanEcliptic: Mat3, r: Vec3, v: Vec3): Double {
        val h = (meanEcliptic * r).cross(meanEcliptic * v)
        return atan2(h.x, -h.y) * RAD_TO_DEG
    }

    /** RAHU_* for KETU_*, null for every other body. Backends compute Ketu from Rahu so both share one speed. */
    fun ascendingNodeOf(body: Body): Body? = when (body) {
        Body.KETU_MEAN -> Body.RAHU_MEAN
        Body.KETU_TRUE -> Body.RAHU_TRUE
        else -> null
    }

    /** The descending node (Ketu) for an ascending-node (Rahu) position: longitude + 180 deg, same speed. */
    fun opposite(ascending: EclipticPos): EclipticPos =
        ascending.copy(lonDeg = norm360(ascending.lonDeg + HALF_TURN_DEG))

    /**
     * Position of an ascending node, RAHU_MEAN or RAHU_TRUE (speed left at 0.0). [moonGeocentricState]
     * supplies the geometric geocentric Moon (r, v) at the frame's instant; it is only called for RAHU_TRUE.
     */
    fun position(body: Body, frame: Frame, moonGeocentricState: () -> Pair<Vec3, Vec3>): EclipticPos {
        val ascending = when (body) {
            Body.RAHU_MEAN -> meanAscendingNodeDeg(frame.tt)
            Body.RAHU_TRUE -> {
                val (r, v) = moonGeocentricState()
                osculatingAscendingNodeDeg(frame.meanEcliptic, r, v)
            }
            else -> throw IllegalArgumentException("$body is not an ascending lunar node")
        }
        val lon = ascending + frame.dpsiRad * RAD_TO_DEG
        return EclipticPos(lonDeg = norm360(lon), latDeg = 0.0, distanceAu = 0.0, speedDegPerDay = 0.0)
    }
}
