@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.travel

import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.model.geo.GeoPlace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One approximate fix, or null (no permission, provider off, timeout). Implemented by the app. */
fun interface CoarseLocator {
    suspend fun fix(): Pair<Double, Double>?
}

/** The platform permission, behind a port so the flow is testable. */
interface CoarsePermission {
    fun granted(): Boolean
    fun request(onResult: (granted: Boolean) -> Unit)
}

/** What the travel card shows. [InPlace] carries the place name for the "You're in ..." line (UI only, never logged). */
sealed interface TravelUi {
    data object Idle : TravelUi
    data object Offer : TravelUi
    data object Rationale : TravelUi
    data object Locating : TravelUi
    data class InPlace(val name: String) : TravelUi
    data object Denied : TravelUi
    data object NoFix : TravelUi
    data object NoPlaceNearby : TravelUi
}

/**
 * Travel mode (M3, decision D8). Opt-in: [onTimeZoneChanged] does nothing unless the setting is on. Location is asked
 * for only after the in-context rationale (`Rationale`, then [continueFromRationale]). The coordinates live in a local
 * variable for one `nearest()` call and are dropped; only the nearest [GeoPlace] is kept, in memory, for the session.
 * The home place is never touched.
 */
class TravelMode(
    private val search: PlaceSearch,
    private val locator: CoarseLocator,
    private val permission: CoarsePermission,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _ui = MutableStateFlow<TravelUi>(TravelUi.Idle)
    val ui: StateFlow<TravelUi> = _ui

    private val _place = MutableStateFlow<GeoPlace?>(null)

    /** The session travel place, or null (use home). */
    val place: StateFlow<GeoPlace?> = _place

    /**
     * The time zone changed. When the device offset now equals the home place's offset (back in the home zone) any
     * offer, travel place or message is cleared. Otherwise an update is offered, only when travel mode is on and
     * nothing is in progress. Offsets are in seconds at that instant; [homeOffsetSeconds] is null if unknown.
     */
    fun onTimeZoneChanged(travelModeOn: Boolean, deviceOffsetSeconds: Int, homeOffsetSeconds: Int?) {
        if (homeOffsetSeconds != null && homeOffsetSeconds == deviceOffsetSeconds) {
            if (_ui.value != TravelUi.Locating) backToHome()
            return
        }
        if (!travelModeOn) return
        when (_ui.value) {
            TravelUi.Idle, is TravelUi.InPlace, TravelUi.Denied, TravelUi.NoFix, TravelUi.NoPlaceNearby ->
                _ui.value = TravelUi.Offer
            else -> Unit
        }
    }

    /** The user tapped the offer: show the rationale first. */
    fun onOfferTapped() {
        if (_ui.value == TravelUi.Offer) _ui.value = TravelUi.Rationale
    }

    /** The user accepted the rationale: ask for the permission if needed, then take one fix. */
    fun continueFromRationale() {
        if (_ui.value != TravelUi.Rationale) return
        if (permission.granted()) {
            locate()
        } else {
            permission.request { granted -> if (granted) locate() else _ui.value = TravelUi.Denied }
        }
    }

    /** Close an offer, rationale or message without changing the place. */
    fun dismiss() {
        when (_ui.value) {
            is TravelUi.InPlace, TravelUi.Locating, TravelUi.Idle -> Unit
            else -> _ui.value = TravelUi.Idle
        }
    }

    /** "Back to home": drop the travel place. */
    fun backToHome() {
        _place.value = null
        _ui.value = TravelUi.Idle
    }

    private fun locate() {
        _ui.value = TravelUi.Locating
        scope.launch {
            val found = withContext(io) {
                val fix = locator.fix() ?: return@withContext Found.NoFix
                val nearest = search.nearest(fix.first, fix.second)
                if (nearest == null) Found.NoPlace else Found.At(nearest)
            }
            when (found) {
                Found.NoFix -> _ui.value = TravelUi.NoFix
                Found.NoPlace -> _ui.value = TravelUi.NoPlaceNearby
                is Found.At -> {
                    _place.value = found.place
                    _ui.value = TravelUi.InPlace(found.place.name)
                }
            }
        }
    }

    private sealed interface Found {
        data object NoFix : Found
        data object NoPlace : Found
        data class At(val place: GeoPlace) : Found
    }
}
