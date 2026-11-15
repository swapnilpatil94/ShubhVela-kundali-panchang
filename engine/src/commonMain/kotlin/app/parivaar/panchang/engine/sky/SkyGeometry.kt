@file:Suppress("MagicNumber") // Meeus / IAU constants, documented where used

package app.parivaar.panchang.engine.sky

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.EarthOrientation
import app.parivaar.panchang.engine.astro.EclipticPos
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.astro.centuriesSinceJ2000
import app.parivaar.panchang.engine.astro.internal.Apparent
import app.parivaar.panchang.engine.astro.internal.Vec3
import app.parivaar.panchang.engine.astro.internal.norm360
import app.parivaar.panchang.engine.astro.internal.wrap180
import app.parivaar.panchang.engine.astro.positionOnly
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Instant

/** Right ascension and declination on the true equator and equinox of date, degrees (RA in [0, 360)). */
data class RaDec(val raDeg: Double, val decDeg: Double)

/**
 * Lighting of one body for drawing it (docs/specs/M07R.md section 6, "Sky geometry"). Geocentric.
 *
 * @property elongationDeg apparent angular distance from the Sun, [0, 180]
 * @property phaseAngleDeg angle Sun-body-Earth, [0, 180] (0 = full)
 * @property illuminatedFraction k = (1 + cos i) / 2
 * @property brightLimbDeg position angle of the Sun seen from the body (= bright-limb angle chi), [0, 360),
 *   north through east on the true equator of date
 * @property eastOfSun true when the body is east of the Sun in ecliptic longitude (evening sky)
 * @property parallacticDeg parallactic angle q at the observer, (-180, 180]; null without a place
 * @property ringTiltDeg Saturnicentric latitude B of the Earth on the ring plane; null for other bodies
 */
data class BodyLight(
    val elongationDeg: Double,
    val phaseAngleDeg: Double,
    val illuminatedFraction: Double,
    val brightLimbDeg: Double,
    val eastOfSun: Boolean,
    val parallacticDeg: Double?,
    val ringTiltDeg: Double?,
) {
    /** The parallactic angle depends on the observer's place, so nothing is printed. */
    override fun toString(): String = "BodyLight(redacted)"
}

/**
 * Sky geometry for rendering planets and the Moon: additive, pure functions on the engine's apparent places
 * ([EclipticPos], true ecliptic and equinox of date). Nothing here changes an existing engine output.
 *
 * Formulas: elongation and phase angle from the geocentric vectors (the Sun-body-Earth triangle, Meeus
 * ch. 41/48); k = (1 + cos i) / 2 (Meeus 48.1); bright limb chi (Meeus 48.5); parallactic angle q (Meeus 14.1);
 * Saturn ring tilt B = latitude of the Earth over the ring plane, whose pole is Saturn's north pole of the IAU
 * WGCCRE 2009 report (docs/ENGINE_DIFFS.md M07R). The phase angle and B use geometric directions: the annual
 * aberration (Meeus 23.2 velocity, kappa = 20.49552") and the solar light deflection of the apparent places
 * are removed first, since neither changes how a body is lit.
 */
object SkyGeometry {
    /** Apparent elongation of [body] from the Sun, degrees in [0, 180]. */
    fun elongationDeg(body: EclipticPos, sun: EclipticPos): Double = angleDeg(unit(body), unit(sun))

    /** True when [body] lies east of the Sun in ecliptic longitude (it follows the Sun: evening sky). */
    fun isEastOfSun(body: EclipticPos, sun: EclipticPos): Boolean = wrap180(body.lonDeg - sun.lonDeg) > 0.0

    /**
     * Phase angle i (Sun-body-Earth), degrees in [0, 180], at TT [tt] of the two apparent places. Needs
     * geocentric distances (not for the lunar nodes).
     */
    fun phaseAngleDeg(body: EclipticPos, sun: EclipticPos, tt: JulianDay): Double {
        require(body.distanceAu > 0.0 && sun.distanceAu > 0.0) { "phase angle needs geocentric distances" }
        val beta = earthVelocityOverC(tt, sun.lonDeg)
        val s = Apparent.aberrate(unit(sun), -beta) * sun.distanceAu
        val toBody = undeflect(Apparent.aberrate(unit(body), -beta), body.distanceAu, s)
        val p = toBody * body.distanceAu
        return angleDeg(s - p, -p)
    }

