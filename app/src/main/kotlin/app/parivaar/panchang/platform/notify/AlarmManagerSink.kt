package app.parivaar.panchang.platform.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.feature.aaj.reminders.AlarmSink
import app.parivaar.panchang.feature.aaj.reminders.PlannedReminder
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * `AlarmManager.setWindow` only: inexact, no SCHEDULE_EXACT_ALARM. Request codes are stable per (kind, date), so
 * cancel-all cancels every code in the plannable range (the manager cannot enumerate alarms).
 */
class AlarmManagerSink(
    private val context: Context,
    private val zone: TimeZone,
    private val clock: Clock = Clock.System,
) : AlarmSink {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    private fun intent(code: Int, payload: ReminderPayload?, create: Boolean): PendingIntent? {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION)
        payload?.toMap()?.forEach { (k, v) -> if (v is Long) i.putExtra(k, v) else i.putExtra(k, v.toString()) }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, code, i, flags)
    }

    override fun setWindow(requestCode: Int, triggerAt: kotlin.time.Instant, window: Duration) =
        set(requestCode, triggerAt, window, null)

    override fun setReminder(reminder: PlannedReminder, window: Duration) =
        set(reminder.requestCode, reminder.triggerAt, window, ReminderPayload.of(reminder, zone.id))

    private fun set(code: Int, at: kotlin.time.Instant, window: Duration, payload: ReminderPayload?) {
        val pi = intent(code, payload, create = true) ?: return
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), window.inWholeMilliseconds, pi)
    }

    override fun cancelAll() {
        val today = clock.now().toLocalDateTime(zone).date
        for (kind in ReminderKind.entries) {
            for (offset in CANCEL_FROM..CANCEL_TO) {
                val d: LocalDate = today.plus(DatePeriod(days = offset))
                intent(PlannedReminder.requestCodeOf(kind, d), null, create = false)?.let {
                    alarms.cancel(it)
                    it.cancel()
                }
            }
        }
    }

    private companion object {
        const val CANCEL_FROM = -3
        const val CANCEL_TO = 17
    }
}
