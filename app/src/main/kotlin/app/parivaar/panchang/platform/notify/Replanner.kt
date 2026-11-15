package app.parivaar.panchang.platform.notify

import app.parivaar.panchang.feature.aaj.reminders.AlarmSink
import app.parivaar.panchang.feature.aaj.reminders.ReminderPlanner
import app.parivaar.panchang.feature.aaj.reminders.replace
import kotlin.time.Instant

/**
 * Re-plan: load inputs, plan, cancel all, set windows. Triggered at app start, on settings or place change, by the
 * daily job, and by boot, time and time-zone changes. With no categories enabled every alarm is cancelled.
 */
class Replanner(
    private val now: () -> Instant,
    private val source: ReminderPlanSource,
    private val sink: AlarmSink,
) {
    /** Returns the number of alarms set, or -1 when the source had no input (alarms untouched). */
    fun replan(): Int {
        val t = now()
        val input = source.load(t) ?: return -1
        val plan =
            ReminderPlanner.plan(
                t, input.zone, input.days, input.festivals, input.vratIds, input.eclipses, input.familyDates,
                input.enabled,
            )
        sink.replace(plan)
        return plan.size
    }
}
