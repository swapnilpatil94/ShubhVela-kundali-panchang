@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.feature.aaj.reminders.FamilyDateSource
import app.parivaar.panchang.feature.aaj.reminders.ReminderPlanner
import app.parivaar.panchang.platform.notify.PlanInput
import app.parivaar.panchang.platform.notify.ReminderPlanSource
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.time.Instant

/** The real [ReminderPlanSource]: settings, the home place and the engine. Blocking; call off the main thread. */
class EngineReminderSource(
    private val access: EngineAccess,
    private val family: FamilyDateSource = FamilyDateSource.None,
) : ReminderPlanSource {
    @Suppress("ReturnCount")
    override fun load(now: Instant): PlanInput? {
        val a = access.active() ?: return null
        val enabled = a.settings.reminders
        val today = access.today(a, now)
        val last = today.plus(DatePeriod(days = ReminderPlanner.HORIZON_DAYS))
        if (enabled.isEmpty()) return PlanInput(a.zone, emptyList(), emptyList(), emptySet(), emptyList(), emptyList(), enabled)
        return PlanInput(
            zone = a.zone,
            days = access.days(a, today, ReminderPlanner.HORIZON_DAYS),
            festivals = access.festivals(a, today, last),
            vratIds = access.vratIds,
            eclipses = access.eclipses(a, today, last),
            familyDates = family.dates(today, last),
            enabled = enabled,
        )
    }
}
