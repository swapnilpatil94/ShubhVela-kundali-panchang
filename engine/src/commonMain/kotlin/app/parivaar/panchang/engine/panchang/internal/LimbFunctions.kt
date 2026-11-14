package app.parivaar.panchang.engine.panchang.internal

import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.astro.positionOnly
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.Zodiac
import app.parivaar.panchang.engine.panchang.LimbKind
import kotlin.math.floor
import kotlin.math.min
import kotlin.time.Instant

/**
 * The three angle functions behind the five limbs (docs/specs/M03.md section 3.1), all geocentric apparent
 * on the true ecliptic of date:
 * - ELONGATION E = wrap360(moon - sun), tropical (TITHI, KARANA);
 * - MOON_SIDEREAL M = wrap360(moon - A) (NAKSHATRA, MOON_RASHI);
 * - YOGA_SUM Y = wrap360(moon + sun - 2A) (YOGA);
 * with A the true ayanamsa of the settings.
 */
internal enum class LimbFunction { ELONGATION, MOON_SIDEREAL, YOGA_SUM }

internal val LimbKind.function: LimbFunction
    get() = when (this) {
        LimbKind.TITHI, LimbKind.KARANA -> LimbFunction.ELONGATION
        LimbKind.NAKSHATRA, LimbKind.MOON_RASHI -> LimbFunction.MOON_SIDEREAL
        LimbKind.YOGA -> LimbFunction.YOGA_SUM
    }

private const val FULL_CIRCLE_DEG = 360.0

/** 0-based arc j0 = min(n - 1, floor(F n / 360)) of the function value [f] in [0, 360). */
internal fun arcIndex(kind: LimbKind, f: Double): Int {
    val n = kind.divisions
    return min(n - 1, floor(f * n / FULL_CIRCLE_DEG).toInt())
}

/** The reported index of arc [j0]: j0 + 1 for TITHI and KARANA, else j0. */
internal fun reportedIndex(kind: LimbKind, j0: Int): Int = j0 + kind.firstIndex

/** Start of arc [j] (any integer, taken mod n), degrees: (j mod n) 360 / n. */
internal fun arcStartDeg(kind: LimbKind, j: Int): Double {
    val n = kind.divisions
    return j.mod(n) * FULL_CIRCLE_DEG / n
}

/** g_B = wrap180(F - B), degrees: negative before the boundary [b], non-negative from it. */
internal fun boundaryDistance(f: Double, b: Double): Double = Zodiac.wrap180(f - b)

/**
 * The two longitudes and the ayanamsa at one instant; [ayanamsaDeg] is computed only when a sidereal
 * function needs it (so TITHI and KARANA work under any ayanamsa id). Values are pure functions of the
 * instant, the backend and the ayanamsa id: a cached sample equals a fresh one bit for bit.
 */
internal class LimbSample(
    private val eph: Ephemeris,
    private val tt: JulianDay,
    private val ayanamsa: AyanamsaId,
) {
    private var sun = Double.NaN
    private var moon = Double.NaN
    private var ayan = Double.NaN

    private fun sunDeg(): Double {
        if (sun.isNaN()) sun = eph.positionOnly(Body.SUN, tt).lonDeg
        return sun
    }

    private fun moonDeg(): Double {
        if (moon.isNaN()) moon = eph.positionOnly(Body.MOON, tt).lonDeg
        return moon
    }

    private fun ayanamsaDeg(): Double {
        if (ayan.isNaN()) ayan = Ayanamsa.trueDeg(ayanamsa, tt)
        return ayan
    }

    /** F in [0, 360). */
    fun value(fn: LimbFunction): Double = when (fn) {
        LimbFunction.ELONGATION -> Zodiac.wrap360(moonDeg() - sunDeg())
        LimbFunction.MOON_SIDEREAL -> Zodiac.wrap360(moonDeg() - ayanamsaDeg())
        LimbFunction.YOGA_SUM -> Zodiac.wrap360(moonDeg() + sunDeg() - 2.0 * ayanamsaDeg())
    }

    companion object {
        fun at(eph: Ephemeris, tables: TimeTables, at: Instant, ayanamsa: AyanamsaId): LimbSample =
            LimbSample(eph, TimeScales.utcToTt(at, tables), ayanamsa)
    }
}
