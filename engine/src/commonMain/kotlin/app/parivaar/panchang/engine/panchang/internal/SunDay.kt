package app.parivaar.panchang.engine.panchang.internal

import app.parivaar.panchang.engine.astro.RiseSetEvent
import app.parivaar.panchang.engine.astro.RiseSetKind
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.PanchangError
import app.parivaar.panchang.engine.panchang.PanchangUnavailableException
import kotlin.time.Instant

/**
 * The sun events that frame a panchang day and the local midnight of its civil date (docs/specs/M03.md
 * section 3.2). Day = [sunrise, nextSunrise).
 */
internal class SunDay(
    val midnight: Instant,
    val sunrise: Instant,
    val sunset: Instant,
    val nextSunrise: Instant,
    val flags: Set<DayFlag>,
) {
    override fun toString(): String = "SunDay(redacted)"

    companion object {
        /**
         * From the events of the window [midnight, +24 h] ([ev]) and of the next one ([fv]): sunrise = first
         * RISE of [ev]; sunset = first SET of both after sunrise; nextSunrise = first RISE of [fv]. Throws
         * [PanchangUnavailableException] (NO_SUN_DAY) unless sunrise < sunset < nextSunrise all exist.
         */
        fun of(midnight: Instant, ev: List<RiseSetEvent>, fv: List<RiseSetEvent>): SunDay {
            val rise = ev.filter { it.kind == RiseSetKind.RISE }.minByOrNull { it.at } ?: unavailable()
            val set = (ev + fv).filter { it.kind == RiseSetKind.SET && it.at > rise.at }.minByOrNull { it.at }
                ?: unavailable()
            val next = fv.filter { it.kind == RiseSetKind.RISE }.minByOrNull { it.at } ?: unavailable()
            if (next.at <= set.at) unavailable()
            val flags = buildSet {
                if (rise.isFallback || set.isFallback || next.isFallback) add(DayFlag.SUN_FALLBACK)
                if (ev.any { it.kind == RiseSetKind.RISE && it.at > set.at }) add(DayFlag.EXTRA_SUNRISE)
            }
            return SunDay(midnight, rise.at, set.at, next.at, flags)
        }

        private fun unavailable(): Nothing = throw PanchangUnavailableException(PanchangError.NO_SUN_DAY)
    }
}
