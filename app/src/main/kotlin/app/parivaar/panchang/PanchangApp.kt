package app.parivaar.panchang

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import app.parivaar.panchang.core.data.DataGraph
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.platform.PlatformWiring
import app.parivaar.panchang.platform.engine.EngineAccess
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.parivaar.panchang.feature.aaj.PpPerf
import javax.inject.Inject

@HiltAndroidApp
class PanchangApp : Application() {
    /** Process-lifetime scope for work that must outlive any screen. */
    val appScope = CoroutineScope(SupervisorJob())

    @Inject lateinit var graph: Lazy<DataGraph>

    @Inject lateinit var engine: Lazy<EngineAccess>

    @Inject lateinit var settingsRepo: Lazy<SettingsRepository>

    /** The shared engine instance (single engine thread), for widgets, tile and reminders. */
    val engineAccess: EngineAccess get() = engine.get()

    override fun onCreate() {
        enableStrictModeInDebug()
        super.onCreate()
        // Debug builds only: PpPerf timings (names and durations, never personal data).
        PpPerf.enabled = resources.getBoolean(R.bool.pp_perf_log)
        PpPerf.mark("app.onCreate")
        // M06 D8: the vault opens (Keystore, disk) only off the main thread; the gate shows Loading meanwhile.
        appScope.launch(Dispatchers.IO) { graph.get().start() }
        // Rule assets and the engine are built once, in the background, before Today needs them.
        appScope.launch(Dispatchers.IO) { engine.get().warm() }
        // M08: reminders and widgets re-plan on settings and place changes (all off the main thread).
        appScope.launch(Dispatchers.IO) {
            PlatformWiring.start(this@PanchangApp, appScope, engine.get(), settingsRepo.get())
        }
    }

    /** Debug builds only: log main-thread disk access and slow calls so startup regressions show up early. */
    private fun enableStrictModeInDebug() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
    }
}
