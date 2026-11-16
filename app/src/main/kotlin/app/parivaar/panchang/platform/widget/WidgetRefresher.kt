@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.widget

import app.parivaar.panchang.platform.engine.today
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.parivaar.panchang.platform.zoneSafe
import app.parivaar.panchang.PanchangApp
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.feature.aaj.widget.WidgetContent
import app.parivaar.panchang.feature.aaj.widget.WidgetModel
import app.parivaar.panchang.platform.engine.ActivePlace
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Inexact, non-wakeup alarm; the pending intent is explicit and not exported. */
internal class AlarmManagerWidgetAlarm(private val context: Context) : WidgetAlarm {
    private fun pi(flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, WidgetRefreshReceiver::class.java).setAction(WidgetRefreshReceiver.ACTION),
            PendingIntent.FLAG_IMMUTABLE or flag,
        )

    override fun setWindow(at: Instant, window: Duration) {
        val pi = pi(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC, at.toEpochMilliseconds(), window.inWholeMilliseconds, pi)
    }

    override fun cancel() {
        pi(PendingIntent.FLAG_NO_CREATE)?.let { context.getSystemService(AlarmManager::class.java).cancel(it) }
    }

    private companion object {
        const val REQUEST = 8101
    }
}

/** Fires at element transitions. Not exported. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        WidgetRefresher.refreshAsync(context, goAsync())
    }

    companion object {
        const val ACTION = "app.parivaar.panchang.action.WIDGET_REFRESH"
    }
}

/** Builds every placed widget from engine data (off the main thread) and schedules the next transition. */
object WidgetRefresher {
    private val providers = listOf(TodayWidget::class.java, NextRahuWidget::class.java, FestivalWidget::class.java)

    fun refreshAsync(context: Context, pending: BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        Thread {
            try {
                zoneSafe(Unit) { refresh(app) } // an uncaught throw on this thread would crash the process
            } finally {
                pending?.finish()
            }
        }.start()
    }

    private fun ids(context: Context, cls: Class<out AppWidgetProvider>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    /** Blocking. */
    fun refresh(context: Context, now: Instant = Clock.System.now()) {
        val mgr = AppWidgetManager.getInstance(context)
        val scheduler = WidgetScheduler(AlarmManagerWidgetAlarm(context))
        val today = ids(context, TodayWidget::class.java)
        val rahu = ids(context, NextRahuWidget::class.java)
        val fest = ids(context, FestivalWidget::class.java)
        if (today.isEmpty() && rahu.isEmpty() && fest.isEmpty()) {
            scheduler.cancel()
            return
        }
        val access = (context.applicationContext as PanchangApp).engineAccess
        val a: ActivePlace? = access.active()
        if (a == null) {
            val b = RemoteViewsBinder(context, WidgetText(context, 0, kotlinx.datetime.LocalDate(EPOCH_YEAR, 1, 1)), now)
            today.forEach { mgr.updateAppWidget(it, b.today(null)) }
            rahu.forEach { mgr.updateAppWidget(it, b.rahu(null)) }
            fest.forEach { mgr.updateAppWidget(it, b.festival(null)) }
            scheduler.cancel()
            return
        }
        val date = access.today(a, now)
        val days = access.days(a, date.plus(DatePeriod(days = -1)), DAYS)
        val offset = a.offsetFor(date)
        val showCity = a.settings.showCityOnWidgets
        val b = RemoteViewsBinder(context, WidgetText(context, a.zone.id, offset, date), now)
        today.forEach { mgr.updateAppWidget(it, b.today(WidgetContent.today(now, days, showCity, a.name))) }
        rahu.forEach { mgr.updateAppWidget(it, b.rahu(WidgetContent.rahu(now, days, showCity, a.name))) }
        if (fest.isNotEmpty()) {
            val occ: List<FestivalOccurrence> =
                access.festivals(a, date, date.plus(DatePeriod(days = WidgetContent.FESTIVAL_HORIZON_DAYS)))
            val m = WidgetContent.festival(date, occ, showCity, a.name)
            fest.forEach { mgr.updateAppWidget(it, b.festival(m)) }
        }
        scheduler.scheduleNext(now, days, offset)
    }

    private const val DAYS = 3
    private const val EPOCH_YEAR = 1970
}
