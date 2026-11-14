package app.parivaar.panchang.engine.calendar.internal

import app.parivaar.panchang.engine.calendar.OccFlag
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.panchang.internal.DayWindows
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private val ONE_DAY = DatePeriod(days = 1)
private const val HARI_VASARA_PARTS = 4

/**
 * Ekadashi fast day and parana (docs/specs/M04.md section 3.7) for Ekadashi span [e] and Dwadashi span [w].
 * C+ = days whose sunrise lies in [e]; D0 = its first day, else the day containing e.start (KSHAYA_TITHI);
 * VRIDDHI_TITHI when two sunrises. SMARTA fasts on D0; VAISHNAVA on D0 + 1 (DASHAMI_VIDDHA) when [e] starts
 * after D0's arunodaya begins (sunrise - 96 min). Parana on the next day: from max(sunrise, end of the first
 * quarter of Dwadashi) to w.end; empty -> null and PARANA_UNDEFINED. Returns (fast day, parana, flags).
 */
internal fun ekadashiDay(
    e: TimeRange,
    w: TimeRange,
    t: Tradition,
    sunrise: (LocalDate) -> Instant,
    containing: (Instant) -> LocalDate,
): Triple<LocalDate, TimeRange?, Set<OccFlag>> {
    val flags = mutableSetOf<OccFlag>()
    val start = containing(e.start)
    val withSunrise = generateSequence(start) { it.plus(ONE_DAY) }.take(3)
        .filter { e.start <= sunrise(it) && sunrise(it) < e.end }.toList()
    val d0 = withSunrise.firstOrNull() ?: start.also { flags += OccFlag.KSHAYA_TITHI }
    if (withSunrise.size >= 2) flags += OccFlag.VRIDDHI_TITHI
    val viddha = t == Tradition.VAISHNAVA && e.start > sunrise(d0) - Kalas.ARUNODAYA
    if (viddha) flags += OccFlag.DASHAMI_VIDDHA
    val fast = if (viddha) d0.plus(ONE_DAY) else d0
    val paranaStart = maxOf(sunrise(fast.plus(ONE_DAY)), DayWindows.part(w.start, w.end, HARI_VASARA_PARTS, 1))
    val parana = if (w.end > paranaStart) TimeRange(paranaStart, w.end) else null
    if (parana == null) flags += OccFlag.PARANA_UNDEFINED
    return Triple(fast, parana, flags)
}
