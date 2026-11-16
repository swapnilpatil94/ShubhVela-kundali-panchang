package app.parivaar.panchang.engine.panchang.internal

import app.parivaar.panchang.engine.astro.RiseSet
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
         * From the sun windows [midnight, +24 h] ([ev]) and the next one ([fv]), event by event (Fable review M1):
         * sunrise = first real RISE of [ev], else the first convention RISE of [ev]; nextSunrise = the same for
         * [fv] (so it equals the next day's sunrise); sunset = first real SET of both windows in
         * (sunrise, nextSunrise), else the first convention SET of [ev], then of [fv], in that interval. A
         * convention event is used only when no real crossing exists, so a sunset just after local midnight
         * (Reykjavik in June) stays real. Throws [PanchangUnavailableException] (NO_SUN_DAY) unless
         * sunrise < sunset < nextSunrise all exist.
         */
        fun of(midnight: Instant, ev: RiseSet.SunWindow, fv: RiseSet.SunWindow): SunDay {
            val rise = firstRise(ev) ?: unavailable()
            val next = firstRise(fv) ?: unavailable()
            if (next.at <= rise.at) unavailable()
            val set = sunset(rise.at, next.at, ev, fv) ?: unavailable()
            val flags = buildSet {
                if (rise.isFallback || set.isFallback || next.isFallback) add(DayFlag.SUN_FALLBACK)
                val sameKind = if (rise.isFallback) ev.fallback else ev.real
                if (sameKind.any { it.kind == RiseSetKind.RISE && it.at > set.at }) add(DayFlag.EXTRA_SUNRISE)
            }
            return SunDay(midnight, rise.at, set.at, next.at, flags)
        }

        /** First real SET of both windows in (rise, next), else the first convention SET of [ev], then [fv]. */
        private fun sunset(rise: Instant, next: Instant, ev: RiseSet.SunWindow, fv: RiseSet.SunWindow): RiseSetEvent? {
            fun first(list: List<RiseSetEvent>) =
                list.filter { it.kind == RiseSetKind.SET && it.at > rise && it.at < next }.minByOrNull { it.at }
            return first(ev.real + fv.real) ?: first(ev.fallback) ?: first(fv.fallback)
        }

        private fun firstRise(w: RiseSet.SunWindow): RiseSetEvent? =
            w.real.filter { it.kind == RiseSetKind.RISE }.minByOrNull { it.at }
                ?: w.fallback.filter { it.kind == RiseSetKind.RISE }.minByOrNull { it.at }

        private fun unavailable(): Nothing = throw PanchangUnavailableException(PanchangError.NO_SUN_DAY)
    }
}
