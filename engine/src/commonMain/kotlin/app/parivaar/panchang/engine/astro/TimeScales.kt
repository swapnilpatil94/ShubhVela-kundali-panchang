@file:Suppress("MagicNumber") // published polynomial coefficients and epoch constants

package app.parivaar.panchang.engine.astro

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.time.Instant

/**
 * Time-scale rules of docs/specs/M01.md section 3.2 (mirrored by `tools/oracle/timescale.py`).
 *
 * - Inside the leap-second table (`first entry <= i <= validThrough`): TT = UTC + (TAI-UTC) + 32.184 s and
 *   UT1 = UTC (DUT1, under 0.9 s, is ignored).
 * - Before the table the Instant is read as UT1 and TT = UT1 + deltaT(y), y = 2000 + (jdUt - 2451544.5) / 365.2425.
 * - After `validThrough` the Instant is read as UT1 and TT - UT1 is held continuous: the table value at
 *   `validThrough` (TAI-UTC + 32.184 s, i.e. TT-UT1 with DUT1 ignored) plus [FUTURE_SLOPE_SECONDS_PER_YEAR] per
 *   Julian year after it. There is no step at the horizon (the old literal rule jumped by about +6.5 s to the
 *   2005-2050 polynomial, 75.69 s at 2027.0; docs/ENGINE_DIFFS.md, Fable review M2).
 *
 * TODO(owner): before release refresh [TimeTables.DEFAULT] (leap seconds and `validThrough`) from the latest IERS
 * Bulletin C, and re-check the slope against IERS Bulletin A UT1-UTC predictions; change
 * `tools/oracle/timescale.py` in lock step and regenerate the golden data.
 */
object TimeScales {
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val SECONDS_PER_DAY = 86400.0
    private const val SECONDS_PER_DAY_LONG = 86400L
    private const val NANOS_PER_SECOND = 1e9
    private const val TT_MINUS_TAI_SECONDS = 32.184
    private const val DECIMAL_YEAR_EPOCH_JD = 2451544.5 // 2000-01-01 00:00
    private const val GREGORIAN_YEAR_DAYS = 365.2425
    private const val TT_TO_UTC_PASSES = 3
    private const val SECONDS_PER_JULIAN_YEAR = 365.25 * 86400.0

    /**
     * Growth of TT - UT1 after `validThrough`, seconds per Julian year. Source assumption (VERIFY / TODO(owner)):
     * a mean excess length of day of about +0.27 ms over the coming decades (0.27 ms x 365.25 = 0.1 s/yr). That
     * sits between the near-zero drift observed by the IERS since 2017 (TT-UT1 about 69.0-69.2 s, Bulletin A)
     * and the long-term tidal trend of the Espenak-Meeus polynomials (about +0.6 s/yr at 2027). The prediction
     * error grows to tens of seconds by 2100 whichever value is chosen; the slope only keeps the rule smooth.
     */
    const val FUTURE_SLOPE_SECONDS_PER_YEAR = 0.1

    /** Julian Day of the Instant on its own (UTC) scale: 2440587.5 + epoch seconds / 86400. */
    fun jdUtc(i: Instant): JulianDay = jdOf(i, 0.0)

    /**
     * deltaT = TT - UT1 in seconds: Espenak-Meeus polynomials (NASA Eclipse Web Site, "Polynomial Expressions
     * for Delta T", https://eclipse.gsfc.nasa.gov/SEcat5/deltatpoly.html; Espenak and Meeus 2006,
     * NASA/TP-2006-214141).
     *
     * Rows from 1900 on are the ones listed in docs/specs/M01.md section 3.2. VERIFY: the rows before 1900
     * were entered from the published Espenak-Meeus expressions without access to the NASA page at the time
     * of writing (no download approved); they are cross-checked against Astronomy Engine's independent
     * implementation of the same polynomials (`DeltaTCrossCheckTest`) but still need a human read against the
     * page. The oracle (`timescale.py`) does not implement them: golden data starts in 1900.
     */
    fun deltaTSeconds(decimalYear: Double): Double {
        val y = decimalYear
        return when {
            y < 1900.0 -> deltaTBefore1900(y)
            y < 1920.0 -> poly(y - 1900.0, -2.79, 1.494119, -0.0598939, 0.0061966, -0.000197)
            y < 1941.0 -> poly(y - 1920.0, 21.20, 0.84493, -0.076100, 0.0020936)
            y < 1961.0 -> poly(y - 1950.0, 29.07, 0.407, -1.0 / 233.0, 1.0 / 2547.0)
            y < 1986.0 -> poly(y - 1975.0, 45.45, 1.067, -1.0 / 260.0, -1.0 / 718.0)
            y < 2005.0 -> poly(y - 2000.0, 63.86, 0.3345, -0.060374, 0.0017275, 0.000651814, 0.00002373599)
            y < 2050.0 -> poly(y - 2000.0, 62.92, 0.32217, 0.005589)
            y < 2150.0 -> longTermParabola(y) - 0.5628 * (2150.0 - y)
            else -> longTermParabola(y)
        }
    }

    /** UT1 as a Julian Day. UT1 = UTC inside the table (DUT1 ignored); outside it the Instant already is UT1. */
    @Suppress("UNUSED_PARAMETER") // tables kept in the signature: a future DUT1 table changes only this body
    fun ut1(i: Instant, t: TimeTables = TimeTables.DEFAULT): JulianDay = jdUtc(i)

