package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.HighLatitudeRule
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.SunriseConvention
import app.parivaar.panchang.engine.astro.internal.Apparent
import app.parivaar.panchang.engine.astro.internal.Brent
import app.parivaar.panchang.engine.astro.internal.Vec3
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

enum class RiseSetKind { RISE, SET }

/**
 * Topocentric direction and distance of a body (internal, docs/specs/M04.md section 3.9).
 *
 * @property unit unit vector, true equator and equinox of date
 * @property distanceKm observer-to-body distance, km
 */
internal class TopoVec(val unit: Vec3, val distanceKm: Double) {
    override fun toString(): String = "TopoVec(redacted)"
}

/**
 * One horizon crossing.
 *
 * @property at instant of the crossing (UTC)
 * @property isFallback true when the instant is a high-latitude convention, not an observed crossing
 */
data class RiseSetEvent(val kind: RiseSetKind, val at: Instant, val isFallback: Boolean = false)

sealed interface RiseSetResult {
    /** The crossings inside the window, in time order (never empty). */
    data class Events(val list: List<RiseSetEvent>) : RiseSetResult

    /** No crossing in the window: the body stayed above the chosen horizon ([alwaysAbove]) or below it. */
    data class NoEvent(val alwaysAbove: Boolean) : RiseSetResult
}

/**
 * Rise and set of the Sun and Moon (docs/specs/M01.md section 3.8).
 *
 * Altitudes are topocentric for a WGS84 observer and airless; refraction and the semidiameter enter only
 * through the altitude threshold h0 of the chosen [SunriseConvention]. Polar motion, diurnal aberration and
 * horizon dip are ignored. Holds the samples of the last Sun and Moon windows: not thread-safe. Results are
 * deterministic.
 */
class RiseSet(private val eph: Ephemeris, private val tables: TimeTables = TimeTables.DEFAULT) {
    private var sunGrid: Grid? = null
    private var moonGrid: Grid? = null

    /** Topocentric, airless altitude of the centre of [body] in degrees. */
    fun altitudeDeg(body: Body, place: Place, at: Instant): Double = sight(body, Site(place), at).altitudeDeg

    /**
     * Crossings of the [settings] horizon by [body] (SUN or MOON) in `[windowStart, windowStart + windowHours]`.
     *
     * f = altitude - h0 is sampled every 600 s; a change from negative to non-negative is a RISE, the reverse
     * a SET, each refined by Brent's method to a bracket of 0.5 s and then interpolated inside it (the instant
     * is good to milliseconds). Without a sign change the result is [RiseSetResult.NoEvent].
     * A body that grazes the horizon between two samples (both crossings within 10 minutes) is not reported.
     */
    fun find(
        body: Body,
        place: Place,
        windowStart: Instant,
        settings: CalcSettings = CalcSettings(),
        windowHours: Int = 24,
    ): RiseSetResult {
        require(body == Body.SUN || body == Body.MOON) { "rise/set is defined for SUN and MOON only" }
        require(windowHours > 0) { "windowHours must be positive" }
        val grid = grid(body, place, windowStart, windowHours)
        val f = DoubleArray(grid.altitudeDeg.size) {
            grid.altitudeDeg[it] - thresholdDeg(body, settings, grid.distanceKm[it])
        }
        val events = ArrayList<RiseSetEvent>()
        for (k in 0 until f.size - 1) {
            val kind = when {
                f[k] < 0.0 && f[k + 1] >= 0.0 -> RiseSetKind.RISE
                f[k] >= 0.0 && f[k + 1] < 0.0 -> RiseSetKind.SET
                else -> continue
            }
            // Seconds are counted from the sample, so an event does not depend on where the window starts.
            val sampleAt = windowStart + offset(k * STEP_SECONDS)
            val root = Brent.findRoot(0.0, f[k], STEP_SECONDS, f[k + 1], BRACKET_SECONDS) { seconds ->
                crossing(body, grid.site, sampleAt + offset(seconds), settings)
            }
            events += RiseSetEvent(kind, sampleAt + offset(root))
        }
        return if (events.isEmpty()) RiseSetResult.NoEvent(alwaysAbove = f[0] >= 0.0) else RiseSetResult.Events(events)
    }

