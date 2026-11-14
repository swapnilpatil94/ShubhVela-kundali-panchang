package app.parivaar.panchang.core.data.store

import kotlinx.coroutines.flow.Flow

/** Table ids (clear column `tbl`). */
internal object Tbl {
    const val PROFILE = 1
    const val PLACE = 2
    const val BOND = 3
    const val SETTINGS = 4
}

/** One encrypted row. [blob] is the sealed envelope; [id] is 16 random bytes (zeros for settings). */
internal class StoredRow(val tbl: Int, val id: ByteArray, val pos: Int, val ver: Int, val blob: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is StoredRow && tbl == other.tbl && pos == other.pos && ver == other.ver &&
            id.contentEquals(other.id) && blob.contentEquals(other.blob)

    override fun hashCode(): Int = 31 * (31 * (31 * tbl + pos) + ver) + id.contentHashCode()

    override fun toString(): String = "StoredRow(tbl=$tbl)"
}

internal interface RecordTx {
    fun get(tbl: Int, id: ByteArray): StoredRow?

    /** Rows of [tbl] ordered by pos, then id (unsigned bytes). */
    fun list(tbl: Int): List<StoredRow>

    /** Inserts or replaces by (tbl, id). */
    fun put(row: StoredRow)

    fun delete(tbl: Int, id: ByteArray): Boolean

    fun count(tbl: Int): Int

    fun clearAll()
}

/** Thrown by [RecordStore.tx] after [RecordStore.closeAndDelete] until [RecordStore.reopen]. Static message. */
internal class StoreClosedException : IllegalStateException("store closed")

internal interface RecordStore {
    /**
     * Runs [block] atomically: all effects commit when it returns, none when it throws.
     * Throws [StoreClosedException] while the store is closed.
     */
    suspend fun <T> tx(block: RecordTx.() -> T): T

    /**
     * Delete-all step: waits for any in-flight [tx] (same lock), closes the database, runs [deleteFiles] and keeps
     * the store closed, so no later [tx] can recreate the database or write a row until [reopen].
     */
    suspend fun closeAndDelete(deleteFiles: () -> Boolean): Boolean

    /** Allows transactions again (called when the vault opens). */
    suspend fun reopen()

    /** Emits once on collection and again after every committed transaction. The value is a change counter. */
    val changes: Flow<Int>
}

/** Ordering shared by every implementation: pos, then id as unsigned bytes. */
internal val rowOrder: Comparator<StoredRow> =
    Comparator<StoredRow> { a, b -> a.pos.compareTo(b.pos) }.thenComparator { a, b -> compareIds(a.id, b.id) }

internal fun compareIds(a: ByteArray, b: ByteArray): Int {
    val n = minOf(a.size, b.size)
    for (i in 0 until n) {
        val c = (a[i].toInt() and 0xff).compareTo(b[i].toInt() and 0xff)
        if (c != 0) return c
    }
    return a.size.compareTo(b.size)
}
