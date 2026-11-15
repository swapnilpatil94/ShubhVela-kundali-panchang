package app.parivaar.panchang.feature.onboarding

import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.engine.calendar.MonthSystem

/**
 * Regional default month system for "Not sure". Empty in M07 (VERIFY with a pandit, TODO(owner));
 * callers fall back to AMANTA and mark the choice as a default.
 */
object RegionPresets {
    @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
    fun forPlace(place: GeoPlace): MonthSystem? = null
}
