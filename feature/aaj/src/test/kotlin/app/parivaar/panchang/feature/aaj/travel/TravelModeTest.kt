@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.travel

import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.PlaceHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

private val DUBAI = GeoPlace(7, "Dubai", null, "AE", 25.2, 55.27, 5.0, 0, "Asia/Dubai", null)

private class FakeSearch(val result: GeoPlace?) : PlaceSearch {
    var lookups = 0
    override fun search(query: String, limit: Int): List<PlaceHit> = emptyList()
    override fun nearest(latDeg: Double, lonDeg: Double, maxKm: Double): GeoPlace? { lookups++; return result }
}

private class FakePermission(var has: Boolean, val answer: Boolean) : CoarsePermission {
    var requests = 0
    override fun granted() = has
    override fun request(onResult: (granted: Boolean) -> Unit) {
        requests++
        has = has || answer
        onResult(answer)
    }
}

class TravelModeTest {
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    private fun mode(search: FakeSearch, perm: FakePermission, fix: Pair<Double, Double>? = 25.2 to 55.27) =
        TravelMode(search, { fix }, perm, scope, Dispatchers.Unconfined)

    @Test fun `travel mode off never offers`() {
        val m = mode(FakeSearch(DUBAI), FakePermission(false, true))
        m.onTimeZoneChanged(false, 3600, 19_800)
        assertEquals(TravelUi.Idle, m.ui.value)
    }

    @Test fun `no location is asked before the rationale`() {
        val perm = FakePermission(false, true)
        val m = mode(FakeSearch(DUBAI), perm)
        m.onTimeZoneChanged(true, 14_400, 19_800)
        assertEquals(TravelUi.Offer, m.ui.value)
        m.continueFromRationale() // not in Rationale yet: ignored
        assertEquals(0, perm.requests)
        m.onOfferTapped()
        assertEquals(TravelUi.Rationale, m.ui.value)
        assertEquals(0, perm.requests)
    }

    @Test fun `granted path finds the nearest place and keeps it in memory`() {
        val search = FakeSearch(DUBAI)
        val perm = FakePermission(false, true)
        val m = mode(search, perm)
        m.onTimeZoneChanged(true, 14_400, 19_800); m.onOfferTapped(); m.continueFromRationale()
        assertEquals(1, perm.requests)
        assertEquals(1, search.lookups)
        assertEquals(TravelUi.InPlace("Dubai"), m.ui.value)
        assertEquals(DUBAI, m.place.value)
    }

    @Test fun `already granted skips the prompt`() {
        val perm = FakePermission(true, false)
        val m = mode(FakeSearch(DUBAI), perm)
        m.onTimeZoneChanged(true, 14_400, 19_800); m.onOfferTapped(); m.continueFromRationale()
        assertEquals(0, perm.requests)
        assertEquals(TravelUi.InPlace("Dubai"), m.ui.value)
    }

    @Test fun `denied keeps home and shows a calm state`() {
        val search = FakeSearch(DUBAI)
        val m = mode(search, FakePermission(false, false))
        m.onTimeZoneChanged(true, 14_400, 19_800); m.onOfferTapped(); m.continueFromRationale()
        assertEquals(TravelUi.Denied, m.ui.value)
        assertNull(m.place.value)
        assertEquals(0, search.lookups)
        m.dismiss()
        assertEquals(TravelUi.Idle, m.ui.value)
    }

    @Test fun `no fix and no nearby place leave the home place alone`() {
        val a = mode(FakeSearch(DUBAI), FakePermission(true, true), fix = null)
        a.onTimeZoneChanged(true, 14_400, 19_800); a.onOfferTapped(); a.continueFromRationale()
        assertEquals(TravelUi.NoFix, a.ui.value)
        val b = mode(FakeSearch(null), FakePermission(true, true))
        b.onTimeZoneChanged(true, 14_400, 19_800); b.onOfferTapped(); b.continueFromRationale()
        assertEquals(TravelUi.NoPlaceNearby, b.ui.value)
        assertNull(b.place.value)
    }

    @Test fun `back to home clears the travel place and a new zone change offers again`() {
        val m = mode(FakeSearch(DUBAI), FakePermission(true, true))
        m.onTimeZoneChanged(true, 14_400, 19_800); m.onOfferTapped(); m.continueFromRationale()
        m.onTimeZoneChanged(true, 14_400, 19_800)
        assertEquals(TravelUi.Offer, m.ui.value)
        m.backToHome()
        assertNull(m.place.value)
        assertEquals(TravelUi.Idle, m.ui.value)
    }

    @Test fun `returning to the home zone clears the offer, the travel place and messages`() {
        val m = mode(FakeSearch(DUBAI), FakePermission(true, true))
        m.onTimeZoneChanged(true, 14_400, 19_800)
        assertEquals(TravelUi.Offer, m.ui.value)
        m.onTimeZoneChanged(true, 19_800, 19_800)
        assertEquals(TravelUi.Idle, m.ui.value)
        m.onTimeZoneChanged(true, 14_400, 19_800); m.onOfferTapped(); m.continueFromRationale()
        assertEquals(DUBAI, m.place.value)
        m.onTimeZoneChanged(true, 19_800, 19_800)
        assertNull(m.place.value)
        assertEquals(TravelUi.Idle, m.ui.value)
        // Also when travel mode has since been switched off, and when the home offset is unknown nothing is cleared.
        m.onTimeZoneChanged(true, 14_400, null)
        assertEquals(TravelUi.Offer, m.ui.value)
        m.onTimeZoneChanged(false, 19_800, 19_800)
        assertEquals(TravelUi.Idle, m.ui.value)
    }
}
