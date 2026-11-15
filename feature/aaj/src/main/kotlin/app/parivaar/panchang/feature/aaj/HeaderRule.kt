package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlin.time.Instant

/** Header rule v1 (docs/specs/M08.md; the G and I sets are product rules, VERIFY with owner). Pure. */
object HeaderRule {
    val INTERVALS: Set<WindowKind> = setOf(
        WindowKind.RAHU_KAAL, WindowKind.YAMAGANDA, WindowKind.GULIKA,
        WindowKind.DURMUHURTA, WindowKind.VARJYAM, WindowKind.BHADRA,
    )
    val GOOD_CODES: Set<String> = setOf("AMRIT", "SHUBH", "LABH")

    private data class Span(val start: Instant, val end: Instant)

    @Suppress("ReturnCount") // one return per rule branch, in rule order
    fun evaluate(now: Instant, days: List<PanchangDay>): Header {
        val idx = days.indexOfFirst { it.sunrise <= now && now < it.nextSunrise }
        if (idx < 0) return Header.Unavailable
        val a = days[idx]
        val scope = listOfNotNull(a, days.getOrNull(idx + 1))
        val windows = scope.flatMap { it.windows }
        val avoid = windows.filter { it.kind in INTERVALS && it.start < it.end }
        val good = goodIntervals(windows, avoid)

        val overlapping = avoid.filter { it.start <= now && now < it.end }
        if (overlapping.isNotEmpty()) {
            val latest = overlapping.maxWith(compareBy<Window> { it.end }.thenByDescending { it.kind.ordinal })
            val nextGood = good.filter { it.start >= latest.end }.minOfOrNull { it.start }
            return Header.WaitUntil(latest.kind, latest.end, nextGood)
        }
        good.firstOrNull { it.start <= now && now < it.end }?.let { return Header.GoodUntil(it.end) }
        good.filter { it.start > now }.minByOrNull { it.start }?.let {
            if (it.start < a.sunset) return Header.NextGood(it.start, it.end)
        }
        val tomorrow = good.filter { it.start >= a.nextSunrise }.minOfOrNull { it.start }
        return Header.TomorrowFrom(tomorrow)
    }

    private fun goodIntervals(windows: List<Window>, avoid: List<Window>): List<Span> {
        val g = windows.filter {
            (it.kind == WindowKind.CHOGHADIYA && it.code in GOOD_CODES) || it.kind == WindowKind.ABHIJIT
        }.map { Span(it.start, it.end) }.sortedBy { it.start }
        val merged = ArrayList<Span>()
        for (s in g) {
            val last = merged.lastOrNull()
            if (last != null && s.start <= last.end) {
                if (s.end > last.end) merged[merged.size - 1] = Span(last.start, s.end)
            } else {
                merged += s
            }
        }
        var out: List<Span> = merged
        for (i in avoid.sortedBy { it.start }) {
            out = out.flatMap { s ->
                if (i.end <= s.start || i.start >= s.end) {
                    listOf(s)
                } else {
                    listOfNotNull(
                        if (s.start < i.start) Span(s.start, i.start) else null,
                        if (i.end < s.end) Span(i.end, s.end) else null,
                    )
                }
            }
        }
        return out.sortedBy { it.start }
    }
}
