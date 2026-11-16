@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.tile

import app.parivaar.panchang.platform.engine.today
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.parivaar.panchang.PanchangApp
import app.parivaar.panchang.R
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.platform.widget.WidgetText
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.time.Clock

/**
 * Quick Settings tile: "Rahu kaal 3:00 PM" with the current choghadiya as subtitle. Updates only while listening;
 * tap opens the app. Exported (the platform requires it) and guarded by BIND_QUICK_SETTINGS_TILE.
 */
class PanchangTileService : TileService() {
    override fun onStartListening() {
        val fallback = getString(R.string.tile_unavailable)
        Thread {
            val (label, subtitle) = safeTile(fallback) { compute() }
            Handler(Looper.getMainLooper()).post { runCatching { apply(label, subtitle) } }
        }.start()
    }

    private fun compute(): Pair<String, String?> {
        val ctx = applicationContext
        val access = (ctx as PanchangApp).engineAccess
        val a = access.active() ?: return ctx.getString(R.string.tile_set_city) to null
        val now = Clock.System.now()
        val date = access.today(a, now)
        val days = access.days(a, date.plus(DatePeriod(days = -1)), 3)
        val text = WidgetText(ctx, a.zone.id, a.offsetFor(date), date)
        val windows = days.flatMap { it.windows }
        val rahu = windows.filter { it.kind == WindowKind.RAHU_KAAL && it.end > now }.minByOrNull { it.start }
        val cg = windows.firstOrNull { it.kind == WindowKind.CHOGHADIYA && it.start <= now && now < it.end }
        val label = rahu?.let { ctx.getString(R.string.tile_rahu, text.time(it.start)) } ?: ctx.getString(R.string.tile_unavailable)
        return label to cg?.let { text.choghadiya(it.code) }
    }

    private fun apply(label: String, subtitle: String?) {
        val tile = qsTile ?: return
        tile.label = label
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.updateTile()
    }

    override fun onClick() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(launch)
        }
    }
}
