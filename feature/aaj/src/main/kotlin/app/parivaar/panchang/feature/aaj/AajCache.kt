package app.parivaar.panchang.feature.aaj

/** In-memory LRU (8 entries by default). Never persisted. Thread-safe. */
class AajCache<V : Any>(private val capacity: Int = CAPACITY) {
    private val map = object : LinkedHashMap<AajKey, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AajKey, V>?): Boolean = size > capacity
    }

    @Synchronized fun get(key: AajKey): V? = map[key]

    @Synchronized fun put(key: AajKey, value: V) { map[key] = value }

    @Synchronized fun size(): Int = map.size

    @Synchronized fun clear() = map.clear()

    companion object { const val CAPACITY = 8 }
}
