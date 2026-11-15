package app.parivaar.panchang

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import app.parivaar.panchang.core.data.DataGraph
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PanchangApp : Application() {
    /** Process-lifetime scope for work that must outlive any screen. */
    val appScope = CoroutineScope(SupervisorJob())

    @Inject lateinit var graph: Lazy<DataGraph>

    override fun onCreate() {
        enableStrictModeInDebug()
        super.onCreate()
        // M06 D8: the vault opens (Keystore, disk) only off the main thread; the gate shows Loading meanwhile.
        appScope.launch(Dispatchers.IO) { graph.get().start() }
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
