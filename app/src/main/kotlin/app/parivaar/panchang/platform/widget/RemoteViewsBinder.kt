@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.widget

import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import app.parivaar.panchang.R
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.feature.aaj.widget.WidgetModel
import kotlin.time.Instant

/** Maps a [WidgetModel] to views. A null model means no home place is chosen yet. */
internal class RemoteViewsBinder(private val context: Context, private val text: WidgetText, private val now: Instant) {
    private fun open(rv: RemoteViews) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val pi = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        rv.setOnClickPendingIntent(R.id.widget_root, pi)
    }

    private fun setText(rv: RemoteViews, id: Int, s: String?) {
        rv.setTextViewText(id, s ?: "")
        rv.setViewVisibility(id, if (s.isNullOrEmpty()) View.GONE else View.VISIBLE)
    }

    private fun nextLine(w: Window?): String? =
        w?.let {
            if (it.start <= now) {
                context.getString(R.string.widget_next_active, text.window(it.kind), text.time(it.end))
            } else {
                val (a, z) = text.range(it.start, it.end)
                context.getString(R.string.widget_next_line, text.window(it.kind), a, z)
            }
        }

    fun today(model: WidgetModel?): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_today)
        open(rv)
        rv.setContentDescription(R.id.widget_root, context.getString(R.string.widget_cd_today))
        for (id in listOf(R.id.today_tithi, R.id.today_nakshatra, R.id.today_sun, R.id.today_next, R.id.today_city)) {
            setText(rv, id, null)
        }
        when (model) {
            is WidgetModel.Today -> {
                setText(rv, R.id.today_title, text.vaar(model.vaar.ordinal))
                setText(rv, R.id.today_tithi, context.getString(R.string.widget_tithi_line, text.tithi(model.tithi), text.time(model.tithi.end)))
                setText(rv, R.id.today_nakshatra, context.getString(R.string.widget_nakshatra_line, text.nakshatra(model.nakshatra), text.time(model.nakshatra.end)))
                setText(rv, R.id.today_sun, text.range(model.sunrise, model.sunset).let { (a, z) -> context.getString(R.string.widget_sun_line, a, z) })
                setText(rv, R.id.today_next, nextLine(model.next))
                setText(rv, R.id.today_city, model.city)
            }
            null -> {
                setText(rv, R.id.today_title, context.getString(R.string.widget_set_city))
                setText(rv, R.id.today_tithi, context.getString(R.string.widget_set_city_hint))
            }
            else -> setText(rv, R.id.today_title, context.getString(R.string.widget_unavailable))
        }
        return rv
    }

    fun rahu(model: WidgetModel?): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_rahu)
        open(rv)
        rv.setContentDescription(R.id.widget_root, context.getString(R.string.widget_cd_rahu))
        rv.setViewVisibility(R.id.rahu_chrono, View.GONE)
        setText(rv, R.id.rahu_range, null)
        setText(rv, R.id.rahu_city, null)
        when (model) {
            is WidgetModel.Rahu -> {
                setText(rv, R.id.rahu_label, context.getString(if (model.active) R.string.widget_rahu_active else R.string.widget_rahu_next))
                setText(rv, R.id.rahu_range, text.range(model.start, model.end).let { (a, z) -> context.getString(R.string.widget_rahu_range, a, z) })
                setText(rv, R.id.rahu_city, model.city)
                // Count down in the system chronometer: no per-second widget updates.
                val target = if (model.active) model.end else model.start
                val base = SystemClock.elapsedRealtime() + (target.toEpochMilliseconds() - now.toEpochMilliseconds())
                rv.setChronometerCountDown(R.id.rahu_chrono, true)
                rv.setChronometer(R.id.rahu_chrono, base, null, true)
                rv.setViewVisibility(R.id.rahu_chrono, View.VISIBLE)
            }
            null -> setText(rv, R.id.rahu_label, context.getString(R.string.widget_set_city))
            else -> setText(rv, R.id.rahu_label, context.getString(R.string.widget_unavailable))
        }
        return rv
    }

    fun festival(model: WidgetModel?): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_festival)
        open(rv)
        rv.setContentDescription(R.id.widget_root, context.getString(R.string.widget_cd_festival))
        setText(rv, R.id.fest_in, null)
        setText(rv, R.id.fest_city, null)
        setText(rv, R.id.fest_label, context.getString(R.string.widget_festival_label))
        when (model) {
            is WidgetModel.Festival -> {
                setText(rv, R.id.fest_name, text.festival(model.id))
                setText(rv, R.id.fest_in, daysText(model.inDays))
                setText(rv, R.id.fest_city, model.city)
            }
            null -> setText(rv, R.id.fest_name, context.getString(R.string.widget_set_city))
            else -> setText(rv, R.id.fest_name, context.getString(R.string.widget_no_festival))
        }
        return rv
    }

    private fun daysText(n: Int): String =
        when (n) {
            0 -> context.getString(R.string.widget_today_word)
            1 -> context.getString(R.string.widget_tomorrow)
            else -> context.getString(R.string.widget_in_days, n)
        }
}
