package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.geo.internal.TzOverlay
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.LocalResolution
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.geo.TzContext
import app.parivaar.panchang.core.model.geo.UnknownZoneException
import app.parivaar.panchang.core.model.geo.isKnownZoneId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.time.DateTimeException
import java.time.ZoneId
import java.time.zone.ZoneRules
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

/**
 * Offset resolver on `java.time` (spec M05 sections 4.4 to 4.6). Rule order: manual, overlay, LMT, IANA.
 * Pure and thread-safe; no I/O after construction.
 */
class JavaTimeOffsetResolver(private val overlay: TzOverlay) : OffsetResolver {
    private class Zone(val rules: ZoneRules, val firstTransition: Long?)

    private val zones = ConcurrentHashMap<String, Zone>()

    private fun zone(zoneId: String): Zone =
        zones[zoneId] ?: run {
            if (!isKnownZoneId(zoneId)) throw UnknownZoneException()
            val rules = try {
                ZoneId.of(zoneId).rules
            } catch (@Suppress("SwallowedException") e: DateTimeException) {
                throw UnknownZoneException() // the message must not carry the zone id
            }
            val first = rules.transitions.firstOrNull()?.instant?.epochSecond
            Zone(rules, first).also { zones[zoneId] = it }
        }

    /** Plain IANA offset from the runtime tz rules, without overlay or LMT substitution. */
    internal fun ianaOffsetAt(zoneId: String, instant: Instant): Int =
        zone(zoneId).rules.getOffset(java.time.Instant.ofEpochSecond(instant.epochSeconds)).totalSeconds

    @Suppress("ReturnCount")
    private fun f(t: Long, ctx: TzContext, manual: ManualOffset?): ResolvedOffset {
        val z = zone(ctx.zoneId)
        if (manual != null) return ResolvedOffset(manual.seconds, OffsetSource.MANUAL, emptySet(), null)
        val flags = mutableSetOf<OffsetFlag>()
        val entry = overlay.find(ctx.overlayKey, t)
        if (entry != null) {
            val off = entry.offsetSeconds
            if (off != null) {
                flags += OffsetFlag.HISTORICAL_VERIFY
                return ResolvedOffset(off, OffsetSource.OVERLAY, flags, entry.id)
            }
            flags += OffsetFlag.NO_OVERLAY_DATA
        }
        val verify = ctx.zoneId in overlay.verifyZones && t < overlay.verifyUntilEpoch
        if (verify) flags += OffsetFlag.HISTORICAL_VERIFY
        val first = z.firstTransition
        if (t < LMT_BEFORE || (first != null && t < first)) {
            flags += OffsetFlag.PRE_STANDARD_LMT
            return ResolvedOffset(Math.round(ctx.lonDeg * LMT_SECONDS_PER_DEG).toInt(), OffsetSource.LMT, flags, null)
        }
        val at = java.time.Instant.ofEpochSecond(t)
        if (z.rules.isDaylightSavings(at)) flags += OffsetFlag.DST
        return ResolvedOffset(z.rules.getOffset(at).totalSeconds, OffsetSource.IANA, flags, null)
    }

    override fun offsetAt(instant: Instant, ctx: TzContext, manual: ManualOffset?): ResolvedOffset =
        f(instant.epochSeconds, ctx, manual)

    private class Local(val epoch: Long, val offset: ResolvedOffset, val alt: Long?, val ambiguous: Boolean)

    private fun resolve(l: Long, ctx: TzContext, choice: AmbiguityChoice, manual: ManualOffset?): Local {
        val oa = f(l - DAY, ctx, manual).offsetSeconds
        val ob = f(l + DAY, ctx, manual).offsetSeconds
        val s = setOf(oa, ob).filter { f(l - it, ctx, manual).offsetSeconds == it }.sortedDescending()
        return when (s.size) {
            1 -> {
                val t = l - s[0]
                Local(t, f(t, ctx, manual), null, false)
            }
            2 -> {
                val early = l - s[0]
                val late = l - s[1]
                val (pick, other) = if (choice == AmbiguityChoice.EARLIER) early to late else late to early
                val r = f(pick, ctx, manual)
                Local(pick, r.copy(flags = r.flags + OffsetFlag.AMBIGUOUS), other, true)
            }
            else -> {
                val t = l - oa
                val r = f(t, ctx, manual)
                Local(t, r.copy(flags = r.flags + OffsetFlag.GAP_SHIFTED), null, false)
            }
        }
    }

    private fun epochOf(local: LocalDateTime): Long =
        local.date.toEpochDays() * DAY + local.hour * SECONDS_PER_HOUR +
            local.minute * SECONDS_PER_MINUTE + local.second

    override fun localToInstant(
        local: LocalDateTime,
        ctx: TzContext,
        choice: AmbiguityChoice,
        manual: ManualOffset?,
    ): LocalResolution {
        val r = resolve(epochOf(local), ctx, choice, manual)
        val alt = r.alt?.let { Instant.fromEpochSeconds(it) }
        // A time on a civil date the zone skipped entirely (Apia 2011-12-30) keeps the shifted result, flagged.
        val offset =
            if (OffsetFlag.GAP_SHIFTED in r.offset.flags && daySkipped(local.date, ctx, manual)) {
                r.offset.copy(flags = r.offset.flags + OffsetFlag.NONEXISTENT_DATE)
            } else {
                r.offset
            }
        return LocalResolution(Instant.fromEpochSeconds(r.epoch), offset, alt)
    }

    /** True when no local time of [date] exists (both the first and the last second fall in a gap). */
    private fun daySkipped(date: LocalDate, ctx: TzContext, manual: ManualOffset?): Boolean {
        val base = date.toEpochDays() * DAY
        val start = resolve(base, ctx, AmbiguityChoice.EARLIER, manual)
        val end = resolve(base + DAY - 1, ctx, AmbiguityChoice.EARLIER, manual)
        return OffsetFlag.GAP_SHIFTED in start.offset.flags && OffsetFlag.GAP_SHIFTED in end.offset.flags
    }

    override fun civilDayOffset(date: LocalDate, ctx: TzContext, manual: ManualOffset?): CivilDayOffset {
        val noon = resolve(date.toEpochDays() * DAY + NOON, ctx, AmbiguityChoice.EARLIER, manual)
        if (OffsetFlag.GAP_SHIFTED in noon.offset.flags && daySkipped(date, ctx, manual)) {
            return CivilDayOffset.Nonexistent
        }
        return CivilDayOffset.Valid(noon.offset)
    }

    private companion object {
        const val DAY = 86_400L
        const val LMT_SECONDS_PER_DEG = 240.0
        const val SECONDS_PER_HOUR = 3600L
        const val SECONDS_PER_MINUTE = 60L
        const val NOON = 43_200L
        const val LMT_BEFORE = -2_208_988_800L // 1900-01-01T00:00:00Z
    }
}
