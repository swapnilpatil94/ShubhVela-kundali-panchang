package app.parivaar.panchang.engine.calendar.internal

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.RiseSet
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.astro.internal.Brent
import app.parivaar.panchang.engine.astro.internal.Vec3
import app.parivaar.panchang.engine.astro.positionOnly
import app.parivaar.panchang.engine.calendar.TimeRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/**
 * Shadow and disc geometry of eclipses (docs/specs/M04.md section 3.9), all angles in radians.
 *
 * Geocentric quantities come from the apparent ecliptic places of the Sun and Moon; local solar ones from
 * [RiseSet.topocentric]. Spherical Earth for the shadow, 1/85 atmospheric enlargement of the Moon's
 * parallax. Holds a [RiseSet] (sample caches): not thread-safe.
 */
internal class ShadowGeometry(private val eph: Ephemeris, private val time: TimeTables) {
    val riseSet = RiseSet(eph, time)

    /** Geocentric apparent Moon and Sun: unit vectors (ecliptic of date) and distances in km. */
    class Geo(val moon: Vec3, val sun: Vec3, val moonKm: Double, val sunKm: Double)

    /** Lunar shadow at one instant: gamma = angle(Moon, antisolar point), umbra and penumbra radii, s_M. */
    class Lunar(val gamma: Double, val rhoU: Double, val rhoP: Double, val sM: Double)

    /** Geocentric solar eclipse quantities: gamma = angle(Moon, Sun) and the eclipse limit. */
    class SolarGlobal(val gamma: Double, val limit: Double)

    /** Topocentric discs: sigma = angle between centres, apparent semidiameters of Sun and Moon. */
    class Discs(val sigma: Double, val sS: Double, val sM: Double)

    fun geo(at: Instant): Geo {
        val tt = TimeScales.utcToTt(at, time)
        val m = eph.positionOnly(Body.MOON, tt)
        val s = eph.positionOnly(Body.SUN, tt)
        return Geo(unit(m.lonDeg, m.latDeg), unit(s.lonDeg, s.latDeg), m.distanceAu * AU_KM, s.distanceAu * AU_KM)
    }

    /** Geocentric apparent ecliptic latitude of the Moon, degrees (the syzygy prefilter). */
    fun moonLatDeg(at: Instant): Double = eph.positionOnly(Body.MOON, TimeScales.utcToTt(at, time)).latDeg

    fun lunar(at: Instant): Lunar {
        val g = geo(at)
        val piM = asin(R_EARTH_KM / g.moonKm)
        val piS = asin(R_EARTH_KM / g.sunKm)
        val sS = asin(R_SUN_KM / g.sunKm)
        val sM = asin(R_MOON_KM / g.moonKm)
        val enlarged = ENLARGEMENT * piM + piS
        return Lunar(angle(g.moon, g.sun * -1.0), enlarged - sS, enlarged + sS, sM)
    }

    fun lunarGamma(at: Instant): Double {
        val g = geo(at)
        return angle(g.moon, g.sun * -1.0)
    }

    fun solarGlobal(at: Instant): SolarGlobal {
        val g = geo(at)
        val piM = asin(R_EARTH_KM / g.moonKm)
        val piS = asin(R_EARTH_KM / g.sunKm)
        val sS = asin(R_SUN_KM / g.sunKm)
        val sM = asin(R_MOON_KM / g.moonKm)
        return SolarGlobal(angle(g.moon, g.sun), piM - piS + sM + sS)
    }

    fun solarGamma(at: Instant): Double {
        val g = geo(at)
        return angle(g.moon, g.sun)
    }

    fun discs(place: Place, at: Instant): Discs {
        val sun = riseSet.topocentric(Body.SUN, place, at)
        val moon = riseSet.topocentric(Body.MOON, place, at)
        return Discs(angle(moon.unit, sun.unit), asin(R_SUN_KM / sun.distanceKm), asin(R_MOON_KM / moon.distanceKm))
    }

    fun sigma(place: Place, at: Instant): Double =
        angle(riseSet.topocentric(Body.MOON, place, at).unit, riseSet.topocentric(Body.SUN, place, at).unit)

    /**
     * First..last instant of [interval] with [body] at or above the [s] horizon (f = altitude - h0 >= 0), or
     * null. f is sampled every 300 s from the start (and at the end); crossings are refined by Brent to 1 ms.
     */
    fun visible(body: Body, place: Place, interval: TimeRange, s: CalcSettings): TimeRange? {
        val a = interval.start
        val length = seconds(interval.end - a)
        val count = (length / VISIBILITY_STEP_SECONDS).toLong().toInt() + 2
        val xs = DoubleArray(count) { minOf(it * VISIBILITY_STEP_SECONDS, length) }
        val f: (Double) -> Double = { x -> riseSet.crossingDeg(body, place, a + offset(x), s) }
        val fs = DoubleArray(count) { f(xs[it]) }
        val first = fs.indexOfFirst { it >= 0.0 }
        if (first < 0) return null
        val last = fs.indexOfLast { it >= 0.0 }
        val start = if (first == 0) {
            a
        } else {
            a + offset(Brent.findRoot(xs[first - 1], fs[first - 1], xs[first], fs[first], ROOT_SECONDS, f))
        }
        val end = if (last == count - 1) {
            interval.end
        } else {
            a + offset(Brent.findRoot(xs[last], fs[last], xs[last + 1], fs[last + 1], ROOT_SECONDS, f))
        }
        return TimeRange(start, maxOf(start, end))
    }

