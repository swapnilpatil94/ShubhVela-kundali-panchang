package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.RiseSet
import app.parivaar.panchang.engine.astro.RiseSetEvent
import app.parivaar.panchang.engine.astro.SelectingEphemeris
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.panchang.internal.DayWindows
import app.parivaar.panchang.engine.panchang.internal.SunDay
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Panchang days (docs/specs/M03.md sections 3.2 and 3.3): the civil date at the caller's UTC offset maps to
 * the day [sunrise, nextSunrise) after its local midnight; limbs from [limbs], windows from [tables].
 *
 * Results depend on the date, offset, place and settings only (never on earlier calls): the caches of
 * [limbs] and of the sun windows change no bit. Not thread-safe.
 */
class PanchangCalculator(
    private val eph: Ephemeris,
    private val tables: PanchangTables,
    private val time: TimeTables = TimeTables.DEFAULT,
) {
    /** The limb solver used for the days; use it for other kalas (M04). */
    val limbs: LimbSolver = LimbSolver(eph, time)

    private val riseSet = RiseSet(eph, time)

    private data class SunKey(val place: Place, val settings: CalcSettings, val windowStart: Instant)

    private val sunMemo = LinkedHashMap<SunKey, List<RiseSetEvent>>()

    /**
     * The panchang day of civil [date] at [place]; [utcOffsetSeconds] is the offset in force at local noon,
     * in [-43,200, 50,400] (else [IllegalArgumentException]). Throws [PanchangUnavailableException] (NO_SUN_DAY)
     * when no sunrise < sunset < nextSunrise exists.
     */
    fun day(date: LocalDate, utcOffsetSeconds: Int, place: Place, s: CalcSettings = CalcSettings()): PanchangDay {
        requireUtcOffset(utcOffsetSeconds)
        val midnight = Instant.fromEpochSeconds(date.toEpochDays() * SECONDS_PER_DAY - utcOffsetSeconds)
        val sun = SunDay.of(midnight, sunEvents(place, midnight, s), sunEvents(place, midnight + DAY, s))
        val vaar = Vaar.of(date)
        val limbSpans = LimbKind.entries.associateWith { k ->
            limbs.spans(k, sun.sunrise, sun.nextSunrise, s).map { span ->
                val hasSunrise = sun.sunrise in span
                val hasNext = sun.nextSunrise in span
                if (hasSunrise || hasNext) span.copy(adhika = hasSunrise && hasNext) else span.copy(kshaya = true)
            }
        }
        val windows = DayWindows(sun, vaar, s, tables).compute(limbSpans) {
            limbs.spanAt(LimbKind.MOON_RASHI, it, s)
        }
        return PanchangDay(
            date = date,
            utcOffsetSeconds = utcOffsetSeconds,
            vaar = vaar,
            sunrise = sun.sunrise,
            sunset = sun.sunset,
            nextSunrise = sun.nextSunrise,
            flags = sun.flags,
            backend = (eph as? SelectingEphemeris)?.backendFor(TimeScales.utcToTt(sun.sunrise, time)),
            limbs = limbSpans,
            windows = windows.windows,
            unavailable = windows.unavailable,
            shool = WeekdayTables.shool(vaar),
        )
    }

    /**
     * [count] consecutive days from [first]; [utcOffsetSeconds] gives each date's offset. With an unchanged
     * offset, nextSunrise of one day is bit-equal to sunrise of the next.
     */
    fun days(
        first: LocalDate,
        count: Int,
        place: Place,
        s: CalcSettings = CalcSettings(),
        utcOffsetSeconds: (LocalDate) -> Int,
    ): List<PanchangDay> {
        require(count >= 0) { "count must not be negative" }
        return (0 until count).map { i ->
            val date = first.plus(DatePeriod(days = i))
            day(date, utcOffsetSeconds(date), place, s)
        }
    }

    /** `sunWithFallback` of one 24 h window; the last few windows are kept (a day's second is the next's first). */
    private fun sunEvents(place: Place, windowStart: Instant, s: CalcSettings): List<RiseSetEvent> {
        val key = SunKey(place, s, windowStart)
        sunMemo[key]?.let { return it }
        val events = riseSet.sunWithFallback(place, windowStart, s)
        if (sunMemo.size >= SUN_MEMO_SIZE) sunMemo.remove(sunMemo.keys.first())
        sunMemo[key] = events
        return events
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
        const val SUN_MEMO_SIZE = 4
        val DAY = 24.hours
    }
}
