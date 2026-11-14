package app.parivaar.panchang.engine.calendar.internal

import app.parivaar.panchang.engine.calendar.Kala
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.engine.panchang.internal.DayWindows
import app.parivaar.panchang.engine.panchang.internal.SunDay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** True iff start <= [t] < end. */
internal operator fun TimeRange.contains(t: Instant): Boolean = start <= t && t < end

/**
 * Kala windows of a panchang day and their coverage of a limb span (docs/specs/M04.md section 3.4).
 * Intervals are [start, end); SUNRISE and MOONRISE are points [p, p].
 */
internal object Kalas {
    private const val PRADOSH_PARTS = 15
    private const val PRADOSH_END = 3
    private const val NISHITA_START = 7
    private const val NISHITA_END = 8
    private const val DAY_PARTS = 5
    private const val MADHYAHNA_START = 2
    private const val APARAHNA_START = 3
    private const val APARAHNA_END = 4
    private const val NANOS = 1_000_000_000L

    /** ARUNODAYA length before sunrise (MASTER: 96 minutes). */
    val ARUNODAYA = 5_760.seconds

    /** True for the point kalas SUNRISE and MOONRISE. */
    fun isPoint(k: Kala): Boolean = k == Kala.SUNRISE || k == Kala.MOONRISE

    /** The window of [k] on the day [sd]; [moonrise] is that day's first moonrise (null: none, window null). */
    fun window(k: Kala, sd: SunDay, moonrise: () -> Instant?): TimeRange? {
        val part = DayWindows::part
        return when (k) {
            Kala.SUNRISE -> TimeRange(sd.sunrise, sd.sunrise)
            Kala.ARUNODAYA -> TimeRange(sd.sunrise - ARUNODAYA, sd.sunrise)
            Kala.PRADOSH -> TimeRange(sd.sunset, part(sd.sunset, sd.nextSunrise, PRADOSH_PARTS, PRADOSH_END))
            Kala.NISHITA -> TimeRange(
                part(sd.sunset, sd.nextSunrise, PRADOSH_PARTS, NISHITA_START),
                part(sd.sunset, sd.nextSunrise, PRADOSH_PARTS, NISHITA_END),
            )
            Kala.MADHYAHNA -> TimeRange(
                part(sd.sunrise, sd.sunset, DAY_PARTS, MADHYAHNA_START),
                part(sd.sunrise, sd.sunset, DAY_PARTS, APARAHNA_START),
            )
            Kala.APARAHNA -> TimeRange(
                part(sd.sunrise, sd.sunset, DAY_PARTS, APARAHNA_START),
                part(sd.sunrise, sd.sunset, DAY_PARTS, APARAHNA_END),
            )
            Kala.MOONRISE -> moonrise()?.let { TimeRange(it, it) }
            Kala.AHORATRA -> TimeRange(sd.sunrise, sd.nextSunrise)
        }
    }

    /**
     * Coverage of span [x] (start <= t < end) by window [w] of kind [k]: a point kala gives 1 if the point is
     * in [x], else 0; an interval gives the overlap in nanoseconds; a null window gives 0.
     */
    fun coverage(k: Kala, w: TimeRange?, x: TimeRange): Long = when {
        w == null -> 0L
        isPoint(k) -> if (x.start <= w.start && w.start < x.end) 1L else 0L
        else -> overlap(w, x)?.let { ns(it.end) - ns(it.start) } ?: 0L
    }

    /** [a] intersect [b] when it has positive length, else null. */
    fun overlap(a: TimeRange, b: TimeRange): TimeRange? {
        val s = maxOf(a.start, b.start)
        val e = minOf(a.end, b.end)
        return if (s < e) TimeRange(s, e) else null
    }

    /** True iff window [w] of kind [k] touches [span]: the point is in it, or a positive overlap. */
    fun touches(k: Kala, w: TimeRange?, span: TimeRange): Boolean = coverage(k, w, span) > 0L

    private fun ns(t: Instant): Long = t.epochSeconds * NANOS + t.nanosecondsOfSecond
}

/**
 * The per-call evaluation context of the festival engine: days of one place ([days]), its moonrises, and limb
 * spans as time ranges. Memoises kala windows. Holds the place indirectly: never keep it in a result.
 *
 * @property vishti the VISHTI karana spans overlapping [from, to)
 * @property nakshatraSpans the spans of nakshatra n overlapping [from, to)
 */
internal class EvalContext(
    val days: DayLookup,
    private val moonriseOf: (SunDay) -> Instant?,
    val vishti: (from: Instant, to: Instant) -> List<TimeRange>,
    val nakshatraSpans: (n: Int, from: Instant, to: Instant) -> List<TimeRange>,
) {
    private data class Key(val date: LocalDate, val kala: Kala)

    private val windows = HashMap<Key, TimeRange?>()
    private val moonrises = HashMap<LocalDate, Instant?>()

    /** The window of [k] on [date] (null: MOONRISE with no moonrise that day). */
    fun kala(date: LocalDate, k: Kala): TimeRange? = windows.getOrPut(Key(date, k)) {
        val sd = days.sunDay(date)
        Kalas.window(k, sd) { moonrises.getOrPut(date) { moonriseOf(sd) } }
    }
}