    /**
     * Sunrise and sunset of the 24 h window from [windowStart] for places where a day may have neither.
     *
     * If the window holds at least one RISE and one SET, those real events are returned unchanged. Otherwise
     * the [CalcSettings.highLatitudeRule] convention applies and every returned event has `isFallback = true`:
     * - LOCAL_NOON_PM_6H: RISE and SET at the instant of greatest Sun altitude in the window (to 1 ms) minus and
     *   plus 6 h; they may fall outside the window when the culmination is near one of its ends.
     * - NEAREST_DAY: for k = 1..183 the window k days earlier, then k days later; the events of the first one
     *   with both a RISE and a SET, moved by k days into this window. Empty when no such day exists (at the
     *   poles) or the ephemeris does not reach that far.
     *
     * VERIFY with pandit (spec TODO): both conventions; the UI flag stays off until sign-off.
     */
    fun sunWithFallback(
        place: Place,
        windowStart: Instant,
        settings: CalcSettings = CalcSettings(),
    ): List<RiseSetEvent> {
        val real = find(Body.SUN, place, windowStart, settings)
        if (real is RiseSetResult.Events && hasRiseAndSet(real.list)) return real.list
        return when (settings.highLatitudeRule) {
            HighLatitudeRule.LOCAL_NOON_PM_6H -> {
                val noon = culmination(place, windowStart)
                listOf(
                    RiseSetEvent(RiseSetKind.RISE, noon - NOON_OFFSET, isFallback = true),
                    RiseSetEvent(RiseSetKind.SET, noon + NOON_OFFSET, isFallback = true),
                )
            }
            HighLatitudeRule.NEAREST_DAY -> nearestDay(place, windowStart, settings)
        }
    }

    /** f = altitude - h0 in degrees: negative below the [settings] horizon. Exposed for the tests. */
    internal fun crossingDeg(body: Body, place: Place, at: Instant, settings: CalcSettings): Double =
        crossing(body, Site(place), at, settings)

    private fun crossing(body: Body, site: Site, at: Instant, settings: CalcSettings): Double {
        val s = sight(body, site, at)
        return s.altitudeDeg - thresholdDeg(body, settings, s.distanceKm)
    }

    /**
     * Topocentric place of the centre of [body] (SUN or MOON) for a WGS84 observer at [place]: unit vector on
     * the true equator and equinox of date, and distance in km. The same maths as the altitudes; for the
     * eclipse engine (docs/specs/M04.md section 3.9).
     */
    internal fun topocentric(body: Body, place: Place, at: Instant): TopoVec {
        val s = sight(body, Site(place), at)
        return TopoVec(s.topocentricKm / s.distanceKm, s.distanceKm)
    }

    /** Altitude, topocentric distance and observer-to-body vector of the body centre (spec 3.8). */
    private fun sight(body: Body, site: Site, at: Instant): Sight {
        val tt = TimeScales.utcToTt(at, tables)
        val ut1 = TimeScales.ut1(at, tables)
        val pos = eph.positionOnly(body, tt)
        val nutation = EarthOrientation.nutation(tt)

        // Geocentric apparent place -> true equator and equinox of date, km.
        val lon = pos.lonDeg * DEG_TO_RAD
        val lat = pos.latDeg * DEG_TO_RAD
        // The nodes are directions (distance 0.0): put them far enough away to have no parallax.
        val geocentricKm = if (pos.distanceAu > 0.0) pos.distanceAu * Apparent.AU_KM else DIRECTION_ONLY_KM
        val ecliptic = Vec3(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat)) * geocentricKm
        val equatorial = EarthOrientation.eclipticToTrueEquator(tt, nutation.depsRad) * ecliptic