    /** Illuminated fraction of the disc k = (1 + cos i) / 2 for a phase angle in degrees. */
    fun illuminatedFraction(phaseAngleDeg: Double): Double = (1.0 + cos(phaseAngleDeg * DEG)) / 2.0

    /** [pos] on the true equator and equinox of date (as rise/set: the ecliptic rotated by the true obliquity). */
    fun equatorial(pos: EclipticPos, tt: JulianDay): RaDec {
        val v = EarthOrientation.eclipticToTrueEquator(tt, EarthOrientation.nutation(tt).depsRad) * unit(pos)
        return RaDec(norm360(atan2(v.y, v.x) / DEG), atan2(v.z, sqrt(v.x * v.x + v.y * v.y)) / DEG)
    }

    /**
     * Position angle chi of the bright limb of [body] (Meeus 48.5): direction of the Sun from the body, north
     * through east, degrees in [0, 360). Ill-conditioned when the elongation is near 0 or 180 degrees.
     */
    fun brightLimbAngleDeg(body: EclipticPos, sun: EclipticPos, tt: JulianDay): Double =
        positionAngleDeg(equatorial(body, tt), equatorial(sun, tt))

    /**
     * Parallactic angle q (Meeus 14.1) of [body] at [place] and UTC [at], degrees in (-180, 180]: the position
     * angle of the zenith seen from the body, positive west of the meridian. Geocentric place of the body,
     * geodetic latitude, local apparent sidereal time (GAST + east longitude). [body] must be the apparent
     * place at the TT of [at].
     */
    fun parallacticAngleDeg(
        body: EclipticPos,
        place: Place,
        at: Instant,
        tables: TimeTables = TimeTables.DEFAULT,
    ): Double {
        val tt = TimeScales.utcToTt(at, tables)
        val ut1 = TimeScales.ut1(at, tables)
        val nutation = EarthOrientation.nutation(tt)
        val v = EarthOrientation.eclipticToTrueEquator(tt, nutation.depsRad) * unit(body)
        val ra = atan2(v.y, v.x)
        val dec = atan2(v.z, sqrt(v.x * v.x + v.y * v.y))
        val hourAngle = EarthOrientation.gastRad(ut1, tt, nutation.dpsiRad) + place.lonDeg * DEG - ra
        val phi = place.latDeg * DEG
        // tan q = sin H / (tan phi cos d - sin d cos H), multiplied through by cos phi >= 0 (defined at the poles).
        val q = atan2(cos(phi) * sin(hourAngle), sin(phi) * cos(dec) - cos(phi) * sin(dec) * cos(hourAngle))
        return half(q / DEG)
    }

    /**
     * Saturn's ring tilt B, degrees: Saturnicentric latitude of the Earth over the ring (equator) plane,
     * positive when the Earth is north of it (we see the northern face). [saturn] and [sun] are apparent places
     * at TT [tt].
     */
    fun saturnRingTiltDeg(saturn: EclipticPos, sun: EclipticPos, tt: JulianDay): Double {
        require(saturn.distanceAu > 0.0 && sun.distanceAu > 0.0) { "ring tilt needs geocentric distances" }
        val t = tt.centuriesSinceJ2000()
        val a0 = (SATURN_POLE_RA0 + SATURN_POLE_RA1 * t) * DEG
        val d0 = (SATURN_POLE_DEC0 + SATURN_POLE_DEC1 * t) * DEG
        val poleIcrf = Vec3(cos(d0) * cos(a0), cos(d0) * sin(a0), sin(d0))
        val pole = EarthOrientation.gcrsToEclipticOfDate(tt, EarthOrientation.nutation(tt).dpsiRad) * poleIcrf
        val beta = earthVelocityOverC(tt, sun.lonDeg)
        val s = Apparent.aberrate(unit(sun), -beta) * sun.distanceAu
        val toSaturn = undeflect(Apparent.aberrate(unit(saturn), -beta), saturn.distanceAu, s)
        return asin((-toSaturn.dot(pole)).coerceIn(-1.0, 1.0)) / DEG
    }

