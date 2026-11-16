package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.model.profile.RecordId

/** Cache key (rule 10): profile, its edit time, calc settings, noon chart and the home place. */
data class BundleKey(
    val profileId: RecordId,
    val updatedAtMs: Long,
    val calcHash: Int,
    val noon: Boolean,
    val homePlaceId: RecordId?,
) {
    override fun toString(): String = "BundleKey(redacted)"
}

/** In-memory LRU of [capacity] values (Kundli bundles). Never on disk; evicted on profile delete and vault lock. */
class KundliCache<V : Any>(private val capacity: Int = 8) {
    private val map = object : LinkedHashMap<BundleKey, V>(capacity, LOAD, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BundleKey, V>?): Boolean = size > capacity
    }

    @Synchronized fun get(k: BundleKey): V? = map[k]

    @Synchronized fun put(k: BundleKey, v: V) {
        map[k] = v
    }

    @Synchronized fun evict(id: RecordId) {
        map.keys.removeAll { it.profileId == id }
    }

    @Synchronized fun clear() = map.clear()

    @Synchronized fun size(): Int = map.size

    private companion object {
        const val LOAD = 0.75f
    }
}
