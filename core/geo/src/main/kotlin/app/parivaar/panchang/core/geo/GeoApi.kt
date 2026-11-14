package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.LocalResolution
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.PlaceHit
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.geo.TzContext
import app.parivaar.panchang.engine.Place
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Offline place lookup. Inputs are used in memory only and never stored or logged. */
interface PlaceSearch {
    fun search(query: String, limit: Int = 20): List<PlaceHit>

    fun nearest(latDeg: Double, lonDeg: Double, maxKm: Double = 100.0): GeoPlace?
}

/** Turns (zone, longitude, overlay key) plus an instant or civil time into the UTC offset the engine takes. */
interface OffsetResolver {
    fun offsetAt(instant: Instant, ctx: TzContext, manual: ManualOffset? = null): ResolvedOffset

    /** Feeds PanchangCalculator.day(date, offset, ...). */
    fun civilDayOffset(date: LocalDate, ctx: TzContext, manual: ManualOffset? = null): CivilDayOffset

    fun localToInstant(
        local: LocalDateTime,
        ctx: TzContext,
        choice: AmbiguityChoice = AmbiguityChoice.EARLIER,
        manual: ManualOffset? = null,
    ): LocalResolution
}

fun GeoPlace.toEnginePlace(): Place = Place(latDeg, lonDeg, elevationM ?: 0.0)
