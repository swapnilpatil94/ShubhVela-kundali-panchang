package app.parivaar.panchang.engine.astro.internal

import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.EarthOrientation
import app.parivaar.panchang.engine.astro.EclipticPos
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.TimeScales
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/** Per-epoch quantities shared by every body: TDB offset, nutation and the two section 3.3 rotations. */
internal class Frame(val tt: JulianDay) {
    /** TDB - TT in days (kept apart from the Julian Day so that no time resolution is lost). */
    val tdbOffsetDays: Double = TimeScales.tdbMinusTtDays(tt)
    val dpsiRad: Double
    val depsRad: Double

    /** GCRS -> mean ecliptic and equinox of date. */
    val meanEcliptic: Mat3 = EarthOrientation.gcrsToEclipticOfDate(tt, 0.0)

    /** GCRS -> true ecliptic and equinox of date. */
    val trueEcliptic: Mat3

    init {
        val n = EarthOrientation.nutation(tt)
        dpsiRad = n.dpsiRad
        depsRad = n.depsRad
        trueEcliptic = EarthOrientation.gcrsToEclipticOfDate(tt, dpsiRad)
    }
}

/** Keeps the [Frame]s of the last 4 distinct TT values (spec 3.5 step 4). Not thread-safe. */
internal class FrameCache {
    private val slots = arrayOfNulls<Frame>(SLOTS)
    private var next = 0

    fun at(tt: JulianDay): Frame {
        for (f in slots) if (f != null && f.tt.value == tt.value) return f
        val f = Frame(tt)
        slots[next] = f
        next = (next + 1) % SLOTS
        return f
    }

    private companion object {
        const val SLOTS = 4
    }
}

/**
 * Barycentric ICRF states for the apparent-place pipeline: km and km/day at the TDB instant
 * `jd + extraDays` (the Julian Day and a small offset are kept apart for resolution).
 */
internal interface BarycentricStates {
    /** SUN, MOON, MERCURY..SATURN (system barycentres for Mars, Jupiter, Saturn). */
    fun bodyPosition(body: Body, jd: Double, extraDays: Double): Vec3

    fun sunPosition(jd: Double, extraDays: Double): Vec3

    fun earthPosition(jd: Double, extraDays: Double): Vec3

    fun earthVelocity(jd: Double, extraDays: Double): Vec3
}

/** Apparent geocentric place, docs/specs/M01.md section 3.5 (own code; formulas as cited per step). */
internal object Apparent {
    /** Half-width h of the central difference for the longitude rate, days. */
    const val SPEED_STEP_DAYS = 0.005

    const val AU_KM = 149597870.7
    private const val C_KM_PER_DAY = 299792.458 * 86400.0

    /** Schwarzschild radius of the Sun, au (ERFA_SRS). */
    private const val SRS_AU = 1.97412574336e-8
    private const val LIGHT_TIME_TOLERANCE_DAYS = 1e-12
    private const val LIGHT_TIME_MAX_ITERATIONS = 10
    private const val DEFLECTION_LIMIT = 1e-6
    private const val RAD_TO_DEG = 180.0 / PI

    /**
     * Steps 1-3 for SUN, MOON, MERCURY..SATURN at the frame's TT: light-time, solar deflection (not for the
     * Sun), annual aberration, rotation to the true ecliptic and equinox of date. Speed is left at 0.0.
     */
    fun precision(states: BarycentricStates, frame: Frame, body: Body): EclipticPos {
        val jd = frame.tt.value
        val tdb = frame.tdbOffsetDays
        val earth = states.earthPosition(jd, tdb)

        // 1. Light-time: tau = |r_b(tdb - tau) - E(tdb)| / c, iterated to 1e-12 d.
        var tau = 0.0
        var bodyPos: Vec3
        var p: Vec3
        var iterations = 0
        do {
            bodyPos = states.bodyPosition(body, jd, tdb - tau)
            p = bodyPos - earth
            val next = p.norm() / C_KM_PER_DAY
            val change = abs(next - tau)
            tau = next
            iterations++
        } while (change >= LIGHT_TIME_TOLERANCE_DAYS && iterations < LIGHT_TIME_MAX_ITERATIONS)
        val distanceKm = p.norm()
        var u = p / distanceKm

        // 2. Light deflection by the Sun (Jupiter and Saturn omitted, spec 3.5).
        if (body != Body.SUN) {
            val sun = states.sunPosition(jd, tdb)
            val sunToEarth = earth - sun
            val em = sunToEarth.norm()
            u = deflect(u, (bodyPos - sun).unit(), sunToEarth / em, em / AU_KM)
        }

        // 3. Annual aberration.
        u = aberrate(u, states.earthVelocity(jd, tdb) / C_KM_PER_DAY)

        return eclipticPos(frame.trueEcliptic, u, distanceKm / AU_KM)
    }

    /**
     * Solar light deflection in the form of ERFA `ld`/`ldsun`: u + w (u x (e x q)),
     * w = SRS / em / max(q.(q + e), 1e-6 / max(em^2, 1)).
     *
     * @param u unit vector observer -> body
     * @param q unit vector Sun -> body
     * @param e unit vector Sun -> observer
     * @param emAu distance Sun -> observer, au
     */
    fun deflect(u: Vec3, q: Vec3, e: Vec3, emAu: Double): Vec3 {
        val limit = DEFLECTION_LIMIT / max(emAu * emAu, 1.0)
        val w = SRS_AU / emAu / max(q.dot(q + e), limit)
        return u + u.cross(e.cross(q)) * w
    }

    /**
     * Relativistic annual aberration: (g u + (1 + (u.b) / (1 + g)) b) / (1 + u.b), g = sqrt(1 - b^2).
     *
     * @param u unit vector observer -> body (natural direction)
     * @param beta observer barycentric velocity in units of c
     */
    fun aberrate(u: Vec3, beta: Vec3): Vec3 {
        val g = sqrt(1.0 - beta.dot(beta))
        val ub = u.dot(beta)
        return (u * g + beta * (1.0 + ub / (1.0 + g))) / (1.0 + ub)
    }

    /** Rotates a GCRS direction with [toEcliptic] and returns longitude in [0, 360) and latitude, degrees. */
    fun eclipticPos(toEcliptic: Mat3, direction: Vec3, distanceAu: Double): EclipticPos {
        val v = toEcliptic * direction
        val lon = norm360(atan2(v.y, v.x) * RAD_TO_DEG)
        val lat = atan2(v.z, sqrt(v.x * v.x + v.y * v.y)) * RAD_TO_DEG // = asin(z / |v|)
        return EclipticPos(lonDeg = lon, latDeg = lat, distanceAu = distanceAu, speedDegPerDay = 0.0)
    }

    /**
     * Step 4: fills `speedDegPerDay` = wrap180(lon(tt + h) - lon(tt - h)) / (2 h), h = 0.005 d, around the
     * position at [tt]. [noSpeed] is the backend's position function (range unchecked).
     */
    inline fun withSpeed(tt: JulianDay, noSpeed: (JulianDay) -> EclipticPos): EclipticPos {
        val at = noSpeed(tt)
        val after = noSpeed(JulianDay(tt.value + SPEED_STEP_DAYS)).lonDeg
        val before = noSpeed(JulianDay(tt.value - SPEED_STEP_DAYS)).lonDeg
        return at.copy(speedDegPerDay = wrap180(after - before) / (2.0 * SPEED_STEP_DAYS))
    }
}
