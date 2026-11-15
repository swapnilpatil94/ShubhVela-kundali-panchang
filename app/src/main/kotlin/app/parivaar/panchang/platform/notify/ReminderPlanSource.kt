package app.parivaar.panchang.platform.notify

import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.LocalEclipse
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.core.model.settings.ReminderKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Everything the pure planner needs for one re-plan, computed by the engine for the active place. */
data class PlanInput(
    val zone: TimeZone,
    val days: List<PanchangDay>,
    val festivals: List<FestivalOccurrence>,
    val vratIds: Set<String>,
    val eclipses: List<LocalEclipse>,
    val familyDates: List<LocalDate>,
    val enabled: Set<ReminderKind>,
)

/**
 * Port to settings, place and engine. Blocking: call off the main thread. Returns null when there is nothing to
 * plan from (no home place yet, vault not open): the re-plan then leaves existing alarms alone.
 */
fun interface ReminderPlanSource {
    fun load(now: Instant): PlanInput?
}

/** Process-wide holder, set once by the app shell when the engine graph exists. Null means no-op. */
object ReminderRuntime {
    @Volatile var source: ReminderPlanSource? = null
}
