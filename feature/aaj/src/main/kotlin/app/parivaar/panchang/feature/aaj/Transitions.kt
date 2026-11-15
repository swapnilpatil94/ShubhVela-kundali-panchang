package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.engine.panchang.PanchangDay
import kotlin.time.Instant

object Transitions {
    private const val DAY = 86_400L

    /** [nextBoundary] with one fixed offset (widgets and tests that do not cross a DST change). */
    fun nextBoundary(now: Instant, days: List<PanchangDay>, utcOffsetSeconds: Int): Instant =
        nextBoundary(now, days) { utcOffsetSeconds }

    /**
     * The earliest instant after [now] where the display can change: limb end, window edge, sunrise or local
     * midnight. [offsetAt] gives the place's UTC offset at an instant, so a midnight after a DST change is not an
     * hour off.
     */
    fun nextBoundary(now: Instant, days: List<PanchangDay>, offsetAt: (Instant) -> Int): Instant {
        var best = nextLocalMidnight(now, offsetAt)
        fun consider(t: Instant) {
            if (t > now && t < best) best = t
        }
        for (d in days) {
            consider(d.sunrise)
            consider(d.nextSunrise)
            d.limbs.values.forEach { l -> l.forEach { consider(it.end) } }
            d.windows.forEach { consider(it.start); consider(it.end) }
        }
        return best
    }

    /** Local midnight after [now]: the offset is read again at the candidate instant, which settles DST changes. */
    fun nextLocalMidnight(now: Instant, offsetAt: (Instant) -> Int): Instant {
        val off0 = offsetAt(now)
        val nextDay = Math.floorDiv(now.epochSeconds + off0, DAY) + 1
        val first = Instant.fromEpochSeconds(nextDay * DAY - off0)
        val second = Instant.fromEpochSeconds(nextDay * DAY - offsetAt(first))
        return if (second > now) second else first
    }
}
