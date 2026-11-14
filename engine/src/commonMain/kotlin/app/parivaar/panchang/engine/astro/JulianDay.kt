package app.parivaar.panchang.engine.astro

import kotlin.jvm.JvmInline

/**
 * A Julian Day number as one Double (resolution about 40 microseconds near the present). The time scale
 * (UTC, UT1, TT or TDB) is given by the API that produces or consumes the value.
 */
@JvmInline
value class JulianDay(val value: Double) : Comparable<JulianDay> {
    override fun compareTo(other: JulianDay): Int = value.compareTo(other.value)

    companion object {
        /** JD of the epoch J2000.0 (2000-01-01 12:00 TT). */
        const val J2000: Double = 2451545.0

        /** Days per Julian century. */
        const val DAYS_PER_CENTURY: Double = 36525.0
    }
}

/** Julian centuries from J2000.0: T(x) = (x - 2451545.0) / 36525. */
internal fun JulianDay.centuriesSinceJ2000(): Double = (value - JulianDay.J2000) / JulianDay.DAYS_PER_CENTURY
