package app.parivaar.panchang.core.model.place

import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.profile.RecordId
import kotlin.time.Instant

data class SavedPlace(val id: RecordId, val place: GeoPlace, val pinned: Boolean, val lastUsedAt: Instant) {
    override fun toString(): String = "SavedPlace(id=${id.hex})"
}