    /** Terrestrial Time (TT) Julian Day of the Instant. */
    fun utcToTt(i: Instant, t: TimeTables = TimeTables.DEFAULT): JulianDay = jdOf(i, ttMinusUtcSeconds(i, t))

    /**
     * Inverse of [utcToTt] by fixed-point iteration (3 passes). Within a few seconds of a step of the rule
     * (leap seconds, table start, `validThrough`) the inverse is not unique and the result is one of the
     * candidates.
     */
    fun ttToUtc(tt: JulianDay, t: TimeTables = TimeTables.DEFAULT): Instant {
        val ttSeconds = (tt.value - UNIX_EPOCH_JD) * SECONDS_PER_DAY
        var utc = instantOfEpochSeconds(ttSeconds)
        repeat(TT_TO_UTC_PASSES) { utc = instantOfEpochSeconds(ttSeconds - ttMinusUtcSeconds(utc, t)) }
        return utc
    }

    /**
     * TT - (the Instant's own scale) in seconds: table value + 32.184 s inside the table; after `validThrough`
     * the value at `validThrough` plus the slope (continuous); before the table deltaT(y).
     */
    internal fun ttMinusUtcSeconds(i: Instant, t: TimeTables): Double = when {
        i < t.leapSeconds.first().first ->
            deltaTSeconds(2000.0 + (jdUtc(i).value - DECIMAL_YEAR_EPOCH_JD) / GREGORIAN_YEAR_DAYS)
        i <= t.validThrough -> tableSeconds(i, t)
        else -> {
            val v = t.validThrough
            val elapsedSeconds = (i.epochSeconds - v.epochSeconds).toDouble() +
                (i.nanosecondsOfSecond - v.nanosecondsOfSecond) / NANOS_PER_SECOND
            tableSeconds(v, t) + FUTURE_SLOPE_SECONDS_PER_YEAR * (elapsedSeconds / SECONDS_PER_JULIAN_YEAR)
        }
    }

    private fun tableSeconds(i: Instant, t: TimeTables): Double {
        var taiMinusUtc = 0
        for ((from, value) in t.leapSeconds) if (i >= from) taiMinusUtc = value
        return taiMinusUtc + TT_MINUS_TAI_SECONDS
    }

    /** TDB - TT in seconds: 0.001657 sin g + 0.000014 sin 2g, g = 357.53 deg + 0.98560028 deg (tt - J2000). */
    internal fun tdbMinusTtSeconds(tt: JulianDay): Double {
        val g = (357.53 + 0.98560028 * (tt.value - JulianDay.J2000)) * (PI / 180.0)
        return 0.001657 * sin(g) + 0.000014 * sin(2.0 * g)
    }

    /** TDB - TT in days (the offset handed to the ephemeris reader next to the TT Julian Day). */
    internal fun tdbMinusTtDays(tt: JulianDay): Double = tdbMinusTtSeconds(tt) / SECONDS_PER_DAY

    /** 2440587.5 + (epoch seconds + offset) / 86400, with the whole days split off first to keep precision. */
    private fun jdOf(i: Instant, offsetSeconds: Double): JulianDay {
        val days = i.epochSeconds.floorDiv(SECONDS_PER_DAY_LONG)
        val secondOfDay = (i.epochSeconds - days * SECONDS_PER_DAY_LONG).toDouble() +
            i.nanosecondsOfSecond / NANOS_PER_SECOND
        return JulianDay((UNIX_EPOCH_JD + days.toDouble()) + (secondOfDay + offsetSeconds) / SECONDS_PER_DAY)
    }

    private fun instantOfEpochSeconds(seconds: Double): Instant {
        val whole = floor(seconds)
        return Instant.fromEpochSeconds(whole.toLong(), ((seconds - whole) * NANOS_PER_SECOND).roundToLong())
    }
}

/** Espenak-Meeus rows before 1900 (VERIFY against the NASA page; see [TimeScales.deltaTSeconds]). */
private fun deltaTBefore1900(y: Double): Double = when {
    y < -500.0 -> longTermParabola(y)
    y < 500.0 -> poly(
        y / 100.0, 10583.6, -1014.41, 33.78311, -5.952053, -0.1798452, 0.022174192, 0.0090316521,
    )
    y < 1600.0 -> poly(
        (y - 1000.0) / 100.0, 1574.2, -556.01, 71.23472, 0.319781, -0.8503463, -0.005050998, 0.0083572073,
    )
    y < 1700.0 -> poly(y - 1600.0, 120.0, -0.9808, -0.01532, 1.0 / 7129.0)
    y < 1800.0 -> poly(y - 1700.0, 8.83, 0.1603, -0.0059285, 0.00013336, -1.0 / 1174000.0)
    y < 1860.0 -> poly(
        y - 1800.0, 13.72, -0.332447, 0.0068612, 0.0041116, -0.00037436, 0.0000121272, -0.0000001699,
        0.000000000875,
    )
    else -> poly(y - 1860.0, 7.62, 0.5737, -0.251754, 0.01680668, -0.0004473624, 1.0 / 233174.0)
}

/** -20 + 32 u^2, u = (y - 1820) / 100. */
private fun longTermParabola(y: Double): Double {
    val u = (y - 1820.0) / 100.0
    return -20.0 + 32.0 * u * u
}

/** c0 + c1 x + c2 x^2 + ... (Horner). */
private fun poly(x: Double, vararg c: Double): Double {
    var acc = 0.0
    for (k in c.indices.reversed()) acc = acc * x + c[k]
    return acc
}
