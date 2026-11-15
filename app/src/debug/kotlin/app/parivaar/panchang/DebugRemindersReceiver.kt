@file:Suppress("MaxLineLength")

package app.parivaar.panchang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.feature.aaj.reminders.ReminderContent
import app.parivaar.panchang.feature.aaj.reminders.ReminderPlanner
import app.parivaar.panchang.platform.notify.Poster
import app.parivaar.panchang.platform.notify.ReminderPayload
import app.parivaar.panchang.platform.notify.ReminderRuntime
import app.parivaar.panchang.platform.widget.WidgetRefresher
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Debug only. `am broadcast -n app.parivaar.panchang/.DebugRemindersReceiver --es action plan|post|widget [--es kind VRAT_EVE]`.
 * Logs (tag PpDebug) counts and kinds only: never places, names, dates of birth or coordinates.
 */
class DebugRemindersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action") ?: return
        val kind = ReminderKind.entries.firstOrNull { it.name == intent.getStringExtra("kind") } ?: ReminderKind.VRAT_EVE
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                when (action) {
                    "plan" -> plan()
                    "post" -> post(app, kind)
                    "widget" -> WidgetRefresher.refresh(app).also { Log.i(TAG, "widgets refreshed") }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun plan() {
        val now = Clock.System.now()
        val input = ReminderRuntime.source?.load(now)
        if (input == null) {
            Log.i(TAG, "plan: no input (vault not open or no home place)")
            return
        }
        val plan = ReminderPlanner.plan(now, input.zone, input.days, input.festivals, input.vratIds, input.eclipses, input.familyDates, input.enabled)
        Log.i(TAG, "plan: enabled=${input.enabled.sorted()} count=${plan.size} byKind=${plan.groupingBy { it.kind }.eachCount()}")
        plan.forEach { Log.i(TAG, "alarm kind=${it.kind} inHours=${(it.triggerAt - now).inWholeHours} code=${it.requestCode}") }
    }

    private fun post(app: Context, kind: ReminderKind) {
        val now = Clock.System.now()
        val content: ReminderContent =
            when (kind) {
                ReminderKind.VRAT_EVE -> ReminderContent.VratEve("ekadashi_test", null)
                ReminderKind.GRAHAN -> ReminderContent.Grahan(TimeRange(now, now))
                ReminderKind.FAMILY_DATES -> ReminderContent.FamilyDate
                ReminderKind.SUNRISE_BRAHMA -> ReminderContent.Brahma(now)
            }
        Poster.post(app, ReminderPayload(kind, TimeZone.currentSystemDefault().id, content), id = TEST_ID)
        Log.i(TAG, "post: kind=$kind")
    }

    private companion object {
        const val TAG = "PpDebug"
        const val TEST_ID = 9_999
    }
}
