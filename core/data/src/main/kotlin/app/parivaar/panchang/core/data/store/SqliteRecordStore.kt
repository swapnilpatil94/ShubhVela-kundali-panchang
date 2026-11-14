package app.parivaar.panchang.core.data.store

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Platform SQLite store (file `pp_family.db`, secure_delete on). Thin by design: all crypto and decoding happen above.
 * Not exercised by JVM unit tests (no SQLite in the JVM unit-test classpath); see the M06b device run list.
 */
internal class SqliteRecordStore(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RecordStore {
    private val helper = Helper(context.applicationContext)
    private val lock = Mutex()
    private val counter = MutableStateFlow(0)
    private var closed = false

    override val changes: Flow<Int> get() = counter

    override suspend fun <T> tx(block: RecordTx.() -> T): T =
        lock.withLock {
            if (closed) throw StoreClosedException()
            val result =
                withContext(io) {
                    val db = helper.writableDatabase
                    db.beginTransaction()
                    try {
                        val r = SqliteTx(db).block()
                        db.setTransactionSuccessful()
                        r
                    } finally {
                        db.endTransaction()
                    }
                }
            counter.value += 1
            result
        }

    override suspend fun closeAndDelete(deleteFiles: () -> Boolean): Boolean =
        lock.withLock {
            closed = true
            withContext(io) {
                helper.close()
                deleteFiles()
            }
        }

    override suspend fun reopen() {
        lock.withLock { closed = false }
    }

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, Schema.DB_NAME, null, Schema.VERSION) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(Schema.CREATE_V1)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // The helper already wraps this call in one transaction.
            for (from in oldVersion until newVersion) {
                val steps = Schema.MIGRATIONS[from] ?: throw SQLiteException("Missing migration")
                steps.forEach(db::execSQL)
            }
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw SQLiteException("Database is newer than this app")
        }
    }

    private class SqliteTx(private val db: SQLiteDatabase) : RecordTx {
        // Ids are bound as a hex literal built from bytes: only [0-9a-f] can reach the SQL text.
        private fun key(tbl: Int, id: ByteArray) = "tbl=$tbl AND id=x'${hex(id)}'"

        private fun hex(b: ByteArray): String {
            val sb = StringBuilder(b.size * 2)
            for (v in b) {
                val u = v.toInt() and BYTE_MASK
                sb.append(DIGITS[u ushr NIBBLE_BITS]).append(DIGITS[u and NIBBLE_MASK])
            }
            return sb.toString()
        }

        private fun rows(where: String): List<StoredRow> =
            db.query(Schema.TABLE, COLUMNS, where, null, null, null, "pos ASC, id ASC").use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            StoredRow(
                                c.getInt(C_TBL),
                                c.getBlob(C_ID),
                                c.getInt(C_POS),
                                c.getInt(C_VER),
                                c.getBlob(C_BLOB),
                            ),
                        )
                    }
                }
            }

        override fun get(tbl: Int, id: ByteArray): StoredRow? = rows(key(tbl, id)).firstOrNull()

        override fun list(tbl: Int): List<StoredRow> = rows("tbl=$tbl")

        override fun put(row: StoredRow) {
            val v =
                ContentValues().apply {
                    put("tbl", row.tbl)
                    put("id", row.id)
                    put("pos", row.pos)
                    put("ver", row.ver)
                    put("blob", row.blob)
                }
            if (db.insertWithOnConflict(Schema.TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE) < 0) {
                throw SQLiteException("Write failed")
            }
        }

        override fun delete(tbl: Int, id: ByteArray): Boolean = db.delete(Schema.TABLE, key(tbl, id), null) > 0

        override fun count(tbl: Int): Int = DatabaseUtils.queryNumEntries(db, Schema.TABLE, "tbl=$tbl").toInt()

        override fun clearAll() {
            db.delete(Schema.TABLE, null, null)
        }

        private companion object {
            val COLUMNS = arrayOf("tbl", "id", "pos", "ver", "blob")
            const val DIGITS = "0123456789abcdef"
            const val BYTE_MASK = 0xff
            const val NIBBLE_MASK = 0xf
            const val NIBBLE_BITS = 4
            const val C_TBL = 0
            const val C_ID = 1
            const val C_POS = 2
            const val C_VER = 3
            const val C_BLOB = 4
        }
    }
}
