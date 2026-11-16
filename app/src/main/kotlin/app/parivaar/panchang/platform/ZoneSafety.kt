package app.parivaar.panchang.platform

import app.parivaar.panchang.core.model.geo.UnknownZoneException
import kotlinx.datetime.IllegalTimeZoneException

/**
 * Runs [block]; an unknown time zone (a row stored before validation, or a zone this device's tz database lacks)
 * yields [fallback] instead of crashing a background re-plan or widget refresh (Fable review item 2).
 */
internal inline fun <T> zoneSafe(fallback: T, block: () -> T): T =
    try {
        block()
    } catch (_: UnknownZoneException) {
        fallback
    } catch (_: IllegalTimeZoneException) {
        fallback
    }
