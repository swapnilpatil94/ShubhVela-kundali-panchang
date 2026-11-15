@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.parivaar.panchang.R
import app.parivaar.panchang.feature.aaj.reminders.ReminderContent
import kotlinx.datetime.TimeZone
import app.parivaar.panchang.platform.widget.rangeWithDayRef
import kotlinx.datetime.offsetIn
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toLocalDateTime
import java.time.Instant as JInstant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Renders [ReminderPayload] to a notification from string resources. No names, no city, no birth data: the content
 * types cannot carry them. If notifications are off (POST_NOTIFICATIONS denied or blocked) it does nothing and
 * never asks again.
 */
object Poster {
    fun post(context: Context, p: ReminderPayload, id: Int) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Channels.ensure(context)
        val zone = TimeZone.of(p.zoneId)
        val text = textFor(context, p.content, zone)
        val channel = Channels.idFor(p.kind)
        val open =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(
                    context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        val public =
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentText(context.getString(R.string.notify_public))
                .build()
        val n =
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(context.getString(R.string.notify_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission") // checked above
        runCatching { nm.notify(id, n) }
    }

    /**
     * [now] is when the notification is shown; times on another civil date than the date they are described
     * relative to get a day label ("(next day)" or a short date).
     */
    internal fun textFor(
        context: Context,
        c: ReminderContent,
        zone: TimeZone,
        now: kotlin.time.Instant = kotlin.time.Clock.System.now(),
    ): String {
        val locale = Locale.getDefault()
        val f = if (DateFormat.is24HourFormat(context)) DateTimeFormatter.ofPattern("HH:mm", locale)
        else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val javaZone = zone.toJavaZoneId()
        fun t(i: kotlin.time.Instant): String = f.format(ZonedDateTime.ofInstant(JInstant.ofEpochSecond(i.epochSeconds), javaZone))
        val dateFmt = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
        val offset = now.offsetIn(zone).totalSeconds
        val today = now.toLocalDateTime(zone).date
        fun range(from: kotlin.time.Instant, to: kotlin.time.Instant, shown: kotlinx.datetime.LocalDate) =
            rangeWithDayRef(
                from, to, offset, shown, ::t,
                context.getString(R.string.widget_next_day), context.getString(R.string.widget_previous_day),
            ) { d -> context.getString(R.string.widget_other_day, dateFmt.format(java.time.LocalDate.of(d.year, d.monthNumber, d.dayOfMonth))) }
        return when (c) {
            is ReminderContent.VratEve ->
                if (c.festivalId.startsWith("ekadashi") && c.parana != null) {
                    // Parana is usually the day after tomorrow: label it relative to the day the user reads this.
                    val (a, z) = range(c.parana!!.start, c.parana!!.end, today)
                    context.getString(R.string.notify_ekadashi_parana, a, z)
                } else {
                    context.getString(R.string.notify_vrat_generic)
                }
            is ReminderContent.Grahan -> {
                // The text says "tomorrow", so tomorrow is the shown date.
                val (a, z) = range(c.visible.start, c.visible.end, today.plus(kotlinx.datetime.DatePeriod(days = 1)))
                context.getString(R.string.notify_grahan, a, z)
            }
            ReminderContent.FamilyDate -> context.getString(R.string.notify_family)
            is ReminderContent.Brahma -> context.getString(R.string.notify_brahma, t(c.sunrise))
        }
    }
}