        // Observer and geodetic zenith in the same frame: local apparent sidereal time theta = GAST + longitude.
        val theta = EarthOrientation.gastRad(ut1, tt, nutation.dpsiRad) + site.lonRad
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        val observer = Vec3(site.equatorialKm * cosTheta, site.equatorialKm * sinTheta, site.polarKm)
        val zenith = Vec3(site.cosLat * cosTheta, site.cosLat * sinTheta, site.sinLat)

        val topocentric = equatorial - observer
        val distanceKm = topocentric.norm()
        val sinAltitude = (topocentric.dot(zenith) / distanceKm).coerceIn(-1.0, 1.0)
        return Sight(asin(sinAltitude) * RAD_TO_DEG, distanceKm, topocentric)
    }

    /** The 600 s samples of a window; the last window of each body is kept (conventions share the samples). */
    private fun grid(body: Body, place: Place, start: Instant, hours: Int): Grid {
        val cached = if (body == Body.SUN) sunGrid else moonGrid
        if (cached != null && cached.covers(place, start, hours)) return cached
        val site = Site(place)
        val count = SAMPLES_PER_HOUR * hours + 1
        val altitudeDeg = DoubleArray(count)
        val distanceKm = DoubleArray(count)
        for (k in 0 until count) {
            val s = sight(body, site, start + offset(k * STEP_SECONDS))
            altitudeDeg[k] = s.altitudeDeg
            distanceKm[k] = s.distanceKm
        }
        val fresh = Grid(place, start, hours, site, altitudeDeg, distanceKm)
        if (body == Body.SUN) sunGrid = fresh else moonGrid = fresh
        return fresh
    }

    /** Instant of the greatest Sun altitude in the 24 h window, to 1 s. */
    private fun culmination(place: Place, windowStart: Instant): Instant {
        val grid = grid(Body.SUN, place, windowStart, DAY_HOURS)
        var top = 0
        for (k in grid.altitudeDeg.indices) if (grid.altitudeDeg[k] > grid.altitudeDeg[top]) top = k
        // The altitude has one maximum between the neighbours of the highest sample.
        val from = maxOf(top - 1, 0) * STEP_SECONDS
        val to = minOf(top + 1, grid.altitudeDeg.size - 1) * STEP_SECONDS
        val seconds = maximise(from, to, CULMINATION_BRACKET_SECONDS) {
            sight(Body.SUN, grid.site, windowStart + offset(it)).altitudeDeg
        }
        return windowStart + offset(seconds)
    }

    private fun nearestDay(place: Place, windowStart: Instant, settings: CalcSettings): List<RiseSetEvent> {
        for (k in 1..NEAREST_DAY_REACH) {
            for (shift in arrayOf(-k.days, k.days)) {
                val start = windowStart + shift
                if (!inEphemerisRange(start)) continue
                val result = find(Body.SUN, place, start, settings)
                if (result is RiseSetResult.Events && hasRiseAndSet(result.list)) {
                    return result.list.map { RiseSetEvent(it.kind, it.at - shift, isFallback = true) }
                }
            }
        }
        return emptyList()
    }

    private fun inEphemerisRange(windowStart: Instant): Boolean =
        TimeScales.utcToTt(windowStart, tables) in eph.validRange &&
            TimeScales.utcToTt(windowStart + DAY_HOURS.hours, tables) in eph.validRange

    private class Sight(val altitudeDeg: Double, val distanceKm: Double, val topocentricKm: Vec3)

    private class Grid(
        val place: Place,
        val start: Instant,
        val hours: Int,
        val site: Site,
        val altitudeDeg: DoubleArray,
        val distanceKm: DoubleArray,
    ) {
        fun covers(place: Place, start: Instant, hours: Int): Boolean =
            this.place == place && this.start == start && this.hours == hours
    }

    /** Place-dependent constants: geocentric observer on the WGS84 ellipsoid (cylindrical components, km). */
    private class Site(place: Place) {
        val lonRad = place.lonDeg * DEG_TO_RAD
        val sinLat = sin(place.latDeg * DEG_TO_RAD)
        val cosLat = cos(place.latDeg * DEG_TO_RAD)
        val equatorialKm: Double
        val polarKm: Double

        init {
            val c = 1.0 / sqrt(cosLat * cosLat + POLAR_FACTOR * sinLat * sinLat)
            val heightKm = place.elevationM / METRES_PER_KM
            equatorialKm = (EARTH_RADIUS_KM * c + heightKm) * cosLat
            polarKm = (EARTH_RADIUS_KM * POLAR_FACTOR * c + heightKm) * sinLat
        }
    }
}