    companion object {
        const val GRID_SECONDS = 600.0
        const val R_EARTH_KM = 6378.137
        const val R_MOON_KM = 1737.4
        const val R_SUN_KM = 695_700.0
        const val AU_KM = 149_597_870.7
        const val ENLARGEMENT = 1.0 + 1.0 / 85.0
        const val ROOT_SECONDS = 1e-3
        private const val VISIBILITY_STEP_SECONDS = 300.0
        private const val GOLDEN_WIDTH_SECONDS = 0.1
        private const val NANOS_PER_SECOND = 1e9
        private const val DEG_TO_RAD = PI / 180.0

        /** 1 / golden ratio. */
        private const val INVERSE_PHI = 0.6180339887498949

        fun offset(seconds: Double): Duration = (seconds * NANOS_PER_SECOND).roundToLong().nanoseconds

        fun seconds(d: Duration): Double = d.inWholeNanoseconds / NANOS_PER_SECOND

        /** Angle between two unit vectors, well conditioned at small angles. */
        fun angle(a: Vec3, b: Vec3): Double = atan2(a.cross(b).norm(), a.dot(b))

        private fun unit(lonDeg: Double, latDeg: Double): Vec3 {
            val lon = lonDeg * DEG_TO_RAD
            val lat = latDeg * DEG_TO_RAD
            return Vec3(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
        }

        /**
         * Instant of the minimum of [f] on [centre] +/- [halfWindowSeconds]: 600 s grid from the window start,
         * then golden-section search between the neighbours of the lowest sample, to 0.1 s.
         */
        fun minimum(centre: Instant, halfWindowSeconds: Double, f: (Instant) -> Double): Instant {
            val start = centre - offset(halfWindowSeconds)
            val count = (2.0 * halfWindowSeconds / GRID_SECONDS).toLong().toInt() + 1
            var low = 0
            var lowValue = Double.POSITIVE_INFINITY
            for (k in 0 until count) {
                val v = f(start + offset(k * GRID_SECONDS))
                if (v < lowValue) {
                    lowValue = v
                    low = k
                }
            }
            val from = maxOf(low - 1, 0) * GRID_SECONDS
            val to = minOf(low + 1, count - 1) * GRID_SECONDS
            return start + offset(goldenMin(from, to) { f(start + offset(it)) })
        }

        /**
         * The contact where [f] (negative at [from]) reaches zero, walking from [from] in 600 s steps in
         * [direction] (+1 later, -1 earlier) up to [reachSeconds]; Brent to 1 ms. Null if f(from) >= 0 or no
         * sign change is found.
         */
        fun contact(from: Instant, direction: Int, reachSeconds: Double, f: (Instant) -> Double): Instant? {
            val g: (Double) -> Double = { x -> f(from + offset(x)) }
            var x0 = 0.0
            var f0 = g(x0)
            if (!(f0 < 0.0)) return null
            var found: Instant? = null
            var k = 1
            while (found == null && k * GRID_SECONDS <= reachSeconds) {
                val x1 = direction * k * GRID_SECONDS
                val f1 = g(x1)
                if (f1 >= 0.0) {
                    val root = if (direction > 0) {
                        Brent.findRoot(x0, f0, x1, f1, ROOT_SECONDS, g)
                    } else {
                        Brent.findRoot(x1, f1, x0, f0, ROOT_SECONDS, g)
                    }
                    found = from + offset(root)
                }
                x0 = x1
                f0 = f1
                k++
            }
            return found
        }

        /** Golden-section search: abscissa of the minimum of a unimodal [f] on [from, to], to 0.1 s. */
        private fun goldenMin(from: Double, to: Double, f: (Double) -> Double): Double {
            var a = from
            var b = to
            var x1 = b - INVERSE_PHI * (b - a)
            var x2 = a + INVERSE_PHI * (b - a)
            var f1 = f(x1)
            var f2 = f(x2)
            while (b - a > GOLDEN_WIDTH_SECONDS) {
                if (f1 > f2) {
                    a = x1
                    x1 = x2
                    f1 = f2
                    x2 = a + INVERSE_PHI * (b - a)
                    f2 = f(x2)
                } else {
                    b = x2
                    x2 = x1
                    f2 = f1
                    x1 = b - INVERSE_PHI * (b - a)
                    f1 = f(x1)
                }
            }
            return (a + b) / 2.0
        }

        /** |a - b| as a helper for C2/C3 thresholds. */
        fun gap(a: Double, b: Double): Double = abs(a - b)
    }
}
