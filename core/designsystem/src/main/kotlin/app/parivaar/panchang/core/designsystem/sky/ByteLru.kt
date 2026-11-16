package app.parivaar.panchang.core.designsystem.sky

/**
 * Least-recently-used map bounded by total bytes ([sizeOf] per value), not entry count. An entry larger than
 * [maxBytes] on its own is not kept. Thread-safe (every call synchronises on the map).
 */
class ByteLru<K : Any, V : Any>(private val maxBytes: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<K, V>(INITIAL, LOAD, true)
    private var bytes = 0L

    @Synchronized fun get(key: K): V? = map[key]

    @Synchronized fun put(key: K, value: V) {
        val size = sizeOf(value)
        map.remove(key)?.let { bytes -= sizeOf(it) }
        if (size > maxBytes) return
        map[key] = value
        bytes += size
        val it = map.entries.iterator()
        while (bytes > maxBytes && it.hasNext()) {
            val e = it.next()
            bytes -= sizeOf(e.value)
            it.remove()
        }
    }

    @Synchronized fun totalBytes(): Long = bytes

    @Synchronized fun size(): Int = map.size

    private companion object {
        const val INITIAL = 8
        const val LOAD = 0.75f
    }
}
