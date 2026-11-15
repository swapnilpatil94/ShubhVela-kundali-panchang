package app.parivaar.panchang.feature.calendar.shouldi

import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlin.time.Instant

enum class Verdict { GOOD_NOW, GOOD_LATER, TOMORROW, NOT_FAVOURED_TODAY, UNAVAILABLE }

/** [best] is the interval to show; [whyIds] are string ids for the expandable explainer. */
data class ShouldIAnswer(val verdict: Verdict, val best: TimeRange?, val whyIds: List<String>)

/**
 * Rule-based "Should I...?" (docs/specs/M09.md). G = (prefer choghadiya + Abhijit) minus avoid windows, clipped to
 * each day's daytime. The M08 header sets are not duplicated: the avoid and good sets live in the rule file.
 * Pure; the clock is the [now] argument. A day the engine could not compute (polar day) is simply not in [days].
 */
object ShouldI {
    const val WHY_TITHI = "why_tithi"
    const val WHY_VAAR = "why_vaar"
    const val WHY_NAKSHATRA = "why_nakshatra"
    const val WHY_ABHIJIT = "why_abhijit"
    const val WHY_CHOGHADIYA_PREFIX = "why_choghadiya_"
    const val WHY_AVOID_PREFIX = "why_avoid_"

    @Suppress("ReturnCount") // one return per verdict branch, in spec order
    fun answer(rule: IntentRule, now: Instant, days: List<PanchangDay>, tithiAtSunrise: Int): ShouldIAnswer {
        val d = days.firstOrNull() ?: return unavailable()
        if (now >= d.nextSunrise) return unavailable()

        val failed = buildList {
            if (tithiAtSunrise in rule.tithiAvoid) add(WHY_TITHI)
            if (rule.vaarPrefer.isNotEmpty() && d.vaar !in rule.vaarPrefer) add(WHY_VAAR)
            if (rule.nakshatraPrefer.isNotEmpty() && nakshatraAtSunrise(d) !in rule.nakshatraPrefer) add(WHY_NAKSHATRA)
        }
        if (failed.isNotEmpty()) return ShouldIAnswer(Verdict.NOT_FAVOURED_TODAY, null, failed)

        val all = days.take(2).flatMap { it.windows }
        val today = good(rule, d, all)
        today.firstOrNull { it.start <= now && now < it.end }?.let {
            return ShouldIAnswer(Verdict.GOOD_NOW, it, why(rule, it, all))
        }
        today.filter { it.start > now }.minByOrNull { it.start }?.let {
            return ShouldIAnswer(Verdict.GOOD_LATER, it, why(rule, it, all))
        }
        days.getOrNull(1)?.let { next -> good(rule, next, all).firstOrNull() }?.let {
            return ShouldIAnswer(Verdict.TOMORROW, it, why(rule, it, all))
        }
        return unavailable()
    }

    private fun unavailable() = ShouldIAnswer(Verdict.UNAVAILABLE, null, emptyList())

    private fun nakshatraAtSunrise(d: PanchangDay): Int? =
        d.limbs[LimbKind.NAKSHATRA]?.firstOrNull { d.sunrise in it }?.index

    private fun isGood(rule: IntentRule, w: Window) =
        (w.kind == WindowKind.CHOGHADIYA && w.code in rule.prefer) || w.kind == WindowKind.ABHIJIT

    private fun avoided(rule: IntentRule, all: List<Window>) =
        all.filter { it.kind in rule.avoid && it.start < it.end }.map { TimeRange(it.start, it.end) }

    private fun good(rule: IntentRule, day: PanchangDay, all: List<Window>): List<TimeRange> {
        val raw = all.filter { isGood(rule, it) }.map { TimeRange(it.start, it.end) }
        val daytime = TimeRange(day.sunrise, day.sunset)
        return Intervals.subtract(Intervals.clip(Intervals.merge(raw), daytime), avoided(rule, all))
    }

    private fun why(rule: IntentRule, best: TimeRange, all: List<Window>): List<String> {
        val ids = LinkedHashSet<String>()
        val span = TimeRange(best.start, best.end)
        for (w in all.filter { isGood(rule, it) && Intervals.overlaps(span, TimeRange(it.start, it.end)) }) {
            ids += if (w.kind == WindowKind.ABHIJIT) WHY_ABHIJIT else WHY_CHOGHADIYA_PREFIX + w.code!!.lowercase()
        }
        // an avoid window that cut into a good window next to the best interval is named so the trim is explained
        val goodSpans = Intervals.merge(
            all.filter { isGood(rule, it) }.map { TimeRange(it.start, it.end) },
        ).filter { Intervals.overlaps(it, span) || it.end == span.start || it.start == span.end }
        val cutting = all.filter { w ->
            w.kind in rule.avoid && goodSpans.any { g -> Intervals.overlaps(g, TimeRange(w.start, w.end)) }
        }
        for (w in cutting) {
            ids += WHY_AVOID_PREFIX + w.kind.name.lowercase()
        }
        return ids.toList()
    }
}
