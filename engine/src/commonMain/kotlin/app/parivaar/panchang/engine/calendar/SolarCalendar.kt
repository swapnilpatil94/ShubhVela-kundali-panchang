package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.internal.DayLookup
import app.parivaar.panchang.engine.calendar.internal.skipUnavailable
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/**
 * The four regional solar calendars (docs/specs/M04.md section 3.3): a month starts on the panchang day its
 * sankranti is kept on by the region's rule (TAMIL before sunset, MALAYALAM before 3/5 of daylight, BENGALI
 * before the night midpoint of the next day, ODIA the civil date at the caller offset). Results never hold the
 * place. Not thread-safe.
 */
class SolarCalendar(private val lunar: LunarCalendar) {
    /**
     * The month starts of [r] whose date is in [first]..[last], in date order; [utcOffsetSeconds] gives each
     * date's offset (range-checked).
     */
    fun monthStarts(
        r: SolarRegion,
        first: LocalDate,
        last: LocalDate,
        place: Place,
        s: CalcSettings = CalcSettings(),
        utcOffsetSeconds: (LocalDate) -> Int,
    ): List<SolarMonthStart> {
        val lookup = DayLookup.of(lunar.riseSet, place, s, utcOffsetSeconds)
        return starts(r, first, last, sankrantisFor(first, last, lookup, s), lookup)
    }

    /** The solar date of [date] in [r]: the latest month start <= [date]. */
    fun dateOf(
        r: SolarRegion,
        date: LocalDate,
        place: Place,
        s: CalcSettings = CalcSettings(),
        utcOffsetSeconds: (LocalDate) -> Int,
    ): SolarDate {
        val lookup = DayLookup.of(lunar.riseSet, place, s, utcOffsetSeconds)
        val first = date.minus(MONTH_BACK)
        return dateOf(date, starts(r, first, date, sankrantisFor(first, date, lookup, s), lookup))
    }

    /** Sankrantis that can start a month dated [first]..[last] (dates lie 1 day before to 2 days after). */
    internal fun sankrantisFor(
        first: LocalDate,
        last: LocalDate,
        lookup: DayLookup,
        s: CalcSettings,
    ): List<Sankranti> {
        require(first <= last) { "first must not be after last" }
        return lunar.sankrantis(lookup.midnight(first) - MARGIN, lookup.midnight(last) + MARGIN + 1.days, s)
    }

    /**
     * Month starts of [r] from [sankrantis] dated [first]..[last]; [into] keeps only that month; [skip] drops a
     * sankranti whose day has no sun day instead of throwing.
     */
    internal fun starts(
        r: SolarRegion,
        first: LocalDate,
        last: LocalDate,
        sankrantis: List<Sankranti>,
        lookup: DayLookup,
        into: Set<Rashi>? = null,
        skip: Boolean = false,
    ): List<SolarMonthStart> = sankrantis.filter { into == null || it.into in into }.mapNotNull { sk ->
        val rule = ruleOf(r)
        val kept = if (skip) skipUnavailable { lookup.sankrantiDate(sk, rule) } else lookup.sankrantiDate(sk, rule)
        if (kept == null || kept.date < first || kept.date > last) {
            null
        } else {
            SolarMonthStart(r, sk.into, kept.date, sk, kept.cutoff)
        }
    }.sortedBy { it.date }

    internal companion object {
        /** Before any month start of a date (months last at most 32 days). */
        val MONTH_BACK = DatePeriod(days = 33)
        private val MARGIN = 3.days

        /** The sankranti rule of [r]. */
        fun ruleOf(r: SolarRegion): SankrantiDay = when (r) {
            SolarRegion.TAMIL -> SankrantiDay.BEFORE_SUNSET
            SolarRegion.MALAYALAM -> SankrantiDay.BEFORE_THREE_FIFTHS
            SolarRegion.BENGALI -> SankrantiDay.BEFORE_MIDNIGHT_NEXT
            SolarRegion.ODIA -> SankrantiDay.CIVIL
        }

        /** The solar date of [date] from [starts] (date order); throws [IllegalStateException] if none is <= it. */
        fun dateOf(date: LocalDate, starts: List<SolarMonthStart>): SolarDate {
            val start = checkNotNull(starts.lastOrNull { it.date <= date }) { "no solar month start found" }
            return SolarDate(start.region, start.month, start.date.daysUntil(date) + 1, start.date)
        }
    }
}
