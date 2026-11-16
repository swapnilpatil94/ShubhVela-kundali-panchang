package app.parivaar.panchang.feature.horoscope

import app.parivaar.panchang.content.horoscope.Line
import app.parivaar.panchang.content.horoscope.NatalInputs
import app.parivaar.panchang.content.horoscope.Period
import app.parivaar.panchang.content.horoscope.PeriodWindow
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Instant

/** Sign basis picker (A7): Moon sign, sidereal Sun sign, or lagna. */
enum class Basis { MOON, SUN, LAGNA }

/**
 * The active profile's chart inputs for rashifal. Birth-derived: memory only, never logged, routed or stored.
 * [lagna] is null for a noon chart (birth time unknown); [dasha] is the current maha and antar lords.
 */
data class NatalSnapshot(
    val moon: Rashi,
    val sun: Rashi,
    val lagna: Rashi?,
    val moonNakshatra: Int,
    val dasha: List<Graha>,
) {
    fun sign(b: Basis): Rashi? = when (b) {
        Basis.MOON -> moon
        Basis.SUN -> sun
        Basis.LAGNA -> lagna
    }

    /** The rules' natal inputs; null for a noon chart (no lagna, so no personal reading). */
    fun toInputs(): NatalInputs? = lagna?.let { NatalInputs(it, moon, moonNakshatra, dasha) }

    override fun toString(): String = "NatalSnapshot(redacted)"
}

/**
 * Rashifal engine port. Blocking: call off the main thread (the app wraps the shared engine under its lock).
 * [variants] gives the variant count of a `gochar.<g>.h<h>` prefix in the user-language pack (en when falling back).
 */
interface RashifalEngine {
    /** H1 lines for [sign] over the [period] window containing [now]; null without a home place. */
    fun h1(sign: Rashi, period: Period, now: Instant, variants: (String) -> Int): List<Line>?

    /** H2 personal daily lines for [natal] (lagna known); null without a home place. */
    fun h2(natal: NatalSnapshot, now: Instant, variants: (String) -> Int): List<Line>?
}

/** Window arithmetic from panchang day starts (sunrise per CalcSettings, with the engine's no-sunrise fallback). */
object PeriodWindows {
    private const val WEEK_DAYS = 7
    private const val MONTHS = 12

    /** The panchang date of [now]: the civil date whose day start is the latest one not after [now]. */
    fun panchangDate(now: Instant, civilToday: LocalDate, dayStart: (LocalDate) -> Instant): LocalDate =
        if (now < dayStart(civilToday)) civilToday.minus(DatePeriod(days = 1)) else civilToday

    /**
     * DAY: day start to next day start, index = epochDay. WEEK: Monday (V2) to +7 days, index = floorDiv(Monday
     * epochDay, 7). MONTH: 1st to 1st of next month, index = year*12 + month - 1. YEAR: 1 Jan to 1 Jan (V3), index =
     * year. Every boundary is a panchang day start.
     */
    fun window(period: Period, date: LocalDate, dayStart: (LocalDate) -> Instant): PeriodWindow {
        val (first, next, index) = when (period) {
            Period.DAY -> Triple(date, date.plus(DatePeriod(days = 1)), date.toEpochDays().toLong())
            Period.WEEK -> {
                val monday = date.minus(DatePeriod(days = date.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))
                val index = Math.floorDiv(monday.toEpochDays().toLong(), WEEK_DAYS.toLong())
                Triple(monday, monday.plus(DatePeriod(days = WEEK_DAYS)), index)
            }
            Period.MONTH -> {
                val first = LocalDate(date.year, date.month, 1)
                Triple(first, first.plus(DatePeriod(months = 1)), date.year.toLong() * MONTHS + date.month.ordinal)
            }
            Period.YEAR -> {
                val first = LocalDate(date.year, 1, 1)
                Triple(first, LocalDate(date.year + 1, 1, 1), date.year.toLong())
            }
        }
        return PeriodWindow(dayStart(first), dayStart(next), index)
    }
}
