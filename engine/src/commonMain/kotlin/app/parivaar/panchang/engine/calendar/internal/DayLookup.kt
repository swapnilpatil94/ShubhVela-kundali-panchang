package app.parivaar.panchang.engine.calendar.internal

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.RiseSet
import app.parivaar.panchang.engine.astro.RiseSetEvent
import app.parivaar.panchang.engine.calendar.Sankranti
import app.parivaar.panchang.engine.calendar.SankrantiDay
import app.parivaar.panchang.engine.panchang.PanchangUnavailableException
import app.parivaar.panchang.engine.panchang.internal.DayWindows
import app.parivaar.panchang.engine.panchang.internal.SunDay
import app.parivaar.panchang.engine.panchang.requireUtcOffset
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * [block], or null when it needs a panchang day that does not exist (NO_SUN_DAY): one evaluation unit is
 * dropped instead of the whole run.
 */
@Suppress("SwallowedException") // the code is the whole information; the unit is skipped by design
internal inline fun <T> skipUnavailable(block: () -> T?): T? =
    try {
        block()
    } catch (e: PanchangUnavailableException) {
        null
    }

/** A day's civil date and the cut-off instant its sankranti rule compared with (null for CIVIL). */
internal data class SankrantiDate(val date: LocalDate, val cutoff: Instant?) {
    override fun toString(): String = "SankrantiDate(redacted)"
}

/**
 * Panchang days of one place, settings and offset function (docs/specs/M04.md section 3.3), memoised for one
 * call (it holds the place: never keep it in a result). [sunDayOf] computes the sun day of a civil date;
 * [offsetOf] gives each date's UTC offset in seconds (range-checked). Not thread-safe.
 */
internal class DayLookup(
    private val offsetOf: (LocalDate) -> Int,
    private val sunDayOf: (date: LocalDate, midnight: Instant) -> SunDay,
) {
    private val days = HashMap<LocalDate, SunDay>()

    /** The UTC offset of [date]; throws [IllegalArgumentException] (no value) when out of range. */
    fun offset(date: LocalDate): Int = offsetOf(date).also { requireUtcOffset(it) }

    /** Local midnight of [date] at its offset. */
    fun midnight(date: LocalDate): Instant =
        Instant.fromEpochSeconds(date.toEpochDays() * SECONDS_PER_DAY - offset(date))

    /** The M03 sun day of [date] (same windows as `PanchangCalculator`). */
    fun sunDay(date: LocalDate): SunDay = days.getOrPut(date) { sunDayOf(date, midnight(date)) }

    /**
     * The date d with sunrise(d) <= [x] < nextSunrise(d), from the civil date of [x] stepping one day at a time.
     * In a gap between nextSunrise(d) and sunrise(d + 1) (an offset change) the earlier day is returned.
     */
    fun containing(x: Instant): LocalDate {
        var d = civilDate(x)
        var lastStep = 0
        repeat(MAX_STEPS) {
            val sd = sunDay(d)
            val step = when {
                x < sd.sunrise -> -1
                x >= sd.nextSunrise -> 1
                else -> return d
            }
            if (step == -lastStep) return if (step < 0) d.minus(ONE_DAY) else d
            lastStep = step
            d = if (step < 0) d.minus(ONE_DAY) else d.plus(ONE_DAY)
        }
        throw IllegalStateException("no panchang day contains the instant")
    }

    /** The civil date of [x] at the offset of its own date (CIVIL rule of section 3.3). */
    fun civilDate(x: Instant): LocalDate {
        val d0 = utcDate(x)
        val off0 = offset(d0)
        val d = utcDate(x + off0.seconds)
        val off = offset(d)
        return if (off == off0) d else utcDate(x + off.seconds)
    }

    /** The date a sankranti is kept on under [rule] (section 3.3) and the cut-off it was compared with. */
    fun sankrantiDate(s: Sankranti, rule: SankrantiDay): SankrantiDate {
        if (rule == SankrantiDay.CIVIL) return SankrantiDate(civilDate(s.at), null)
        val p = containing(s.at)
        val sd = sunDay(p)
        return when (rule) {
            SankrantiDay.BEFORE_SUNSET -> beforeCutoff(p, s.at, sd.sunset, 0)
            SankrantiDay.BEFORE_THREE_FIFTHS ->
                beforeCutoff(p, s.at, DayWindows.part(sd.sunrise, sd.sunset, FIFTHS, THREE), 0)
            SankrantiDay.BEFORE_MIDNIGHT_NEXT ->
                beforeCutoff(p, s.at, DayWindows.part(sd.sunset, sd.nextSunrise, 2, 1), 1)
            SankrantiDay.CIVIL -> error("unreachable")
        }
    }

    private fun beforeCutoff(p: LocalDate, at: Instant, cutoff: Instant, base: Int): SankrantiDate =
        SankrantiDate(p.plus(DatePeriod(days = base + if (at < cutoff) 0 else 1)), cutoff)

    companion object {
        private const val SECONDS_PER_DAY = 86_400L
        private const val MAX_STEPS = 8
        private const val FIFTHS = 5
        private const val THREE = 3
        private val ONE_DAY = DatePeriod(days = 1)
        private val WINDOW = 24.hours

        /** Real sun days of [place] via [riseSet] (`sunWithFallback` of [midnight, +24 h] and the next window). */
        fun of(riseSet: RiseSet, place: Place, s: CalcSettings, offsetOf: (LocalDate) -> Int): DayLookup {
            val windows = HashMap<Instant, List<RiseSetEvent>>()
            fun events(start: Instant) = windows.getOrPut(start) { riseSet.sunWithFallback(place, start, s) }
            return DayLookup(offsetOf) { _, midnight ->
                SunDay.of(midnight, events(midnight), events(midnight + WINDOW))
            }
        }

        /** The UTC date of [x]. */
        fun utcDate(x: Instant): LocalDate =
            LocalDate.fromEpochDays(x.epochSeconds.floorDiv(SECONDS_PER_DAY).toInt())
    }
}
