package app.parivaar.panchang.core.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Test double with the same contract as the SQLite store: atomic tx, ordering, change counter. */
internal class InMemoryRecordStore : RecordStore {
    private val lock = Mutex()
    private val counter = MutableStateFlow(0)
    private var rows = LinkedHashMap<Pair<Int, List<Byte>>, StoredRow>()
    private var closed = false

    override val changes: Flow<Int> get() = counter

    /** Snapshot for tamper tests: direct access to what a stolen database would contain. */
    fun dump(): List<StoredRow> = rows.values.toList()

    override suspend fun <T> tx(block: RecordTx.() -> T): T =
        lock.withLock {
            if (closed) throw StoreClosedException()
            val work = LinkedHashMap(rows)
            val t = Tx(work)
            val r = t.block()
            rows = work
            if (t.wrote) counter.value += 1
            r
        }

    /** Same contract as the SQLite store: waits for in-flight transactions, drops every row, stays closed. */
    override suspend fun closeAndDelete(deleteFiles: () -> Boolean): Boolean =
        lock.withLock {
            closed = true
            rows = LinkedHashMap()
            deleteFiles()
        }

    override suspend fun reopen() {
        lock.withLock { closed = false }
    }

    private class Tx(private val m: LinkedHashMap<Pair<Int, List<Byte>>, StoredRow>) : RecordTx {
        var wrote = false

        private fun k(tbl: Int, id: ByteArray) = tbl to id.toList()

        override fun get(tbl: Int, id: ByteArray) = m[k(tbl, id)]

        override fun list(tbl: Int) = m.values.filter { it.tbl == tbl }.sortedWith(rowOrder)

        override fun put(row: StoredRow) {
            m[k(row.tbl, row.id)] = row
            wrote = true
        }

        override fun delete(tbl: Int, id: ByteArray) = (m.remove(k(tbl, id)) != null).also { if (it) wrote = true }

        override fun count(tbl: Int) = m.values.count { it.tbl == tbl }

        override fun clearAll() {
            m.clear()
            wrote = true
        }
    }
}
