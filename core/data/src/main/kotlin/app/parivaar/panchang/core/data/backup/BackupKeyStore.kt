package app.parivaar.panchang.core.data.backup

/**
 * Off-device holder of the backup key K_b (spec section 3, section 5; D4). A real implementation (Block Store,
 * M06b) is used only if it is end-to-end encrypted. [store] copies [kb]; [retrieve] returns a fresh copy that
 * the caller wipes. Holds one generation at a time.
 */
internal interface BackupKeyStore {
    fun isAvailable(): Boolean
    fun store(gen: Int, kb: ByteArray)
    fun retrieve(): Pair<Int, ByteArray>?
    fun clear()
}

/** M06 binding: no backup key store, so no `snapshot.bin` is ever written (D4). */
internal object UnavailableBackupKeyStore : BackupKeyStore {
    override fun isAvailable(): Boolean = false

    override fun store(gen: Int, kb: ByteArray) {
        throw IllegalStateException("backup key store unavailable")
    }

    override fun retrieve(): Pair<Int, ByteArray>? = null

    override fun clear() = Unit
}
