package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.EarthOrientation
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.time.Instant

/**
 * The two angles the house formulas need (docs/specs/M02.md section 3.3). They follow from an instant and a
 * place, so `toString` is redacted.
 *
 * @property lastDeg local apparent sidereal time as an angle, degrees in [0, 360)
 * @property epsTrueDeg true obliquity of the ecliptic, degrees
 */
data class HouseAngles(val lastDeg: Double, val epsTrueDeg: Double) {
    override fun toString(): String = "HouseAngles(redacted)"
}

/** Why a house system could not be computed (docs/specs/M02.md section 3.3). */
enum class HouseError { PLACIDUS_HIGH_LATITUDE, NO_CONVERGENCE }

/** Outcome of a house computation: [Cusps], or [Unavailable] with a code. */
sealed interface HouseResult {
    /**
     * Twelve houses. Angles are degrees in [0, 360); both lists are indexed by house - 1.
     *
     * The ascendant is derived from birth data, so `toString` is redacted.
     *
     * @property system the system these cusps belong to
     * @property ascDeg ascendant longitude
     * @property mcDeg midheaven longitude
     * @property cuspsDeg the cusp of each house (for SRIPATI and EQUAL the bhava madhya)
     * @property startsDeg where each house begins; house i covers [starts_i, starts_i+1)
     */
    data class Cusps(
        val system: HouseSystem,
        val ascDeg: Double,
        val mcDeg: Double,
        val cuspsDeg: List<Double>,
        val startsDeg: List<Double>,
    ) : HouseResult {
        init {
            require(cuspsDeg.size == HOUSE_COUNT && startsDeg.size == HOUSE_COUNT) { "a chart has 12 houses" }
        }

        /**
         * House 1..12 of [lon]: the i with wrap360(lon - starts_i) < wrap360(starts_i+1 - starts_i).
         * If rounding leaves [lon] in a gap of a few ulps between two houses, the house whose start is nearest
         * behind it is returned.
         */
        fun houseOf(lon: Double): Int {
            require(lon.isFinite()) { "longitude must be finite" }
            var nearest = 0
            var nearestArc = Double.POSITIVE_INFINITY
            for (i in 0 until HOUSE_COUNT) {
                val arc = Zodiac.wrap360(lon - startsDeg[i])
                if (arc < Zodiac.wrap360(startsDeg[(i + 1) % HOUSE_COUNT] - startsDeg[i])) return i + 1
                if (arc < nearestArc) {
                    nearestArc = arc
                    nearest = i
                }
            }
            return nearest + 1
        }

        override fun toString(): String = "HouseResult.Cusps(redacted)"

        private companion object {
            const val HOUSE_COUNT = 12
        }
    }

    /** The requested system is not defined for this input; [code] says why. */
    data class Unavailable(val code: HouseError) : HouseResult
}

/**
 * Ascendant, midheaven and house cusps (docs/specs/M02.md section 3.3).
 *
 * VERIFY with pandit: the EQUAL convention (the lagna is the middle of the first house) and the ascendant
 * inside the polar circles (the eastern intersection of horizon and ecliptic is always taken).
 */
object Houses {
    /** Local apparent sidereal time and true obliquity for [at] and [place] (UT1 = UTC, IAU 2006 / 2000B). */
    fun angles(at: Instant, place: Place, tables: TimeTables = TimeTables.DEFAULT): HouseAngles {
        val ut1 = TimeScales.ut1(at, tables)
        val tt = TimeScales.utcToTt(at, tables)
        val nutation = EarthOrientation.nutation(tt)
        val gastDeg = EarthOrientation.gastRad(ut1, tt, nutation.dpsiRad) / DEG_TO_RAD
        val epsDeg = (EarthOrientation.meanObliquityRad(tt) + nutation.depsRad) / DEG_TO_RAD
        return HouseAngles(Zodiac.wrap360(gastDeg + place.lonDeg), epsDeg)
    }

