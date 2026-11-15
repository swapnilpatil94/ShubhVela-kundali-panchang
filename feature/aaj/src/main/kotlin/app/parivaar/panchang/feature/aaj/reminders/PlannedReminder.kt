package app.parivaar.panchang.feature.aaj.reminders

import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.engine.calendar.TimeRange
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * What a reminder says, as data. Structurally PII-free (M08 D2/D3): no type here has a name, a city, a place,
 * birth data or a profile id. The platform layer turns this into text from string resources.
 */
sealed interface ReminderContent {
    /** Evening before a vrat. [festivalId] is a rule id; [parana] is the fast-breaking window. */
    data class VratEve(val festivalId: String, val parana: TimeRange?) : ReminderContent

    /** Evening before a locally visible eclipse. */
    data class Grahan(val visible: TimeRange) : ReminderContent

    /** Evening before a family date. Deliberately has no fields: no names, no relation, no age. */
    data object FamilyDate : ReminderContent

    /** Brahma muhurta has begun; [sunrise] is the day's sunrise. */
    data class Brahma(val sunrise: Instant) : ReminderContent
}

/**
 * One alarm to set. [eventDate] is the local date the reminder is about (tomorrow for evening reminders,
 * the same day for Brahma). [requestCode] is stable for (kind, eventDate).
 */
data class PlannedReminder(
    val kind: ReminderKind,
    val eventDate: LocalDate,
    val triggerAt: Instant,
    val content: ReminderContent,
) {
    val requestCode: Int get() = requestCodeOf(kind, eventDate)

    override fun toString(): String = "PlannedReminder(redacted)"

    companion object {
        /** Stable across runs and devices: kind ordinal and epoch day, no hashCode of strings. */
        fun requestCodeOf(kind: ReminderKind, eventDate: LocalDate): Int =
            (kind.ordinal + 1) * REQUEST_CODE_STRIDE + (eventDate.toEpochDays() % REQUEST_CODE_STRIDE).toInt()

        private const val REQUEST_CODE_STRIDE = 1_000_000
    }
}
