package app.parivaar.panchang.platform.travel

import android.content.Context
import app.parivaar.panchang.feature.aaj.travel.CoarseLocator
import app.parivaar.panchang.feature.aaj.travel.CoarsePermission
import app.parivaar.panchang.feature.onboarding.LocationOneShot

/**
 * The permission request lives in the activity; the shell holder survives `recreate()`, so it talks to whichever
 * activity is current through this bridge. With no activity it reports "not granted" and never prompts.
 */
class PermissionBridge : CoarsePermission {
    @Volatile var delegate: CoarsePermission? = null

    override fun granted(): Boolean = delegate?.granted() ?: false

    override fun request(onResult: (granted: Boolean) -> Unit) {
        val d = delegate
        if (d == null) onResult(false) else d.request(onResult)
    }
}

/** One approximate fix through the same one-shot helper onboarding uses; nothing is stored or logged. */
class AppCoarseLocator(private val context: Context) : CoarseLocator {
    override suspend fun fix(): Pair<Double, Double>? = LocationOneShot.fetch(context)
}
