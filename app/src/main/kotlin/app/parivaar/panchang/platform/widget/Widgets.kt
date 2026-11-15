package app.parivaar.panchang.platform.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 4x2: vaar, tithi, nakshatra, sunrise and sunset, next window. Exported: the launcher binds it. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        WidgetRefresher.refreshAsync(context, goAsync())
}

/** 2x1: the next or active Rahu kaal with a chronometer countdown. */
class NextRahuWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        WidgetRefresher.refreshAsync(context, goAsync())
}

/** 2x2: the next festival within 60 days. */
class FestivalWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        WidgetRefresher.refreshAsync(context, goAsync())
}
