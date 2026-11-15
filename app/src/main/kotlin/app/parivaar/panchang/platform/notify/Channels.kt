package app.parivaar.panchang.platform.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import app.parivaar.panchang.R
import app.parivaar.panchang.core.model.settings.ReminderKind

/** Channel ids are permanent once shipped. */
object Channels {
    const val VRAT_EVE = "vrat_eve"
    const val FAMILY_DATES = "family_dates"
    const val GRAHAN = "grahan"
    const val DAILY_TIMES = "daily_times"

    fun idFor(kind: ReminderKind): String =
        when (kind) {
            ReminderKind.VRAT_EVE -> VRAT_EVE
            ReminderKind.FAMILY_DATES -> FAMILY_DATES
            ReminderKind.GRAHAN -> GRAHAN
            ReminderKind.SUNRISE_BRAHMA -> DAILY_TIMES
        }

    /** (id, name, description). All IMPORTANCE_DEFAULT; private on the lock screen. Safe to call repeatedly. */
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val defs =
            listOf(
                Triple(VRAT_EVE, R.string.notify_channel_vrat_eve, R.string.notify_channel_vrat_eve_desc),
                Triple(FAMILY_DATES, R.string.notify_channel_family, R.string.notify_channel_family_desc),
                Triple(GRAHAN, R.string.notify_channel_grahan, R.string.notify_channel_grahan_desc),
                Triple(DAILY_TIMES, R.string.notify_channel_daily, R.string.notify_channel_daily_desc),
            )
        for ((id, name, desc) in defs) {
            val c = NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT)
            c.description = context.getString(desc)
            c.lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            nm.createNotificationChannel(c)
        }
    }
}