    /**
     * Houses on the true equinox of date (tropical longitudes) for geodetic latitude [latDeg].
     * PLACIDUS is [HouseResult.Unavailable] above 66.0 degrees of latitude, or if its iteration does not settle.
     */
    fun tropical(a: HouseAngles, latDeg: Double, system: HouseSystem): HouseResult {
        require(latDeg >= -QUARTER_CIRCLE_DEG && latDeg <= QUARTER_CIRCLE_DEG) { "latitude out of range" }
        val theta = a.lastDeg * DEG_TO_RAD
        val eps = a.epsTrueDeg * DEG_TO_RAD
        val phi = latDeg * DEG_TO_RAD
        val asc = ascendantDeg(theta, eps, phi)
        val mc = Zodiac.wrap360(eclipticLonOfRa(theta, eps) / DEG_TO_RAD)
        return when (system) {
            HouseSystem.WHOLE_SIGN -> wholeSign(asc, mc)
            HouseSystem.EQUAL -> equal(asc, mc)
            HouseSystem.SRIPATI -> sripati(asc, mc)
            HouseSystem.PLACIDUS -> placidus(asc, mc, theta, eps, phi)
        }
    }

    /**
     * Sidereal houses: every tropical angle minus the true ayanamsa of `settings.ayanamsa`; WHOLE_SIGN is
     * rebuilt from the sidereal ascendant. Throws [AyanamsaUnavailableException] for a disabled ayanamsa.
     */
    fun sidereal(
        at: Instant,
        place: Place,
        settings: CalcSettings = CalcSettings(),
        tables: TimeTables = TimeTables.DEFAULT,
    ): HouseResult {
        val ayanamsa = Ayanamsa.trueDeg(settings.ayanamsa, TimeScales.utcToTt(at, tables))
        val tropical = tropical(angles(at, place, tables), place.latDeg, settings.houseSystem)
        if (tropical !is HouseResult.Cusps) return tropical
        val asc = Zodiac.wrap360(tropical.ascDeg - ayanamsa)
        val mc = Zodiac.wrap360(tropical.mcDeg - ayanamsa)
        return when (tropical.system) {
            HouseSystem.WHOLE_SIGN -> wholeSign(asc, mc)
            else -> HouseResult.Cusps(
                system = tropical.system,
                ascDeg = asc,
                mcDeg = mc,
                cuspsDeg = tropical.cuspsDeg.map { Zodiac.wrap360(it - ayanamsa) },
                startsDeg = tropical.startsDeg.map { Zodiac.wrap360(it - ayanamsa) },
            )
        }
    }
}

private const val DEG_TO_RAD = PI / 180.0
private const val QUARTER_CIRCLE_DEG = 90.0
private const val HALF_CIRCLE_DEG = 180.0
private const val SIGN_DEG = 30.0
private const val HOUSES = 12
private const val OPPOSITE = HOUSES / 2
private const val THIRD = 1.0 / 3.0
private const val TWO_THIRDS = 2.0 / 3.0

private const val PLACIDUS_MAX_LAT_DEG = 66.0
private const val PLACIDUS_TOLERANCE_DEG = 1e-11
private const val PLACIDUS_MAX_PASSES = 100

// Indices (house - 1) of the cusps that are computed; the other six are their opposites.
private const val C1 = 0
private const val C2 = 1
private const val C3 = 2
private const val C10 = 9
private const val C11 = 10
private const val C12 = 11

/** Ecliptic longitude (radians) of the ecliptic point with right ascension [alpha]. */
private fun eclipticLonOfRa(alpha: Double, eps: Double): Double = atan2(sin(alpha), cos(alpha) * cos(eps))

/**
 * The rising intersection of horizon and ecliptic, degrees in [0, 360). The closed form picks one of the two
 * intersections; inside the polar circles it can be the setting one (hour angle west of the meridian), and
 * then the opposite point is the ascendant.
 */
private fun ascendantDeg(theta: Double, eps: Double, phi: Double): Double {
    val lam = atan2(cos(theta) * cos(phi), -(sin(theta) * cos(eps) * cos(phi) + sin(phi) * sin(eps)))
    val alpha = atan2(sin(lam) * cos(eps), cos(lam))
    val rising = if (sin(theta - alpha) > 0.0) lam + PI else lam
    return Zodiac.wrap360(rising / DEG_TO_RAD)
}

private fun wholeSign(asc: Double, mc: Double): HouseResult.Cusps {
    val first = min(HOUSES - 1, floor(asc / SIGN_DEG).toInt())
    val starts = List(HOUSES) { SIGN_DEG * ((first + it) % HOUSES) }
    return HouseResult.Cusps(HouseSystem.WHOLE_SIGN, asc, mc, starts, starts)
}