private const val DEG_TO_RAD = PI / 180.0
private const val RAD_TO_DEG = 180.0 / PI
private const val NANOS_PER_SECOND = 1e9
private const val METRES_PER_KM = 1000.0

/** Sampling step of the search, seconds (6 samples per hour). */
private const val STEP_SECONDS = 600.0
private const val SAMPLES_PER_HOUR = 6
private const val DAY_HOURS = 24

/** Widest bracket left around a returned crossing, seconds. */
private const val BRACKET_SECONDS = 0.5
/** Culmination bracket: 1 ms, so day-to-day fallback night lengths are smooth (docs/ENGINE_DIFFS.md M04). */
private const val CULMINATION_BRACKET_SECONDS = 0.001
private const val NEAREST_DAY_REACH = 183
private val NOON_OFFSET: Duration = 6.hours

/** WGS84 equatorial radius a, km, and (1 - f)^2 with f = 1 / 298.257223563. */
private const val EARTH_RADIUS_KM = 6378.137
private const val POLAR_FACTOR = (1.0 - 1.0 / 298.257223563) * (1.0 - 1.0 / 298.257223563)
private const val MOON_RADIUS_KM = 1737.4

/** Stand-in distance for bodies without one (the lunar nodes): parallax under 1e-9 arcsec. */
private const val DIRECTION_ONLY_KM = 1e18

/** Refraction at the horizon: 34 arcmin at 1010 hPa and 10 C (283 K). */
private const val REFRACTION_DEG = 34.0 / 60.0
private const val REFERENCE_PRESSURE_HPA = 1010.0
private const val REFERENCE_TEMPERATURE_K = 283.0
private const val CELSIUS_TO_KELVIN = 273.0
private const val SUN_SEMIDIAMETER_DEG = 16.0 / 60.0

/** 1 / golden ratio. */
private const val INVERSE_PHI = 0.6180339887498949

/** h0: altitude of the body centre at the moment of rise or set, degrees (spec 3.8). */
private fun thresholdDeg(body: Body, settings: CalcSettings, distanceKm: Double): Double {
    val refraction = REFRACTION_DEG * (settings.pressureHPa / REFERENCE_PRESSURE_HPA) *
        (REFERENCE_TEMPERATURE_K / (CELSIUS_TO_KELVIN + settings.temperatureC))
    return when (settings.sunrise) {
        SunriseConvention.CENTER_GEOMETRIC -> 0.0
        SunriseConvention.CENTER_REFRACTED -> -refraction
        SunriseConvention.UPPER_LIMB_REFRACTED -> when (body) {
            Body.SUN -> -(refraction + SUN_SEMIDIAMETER_DEG)
            else -> -(refraction + asin(MOON_RADIUS_KM / distanceKm) * RAD_TO_DEG)
        }
    }
}

private fun hasRiseAndSet(list: List<RiseSetEvent>): Boolean =
    list.any { it.kind == RiseSetKind.RISE } && list.any { it.kind == RiseSetKind.SET }

/** Seconds from a window start as a Duration, rounded to the nanosecond. */
private fun offset(seconds: Double): Duration = (seconds * NANOS_PER_SECOND).roundToLong().nanoseconds

/** Golden-section search: abscissa of the maximum of a unimodal [f] on [from, to], to within [width]. */
private fun maximise(from: Double, to: Double, width: Double, f: (Double) -> Double): Double {
    var a = from
    var b = to
    var x1 = b - INVERSE_PHI * (b - a)
    var x2 = a + INVERSE_PHI * (b - a)
    var f1 = f(x1)
    var f2 = f(x2)
    while (b - a > width) {
        if (f1 < f2) {
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
