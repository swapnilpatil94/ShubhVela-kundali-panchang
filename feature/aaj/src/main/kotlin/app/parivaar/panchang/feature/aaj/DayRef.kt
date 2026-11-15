package app.parivaar.panchang.feature.aaj

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Where an instant's civil date (in the place's offset) falls relative to the displayed date. */
sealed interface DayRef {
    data object Same : DayRef
    data object Next : DayRef
    data object Previous : DayRef

    /** Two or more days away: the caller prints this explicit date. */
    data class Other(val date: LocalDate) : DayRef
}

private const val SECONDS_PER_DAY = 86_400L

/** The civil date of [at] at [offsetSeconds]. */
fun civilDate(at: Instant, offsetSeconds: Int): LocalDate =
    LocalDate.fromEpochDays(Math.floorDiv(at.epochSeconds + offsetSeconds, SECONDS_PER_DAY).toInt())

/**
 * Labels for a range [from]..[to]: when both ends fall on the same non-displayed date the label is printed once, at
 * the end (start becomes [DayRef.Same], i.e. plain); when the dates differ each end keeps its own label.
 */
fun rangeRefs(from: Instant, to: Instant, offsetSeconds: Int, shown: LocalDate): Pair<DayRef, DayRef> {
    val a = dayRef(from, offsetSeconds, shown)
    val b = dayRef(to, offsetSeconds, shown)
    return if (a == b) DayRef.Same to b else a to b
}

/** How the header names a "next good time" that starts in the next window of good times. */
enum class NextPhrase { LATER_TODAY, TOMORROW, DATED }

/** Before sunrise the "tomorrow" start can still be on the shown date; then it must not say tomorrow. */
fun nextPhrase(ref: DayRef): NextPhrase = when (ref) {
    DayRef.Same -> NextPhrase.LATER_TODAY
    DayRef.Next -> NextPhrase.TOMORROW
    else -> NextPhrase.DATED
}

/** Same date: no label; +1: next day; -1: previous day; otherwise the explicit date. */
fun dayRef(at: Instant, offsetSeconds: Int, shown: LocalDate): DayRef {
    val d = civilDate(at, offsetSeconds)
    return when (d.toEpochDays().toLong() - shown.toEpochDays()) {
        0L -> DayRef.Same
        1L -> DayRef.Next
        -1L -> DayRef.Previous
        else -> DayRef.Other(d)
    }
}
