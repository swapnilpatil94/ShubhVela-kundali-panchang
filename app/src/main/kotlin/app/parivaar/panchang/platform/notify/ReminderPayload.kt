package app.parivaar.panchang.platform.notify

import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.feature.aaj.reminders.PlannedReminder
import app.parivaar.panchang.feature.aaj.reminders.ReminderContent
import kotlin.time.Instant

/**
 * The alarm's extras: kind, rule id, instants and the zone id used to render times. Nothing personal: no name,
 * city, coordinates or birth data (M08 D2/D3).
 */
data class ReminderPayload(val kind: ReminderKind, val zoneId: String, val content: ReminderContent) {
    fun toMap(): Map<String, Any> =
        buildMap {
            put(KIND, kind.name)
            put(ZONE, zoneId)
            when (content) {
                is ReminderContent.VratEve -> {
                    put(ID, content.festivalId)
                    content.parana?.let { put(A, it.start.epochSeconds); put(B, it.end.epochSeconds) }
                }
                is ReminderContent.Grahan -> {
                    put(A, content.visible.start.epochSeconds)
                    put(B, content.visible.end.epochSeconds)
                }
                is ReminderContent.Brahma -> put(A, content.sunrise.epochSeconds)
                ReminderContent.FamilyDate -> Unit
            }
        }

    companion object {
        const val KIND = "k"
        const val ZONE = "z"
        const val ID = "i"
        const val A = "a"
        const val B = "b"

        fun of(r: PlannedReminder, zoneId: String) = ReminderPayload(r.kind, zoneId, r.content)

        @Suppress("ReturnCount")
        fun fromMap(m: Map<String, Any?>): ReminderPayload? {
            val kind = ReminderKind.entries.firstOrNull { it.name == m[KIND] } ?: return null
            val zone = m[ZONE] as? String ?: return null
            val a = (m[A] as? Long)?.let(Instant::fromEpochSeconds)
            val b = (m[B] as? Long)?.let(Instant::fromEpochSeconds)
            val content =
                when (kind) {
                    ReminderKind.VRAT_EVE ->
                        ReminderContent.VratEve(
                            m[ID] as? String ?: return null,
                            if (a != null && b != null) TimeRange(a, b) else null,
                        )
                    ReminderKind.GRAHAN -> ReminderContent.Grahan(TimeRange(a ?: return null, b ?: return null))
                    ReminderKind.FAMILY_DATES -> ReminderContent.FamilyDate
                    ReminderKind.SUNRISE_BRAHMA -> ReminderContent.Brahma(a ?: return null)
                }
            return ReminderPayload(kind, zone, content)
        }
    }
}
