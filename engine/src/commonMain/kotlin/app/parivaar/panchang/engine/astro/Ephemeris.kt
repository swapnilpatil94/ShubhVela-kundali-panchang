package app.parivaar.panchang.engine.astro

/** Bodies of the Vedic chart. `RAHU_*` is the Moon's ascending node, `KETU_*` the descending node. */
enum class Body { SUN, MOON, MERCURY, VENUS, MARS, JUPITER, SATURN, RAHU_MEAN, RAHU_TRUE, KETU_MEAN, KETU_TRUE }

/**
 * Geocentric apparent place on the true ecliptic and equinox of date.
 *
 * @property lonDeg ecliptic longitude in degrees, in [0, 360)
 * @property latDeg ecliptic latitude in degrees (0.0 for the nodes)
 * @property distanceAu geocentric distance in au at the time the light left the body (0.0 for the nodes)
 * @property speedDegPerDay rate of [lonDeg] in degrees per day of TT (central difference over +/- 0.005 d)
 */
data class EclipticPos(val lonDeg: Double, val latDeg: Double, val distanceAu: Double, val speedDegPerDay: Double)

/** An ephemeris backend. Instances hold small caches and are not thread-safe; results are deterministic. */
interface Ephemeris {
    /**
     * Geocentric apparent position of [body] at Terrestrial Time [tt].
     * Throws [EphemerisRangeException] outside [validRange].
     */
    fun geoApparent(body: Body, tt: JulianDay): EclipticPos

    /** Inclusive range of TT Julian Days this backend serves. */
    val validRange: ClosedRange<JulianDay>
}

/**
 * Thrown when a TT Julian Day lies outside the range an [Ephemeris] can serve.
 *
 * The requested instant can be a birth instant, so it is kept out of `message` and `toString` (they reach
 * crash reports); callers that need it read [tt].
 *
 * @property tt the requested TT Julian Day; personal data, never log it
 */
class EphemerisRangeException(val tt: JulianDay) :
    IllegalArgumentException("requested instant is outside the ephemeris range")
