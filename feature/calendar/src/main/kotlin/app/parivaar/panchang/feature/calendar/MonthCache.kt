package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import kotlinx.datetime.DayOfWeek

/** What is cached per month: engine output only (the grid and "today" are rebuilt from it, cheaply). */
class CachedMonth(val bundle: MonthBundle, val firstDow: DayOfWeek, val proof: ProofInfo)

/** In-memory LRU (6 by default). Never persisted. Thread-safe. */
class MonthCache(private val capacity: Int = CAPACITY) {
    private val map = object : LinkedHashMap<MonthKey, CachedMonth>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MonthKey, CachedMonth>?): Boolean =
            size > capacity
    }

    @Synchronized fun get(key: MonthKey): CachedMonth? = map[key]

    @Synchronized fun put(key: MonthKey, value: CachedMonth) { map[key] = value }

    @Synchronized fun size(): Int = map.size

    @Synchronized fun clear() = map.clear()

    companion object { const val CAPACITY = 6 }
}