    /**
     * Everything the renderer needs for [body] (MOON, MERCURY..SATURN) at UTC [at]; the parallactic angle only
     * with a [place]. Two position evaluations (body and Sun) on [eph].
     */
    fun light(
        eph: Ephemeris,
        body: Body,
        at: Instant,
        place: Place?,
        tables: TimeTables = TimeTables.DEFAULT,
    ): BodyLight {
        require(body in LIT_BODIES) { "light needs the Moon or a planet" }
        val tt = TimeScales.utcToTt(at, tables)
        val pos = eph.positionOnly(body, tt)
        val sun = eph.positionOnly(Body.SUN, tt)
        val i = phaseAngleDeg(pos, sun, tt)
        return BodyLight(
            elongationDeg = elongationDeg(pos, sun),
            phaseAngleDeg = i,
            illuminatedFraction = illuminatedFraction(i),
            brightLimbDeg = brightLimbAngleDeg(pos, sun, tt),
            eastOfSun = isEastOfSun(pos, sun),
            parallacticDeg = place?.let { parallacticAngleDeg(pos, it, at, tables) },
            ringTiltDeg = if (body == Body.SATURN) saturnRingTiltDeg(pos, sun, tt) else null,
        )
    }

    /**
     * Earth's orbital velocity over c in the ecliptic of date (Meeus 23.2 in vector form):
     * kappa (sin L - e sin pi, -(cos L - e cos pi), 0), L = the Sun's longitude, e and pi = eccentricity and
     * perihelion longitude of the Earth's orbit.
     */
    internal fun earthVelocityOverC(tt: JulianDay, sunLonDeg: Double): Vec3 {
        val t = tt.centuriesSinceJ2000()
        val e = 0.016708634 - (0.000042037 + 0.0000001267 * t) * t
        val perihelion = (102.93735 + (1.71946 + 0.00046 * t) * t) * DEG
        val sunLon = sunLonDeg * DEG
        return Vec3(sin(sunLon) - e * sin(perihelion), -(cos(sunLon) - e * cos(perihelion)), 0.0) * KAPPA_RAD
    }

    private val LIT_BODIES = setOf(Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS, Body.JUPITER, Body.SATURN)

    /** Constant of aberration, 20.49552 arcsec (Meeus ch. 23), radians. */
    private const val KAPPA_RAD = 20.49552 / 3600.0 * DEG


    /** Saturn's north pole, ICRF, IAU WGCCRE 2009 (Archinal et al. 2011, table 2): degrees and degrees/century. */
    private const val SATURN_POLE_RA0 = 40.589
    private const val SATURN_POLE_RA1 = -0.036
    private const val SATURN_POLE_DEC0 = 83.537
    private const val SATURN_POLE_DEC1 = -0.004
}

private operator fun Vec3.unaryMinus(): Vec3 = Vec3(-x, -y, -z)

/** Removes the solar light deflection of the engine's apparent places (inverse of ERFA `ld`, first order). */
private fun undeflect(u: Vec3, distanceAu: Double, sun: Vec3): Vec3 {
    val em = sun.norm()
    val e = -sun / em
    val q = (u * distanceAu - sun).unit()
    val w = SRS_AU / em / max(q.dot(q + e), DEFLECTION_LIMIT / max(em * em, 1.0))
    return (u - u.cross(e.cross(q)) * w).unit()
}

private fun unit(p: EclipticPos): Vec3 {
    val lon = p.lonDeg * DEG
    val lat = p.latDeg * DEG
    return Vec3(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
}

/** Angle between two vectors, degrees (atan2 form, accurate near 0 and 180). */
private fun angleDeg(a: Vec3, b: Vec3): Double = atan2(a.cross(b).norm(), a.dot(b)) / DEG

/** Position angle of [to] seen from [from], north through east, degrees in [0, 360). */
private fun positionAngleDeg(from: RaDec, to: RaDec): Double {
    val d = (to.raDeg - from.raDeg) * DEG
    val d1 = from.decDeg * DEG
    val d2 = to.decDeg * DEG
    return norm360(atan2(cos(d2) * sin(d), sin(d2) * cos(d1) - cos(d2) * sin(d1) * cos(d)) / DEG)
}

/** Degrees reduced to (-180, 180]. */
private fun half(deg: Double): Double {
    val w = wrap180(deg)
    return if (w == -180.0) 180.0 else w
}

private const val DEG = PI / 180.0

/** Schwarzschild radius of the Sun in au and the deflection limiter, as the engine's apparent places (ERFA). */
private const val SRS_AU = 1.97412574336e-8
private const val DEFLECTION_LIMIT = 1e-6
