package app.parivaar.panchang.feature.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.Handler
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One approximate network fix with a 10 s timeout. The caller must already hold ACCESS_COARSE_LOCATION and must use
 * the result for a single nearest-place lookup, then drop it. Nothing is logged or stored here.
 */
object LocationOneShot {
    private const val TIMEOUT_MS = 10_000L

    suspend fun fetch(context: Context): Pair<Double, Double>? {
        val granted = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(LocationManager::class.java)
        if (!granted || manager == null || !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null
        val location = withTimeoutOrNull(TIMEOUT_MS) { request(manager) }
        return location?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun request(manager: LocationManager): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val handler = Handler(Looper.getMainLooper())
                    val mainExecutor = Executor { handler.post(it) }
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(
                        LocationManager.NETWORK_PROVIDER, signal, mainExecutor,
                    ) { if (cont.isActive) cont.resume(it) }
                } else {
                    val listener = LocationListener { if (cont.isActive) cont.resume(it) }
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                    manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
}
