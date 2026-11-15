package app.parivaar.panchang.feature.aaj

import android.os.SystemClock
import android.util.Log

/**
 * Debug-only timing log (tag `PpPerf`): names and durations only, never a place, date or value. It stays off unless
 * the app enables it for debuggable builds, so release builds never log.
 */
object PpPerf {
    @Volatile var enabled = false

    private const val TAG = "PpPerf"
    const val NANOS_PER_MS = 1_000_000.0

    inline fun <T> time(name: String, block: () -> T): T {
        if (!enabled) return block()
        val t0 = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } finally {
            log(name, (SystemClock.elapsedRealtimeNanos() - t0) / NANOS_PER_MS)
        }
    }

    /** A point in time as milliseconds since process start (the first-content budget). */
    fun mark(name: String) {
        if (!enabled) return
        val sinceStart = SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
        log("$name sinceProcessStart", sinceStart.toDouble())
    }

    @PublishedApi
    internal fun log(name: String, ms: Double) {
        Log.d(TAG, "$name ${"%.1f".format(ms)} ms")
    }
}
