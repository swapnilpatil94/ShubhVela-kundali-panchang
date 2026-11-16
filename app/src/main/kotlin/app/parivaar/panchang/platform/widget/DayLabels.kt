package app.parivaar.panchang.platform.widget

import app.parivaar.panchang.feature.aaj.DayRef
import app.parivaar.panchang.feature.aaj.PlaceClock
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Pure: appends "(next day)", "(previous day)" or "(Nov 17)" to a formatted time, using [DayRef]. */
internal fun timeWithDayRef(
    time: String,
    ref: DayRef,
    nextDay: String,
    previousDay: String,
    dateLabel: (LocalDate) -> String,
): String =
    when (ref) {
        DayRef.Same -> time
        DayRef.Next -> "$time $nextDay"
        DayRef.Previous -> "$time $previousDay"
        is DayRef.Other -> "$time ${dateLabel(ref.date)}"
    }

/** Pure: start and end texts of a range; one shared label at the end when both fall on the same other date. */
@Suppress("LongParameterList")
internal fun rangeWithDayRef(
    from: Instant,
    to: Instant,
    placeClock: PlaceClock,
    shown: LocalDate,
    clock: (Instant) -> String,
    nextDay: String,
    previousDay: String,
    dateLabel: (LocalDate) -> String,
): Pair<String, String> {
    val (a, b) = placeClock.rangeRefs(from, to, shown)
    return timeWithDayRef(clock(from), a, nextDay, previousDay, dateLabel) to
        timeWithDayRef(clock(to), b, nextDay, previousDay, dateLabel)
}
