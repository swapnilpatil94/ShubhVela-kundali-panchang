package app.parivaar.panchang.core.data.crypto

/**
 * Data-encryption keys by generation id g >= 1 (spec section 4 "Hierarchy", "Rotation"). Rows name the
 * key they were sealed under; new rows use [currentId].
 */
internal interface DekRing {
    val currentId: Int

    /**
     * A fresh copy of key [id] (or null), taken under the ring lock so a concurrent [AutoCloseable.close] can never
     * zero it mid-use. The caller wipes the copy after use.
     */
    fun key(id: Int): ByteArray?
}

/** Thread-safe in-memory [DekRing]. [close] wipes every key; afterwards the ring is empty. */
internal class MutableDekRing : DekRing, AutoCloseable {
    private val lock = Any()
    private val keys = HashMap<Int, ByteArray>()
    private var current = 0

    override val currentId: Int
        get() = synchronized(lock) {
            check(current >= 1) { "no current key" }
            current
        }

    override fun key(id: Int): ByteArray? = synchronized(lock) { keys[id]?.copyOf() }

    val ids: Set<Int> get() = synchronized(lock) { keys.keys.toSet() }

    /** Takes ownership of [key] (no copy). A key already stored under [id] is wiped and replaced. */
    fun put(id: Int, key: ByteArray, makeCurrent: Boolean) {
        require(id >= 1) { "bad key id" }
        require(key.size == AesGcm.KEY_BYTES) { "bad key length" }
        synchronized(lock) {
            val old = keys.put(id, key)
            if (old !== key) Bytes.wipe(old)
            if (makeCurrent) current = id
        }
    }

    /** Makes the stored key [id] the one new rows are sealed under. */
    fun makeCurrent(id: Int) {
        synchronized(lock) {
            check(keys.containsKey(id)) { "unknown key id" }
            current = id
        }
    }

    /** Wipes and forgets the key [id]; the current key cannot be removed. */
    fun remove(id: Int) {
        synchronized(lock) {
            check(id != current) { "cannot remove current key" }
            Bytes.wipe(keys.remove(id))
        }
    }

    override fun close() {
        synchronized(lock) {
            keys.values.forEach { Bytes.wipe(it) }
            keys.clear()
            current = 0
        }
    }

    override fun toString(): String = "DekRing(redacted)"
}
