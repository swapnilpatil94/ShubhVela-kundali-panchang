package app.parivaar.panchang.feature.aaj.reminders

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Port over `AlarmManager`. Only inexact windows exist: there is deliberately no exact-alarm method. */
interface AlarmSink {
    /** Fire within [window] starting at [triggerAt]. Replaces any alarm with the same [requestCode]. */
    fun setWindow(requestCode: Int, triggerAt: Instant, window: Duration)

    /** Set one planned reminder. Platform sinks override this to attach the (PII-free) content to the alarm. */
    fun setReminder(reminder: PlannedReminder, window: Duration) =
        setWindow(reminder.requestCode, reminder.triggerAt, window)

    /** Cancel every alarm this app set through the sink. */
    fun cancelAll()
}

/** The length of every reminder window (M08 L2). */
val REMINDER_WINDOW: Duration = 2.minutes

/** Cancel everything, then set [plan]. Call on every re-plan trigger. */
fun AlarmSink.replace(plan: List<PlannedReminder>) {
    cancelAll()
    for (r in plan) setReminder(r, REMINDER_WINDOW)
}
