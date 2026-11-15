@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.parivaar.panchang.platform.widget.WidgetRefresher

/** Boot, time, time-zone and locale changes re-plan reminders and refresh widgets. Not exported; the system's broadcasts still arrive. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                ReplanJobService.schedule(app)
                Replan.run(app)
                WidgetRefresher.refresh(app)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        val ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_LOCALE_CHANGED,
            )
    }
}
