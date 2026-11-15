package app.parivaar.panchang.feature.calendar.shouldi

import app.parivaar.panchang.engine.calendar.TimeRange

/** Pure interval algebra over [TimeRange] (half-open reading: touching ranges merge, empty ranges are dropped). */
object Intervals {
    /** Sorted, non-empty, merged union. */
    fun merge(ranges: List<TimeRange>): List<TimeRange> {
        val out = ArrayList<TimeRange>()
        for (r in ranges.filter { it.start < it.end }.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && r.start <= last.end) {
                if (r.end > last.end) out[out.size - 1] = TimeRange(last.start, r.end)
            } else {
                out += r
            }
        }
        return out
    }

    /** [from] minus every range of [cut]; result sorted and merged. */
    fun subtract(from: List<TimeRange>, cut: List<TimeRange>): List<TimeRange> {
        var out = merge(from)
        for (c in merge(cut)) {
            out = out.flatMap { s ->
                if (c.end <= s.start || c.start >= s.end) {
                    listOf(s)
                } else {
                    listOfNotNull(
                        if (s.start < c.start) TimeRange(s.start, c.start) else null,
                        if (c.end < s.end) TimeRange(c.end, s.end) else null,
                    )
                }
            }
        }
        return out
    }

    /** [ranges] clipped to [window]; empty results dropped. */
    fun clip(ranges: List<TimeRange>, window: TimeRange): List<TimeRange> = ranges.mapNotNull {
        val s = maxOf(it.start, window.start)
        val e = minOf(it.end, window.end)
        if (s < e) TimeRange(s, e) else null
    }

    /** True when the two ranges share more than a boundary point. */
    fun overlaps(a: TimeRange, b: TimeRange): Boolean = a.start < b.end && b.start < a.end
}
