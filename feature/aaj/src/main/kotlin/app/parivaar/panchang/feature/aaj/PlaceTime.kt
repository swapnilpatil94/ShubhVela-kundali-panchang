package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.engine.panchang.PanchangDay
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The sunrise-to-sunrise day containing [now] (`sunrise <= now < nextSunrise`):
 * its vaar, gem and limbs are current.
 */
fun vedicDayAt(days: List<PanchangDay>, now: Instant): PanchangDay? =
    days.firstOrNull { it.sunrise <= now && now < it.nextSunrise }

/**
 * Wall-clock offsets of a place, per instant (DST-aware). [zoneId] is the place's IANA id; [dayOffsetSeconds] is the
 * resolver's offset of the shown civil day. When the IANA rules never give that offset on the shown day (a
 * historical overlay such as Bombay time, or an unknown id) the resolver's fixed offset wins, so text never
 * disagrees with the engine's own day boundaries.
 */
class PlaceClock(zoneId: String?, dayOffsetSeconds: Int, shown: LocalDate) {
    val zone: ZoneId = resolve(zoneId, dayOffsetSeconds, shown)

    fun offsetAt(at: Instant): Int = zone.offsetSecondsAt(at)

    fun dayRef(at: Instant, shown: LocalDate): DayRef = dayRef(at, offsetAt(at), shown)

    /** As [rangeRefs] but each end in its own offset. */
    fun rangeRefs(from: Instant, to: Instant, shown: LocalDate): Pair<DayRef, DayRef> {
        val a = dayRef(from, shown)
        val b = dayRef(to, shown)
        return if (a == b) DayRef.Same to b else a to b
    }

    companion object {
        fun fixed(offsetSeconds: Int): PlaceClock = PlaceClock(null, offsetSeconds, LocalDate(EPOCH_YEAR, 1, 1))

        private fun resolve(zoneId: String?, dayOffset: Int, shown: LocalDate): ZoneId {
            val fixed = ZoneOffset.ofTotalSeconds(dayOffset)
            val z = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return fixed
            val day = java.time.LocalDate.ofEpochDay(shown.toEpochDays().toLong())
            val start = day.atStartOfDay(z).toInstant()
            val end = day.plusDays(1).atStartOfDay(z).toInstant()
            val seen = setOf(z.rules.getOffset(start).totalSeconds, z.rules.getOffset(end.minusSeconds(1)).totalSeconds)
            return if (dayOffset in seen) z else fixed
        }

        private const val EPOCH_YEAR = 1970
    }
}

/** UTC offset of [at] in this zone, in seconds. */
fun ZoneId.offsetSecondsAt(at: Instant): Int =
    rules.getOffset(java.time.Instant.ofEpochSecond(at.epochSeconds)).totalSeconds
