package app.parivaar.panchang.platform

import android.content.Context
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.platform.engine.EngineAccess
import app.parivaar.panchang.platform.engine.EngineReminderSource
import app.parivaar.panchang.platform.notify.Replan
import app.parivaar.panchang.platform.notify.ReminderRuntime
import app.parivaar.panchang.platform.notify.ReplanJobService
import app.parivaar.panchang.platform.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts reminder and widget re-planning. Everything runs on `Dispatchers.IO`; nothing touches disk on main. */
object PlatformWiring {
    private data class Key(val inputs: List<Any?>)

    fun start(context: Context, scope: CoroutineScope, access: EngineAccess, settings: SettingsRepository) {
        val app = context.applicationContext
        ReminderRuntime.source = EngineReminderSource(access)
        scope.launch(Dispatchers.IO) { ReplanJobService.schedule(app) }
        scope.launch(Dispatchers.IO) {
            // Re-plan when anything the plan or the widgets depend on changes (the vault opening also changes it).
            settings.settings
                .map { Key(listOf(it.homePlaceId, it.defaultCalc, it.reminders, it.showCityOnWidgets, it.localeTag)) }
                .distinctUntilChanged()
                .collect {
                    Replan.run(app)
                    WidgetRefresher.refresh(app)
                }
        }
    }
}