private fun equal(asc: Double, mc: Double): HouseResult.Cusps {
    val cusps = List(HOUSES) { Zodiac.wrap360(asc + SIGN_DEG * it) }
    val starts = List(HOUSES) { Zodiac.wrap360(asc + SIGN_DEG * it - SIGN_DEG / 2.0) }
    return HouseResult.Cusps(HouseSystem.EQUAL, asc, mc, cusps, starts)
}

/** Porphyry trisection of the quadrants; the cusps are the middles of the houses, the starts the sandhis. */
private fun sripati(asc: Double, mc: Double): HouseResult.Cusps {
    val a = Zodiac.wrap360(asc - mc)
    val c = DoubleArray(HOUSES)
    c[C10] = mc
    c[C11] = mc + a * THIRD
    c[C12] = mc + a * TWO_THIRDS
    c[C1] = asc
    c[C2] = asc + (HALF_CIRCLE_DEG - a) * THIRD
    c[C3] = asc + (HALF_CIRCLE_DEG - a) * TWO_THIRDS
    val cusps = completed(c)
    val starts = List(HOUSES) { i ->
        val previous = cusps[(i + HOUSES - 1) % HOUSES]
        Zodiac.wrap360(previous + Zodiac.wrap360(cusps[i] - previous) / 2.0)
    }
    return HouseResult.Cusps(HouseSystem.SRIPATI, asc, mc, cusps, starts)
}

private fun placidus(asc: Double, mc: Double, theta: Double, eps: Double, phi: Double): HouseResult {
    if (abs(phi / DEG_TO_RAD) > PLACIDUS_MAX_LAT_DEG) {
        return HouseResult.Unavailable(HouseError.PLACIDUS_HIGH_LATITUDE)
    }
    val c = DoubleArray(HOUSES)
    c[C1] = asc
    c[C10] = mc
    // d = diurnal semi-arc of the cusp point; the nocturnal semi-arc is pi - d.
    c[C11] = placidusCusp(theta, eps, phi, SIGN_DEG * DEG_TO_RAD) { d -> d * THIRD }
    c[C12] = placidusCusp(theta, eps, phi, 2.0 * SIGN_DEG * DEG_TO_RAD) { d -> d * TWO_THIRDS }
    c[C2] = placidusCusp(theta, eps, phi, PI - 2.0 * SIGN_DEG * DEG_TO_RAD) { d -> PI - (PI - d) * TWO_THIRDS }
    c[C3] = placidusCusp(theta, eps, phi, PI - SIGN_DEG * DEG_TO_RAD) { d -> PI - (PI - d) * THIRD }
    return if (c.any { it.isNaN() }) {
        HouseResult.Unavailable(HouseError.NO_CONVERGENCE)
    } else {
        val cusps = completed(c)
        HouseResult.Cusps(HouseSystem.PLACIDUS, asc, mc, cusps, cusps)
    }
}

/**
 * One Placidus cusp, degrees in [0, 360), or NaN if the fixed-point iteration does not settle in 100 passes.
 * The right ascension starts at theta + [startOffset] and is replaced by theta + [rule] (diurnal semi-arc of
 * the ecliptic point with that right ascension) until it moves by less than 1e-11 degrees.
 */
private inline fun placidusCusp(
    theta: Double,
    eps: Double,
    phi: Double,
    startOffset: Double,
    rule: (Double) -> Double,
): Double {
    var alpha = theta + startOffset
    var cusp = Double.NaN
    var pass = 0
    while (pass < PLACIDUS_MAX_PASSES && cusp.isNaN()) {
        val lam = eclipticLonOfRa(alpha, eps)
        val delta = asin(sin(eps) * sin(lam))
        val next = theta + rule(acos(-tan(phi) * tan(delta)))
        val step = Zodiac.wrap180((next - alpha) / DEG_TO_RAD)
        alpha = next
        // A NaN step (no semi-arc) never passes this test, so the loop ends without a cusp.
        if (abs(step) < PLACIDUS_TOLERANCE_DEG) cusp = Zodiac.wrap360(eclipticLonOfRa(alpha, eps) / DEG_TO_RAD)
        pass++
    }
    return cusp
}

/** Fills houses 4..9 with the opposites of 10, 11, 12, 1, 2, 3 and reduces everything to [0, 360). */
private fun completed(c: DoubleArray): List<Double> {
    for (i in intArrayOf(C10, C11, C12, C1, C2, C3)) c[(i + OPPOSITE) % HOUSES] = c[i] + HALF_CIRCLE_DEG
    return List(HOUSES) { Zodiac.wrap360(c[it]) }
}
