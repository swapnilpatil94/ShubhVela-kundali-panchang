package app.parivaar.panchang.core.data.store

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaTest {
    private val root = File(System.getProperty("pp.root.dir"), "core/data")

    private fun id(vararg b: Int) = ByteArray(16) { i -> if (i < b.size) b[i].toByte() else 0 }

    private fun row(tbl: Int, id: ByteArray, pos: Int = 0, blob: Int = 1) =
        StoredRow(tbl, id, pos, 1, byteArrayOf(blob.toByte()))

    @Test
    fun createStatementEqualsSchemaFile() {
        assertEquals(Schema.CREATE_V1, File(root, "schemas/v1.sql").readText().trim())
        assertEquals(1, Schema.VERSION)
    }

    @Test
    fun schemaHasNoPersonalColumnsOrIndexes() {
        val sql = Schema.CREATE_V1.lowercase()
        val banned = listOf("name", "date", "time", "created", "updated", "index", "fts")
        for (word in banned) assertFalse(sql.contains(word))
    }

    @Test
    fun migrationKeysAreContiguousAndSchemaFilesExist() {
        val keys = Schema.MIGRATIONS.keys.sorted()
        assertEquals((1 until Schema.VERSION).toList(), keys)
        for (v in 1..Schema.VERSION) assertTrue(File(root, "schemas/v$v.sql").isFile)
    }

    @Test
    fun txCommitsAndRollsBack() = runBlocking {
        val s = InMemoryRecordStore()
        s.tx { put(row(1, id(1))); put(row(1, id(2))) }
        assertFailsWith<IllegalStateException> {
            s.tx { delete(1, id(1)); put(row(1, id(3))); error("boom") }
        }
        s.tx {
            assertEquals(2, count(1))
            assertNotNull(get(1, id(1)))
            assertNull(get(1, id(3)))
        }
    }

    @Test
    fun putReplacesAndOrderIsPosThenIdUnsigned() = runBlocking {
        val s = InMemoryRecordStore()
        s.tx {
            put(row(1, id(0x80), pos = 1))
            put(row(1, id(0x7f), pos = 1))
            put(row(1, id(0xff), pos = 0))
            put(row(2, id(1)))
            put(row(1, id(0x7f), pos = 1, blob = 9))
        }
        val listed = s.tx { list(1) }
        assertEquals(3, listed.size)
        assertContentEquals(id(0xff), listed[0].id)
        assertContentEquals(id(0x7f), listed[1].id)
        assertContentEquals(id(0x80), listed[2].id)
        assertEquals(9, listed[1].blob[0].toInt())
        assertEquals(1, s.tx { count(2) })
    }

    @Test
    fun tablesAreSeparateAndDeleteReportsPresence() = runBlocking {
        val s = InMemoryRecordStore()
        s.tx { put(row(1, id(1))); put(row(3, id(1))) }
        assertTrue(s.tx { delete(1, id(1)) })
        assertFalse(s.tx { delete(1, id(1)) })
        assertNotNull(s.tx { get(3, id(1)) })
        s.tx { clearAll() }
        assertEquals(0, s.tx { count(3) })
    }

    @Test
    fun changesEmitsInitialAndAfterCommitOnly() = runBlocking {
        val s = InMemoryRecordStore()
        val before = s.changes.first()
        runCatching { s.tx { error("x") } }
        assertEquals(before, s.changes.first())
        s.tx { put(row(1, id(1))) }
        assertEquals(before + 1, s.changes.first())
    }

    @Test
    fun storedRowToStringIsRedacted() {
        assertEquals("StoredRow(tbl=1)", row(1, id(5)).toString())
        assertEquals(row(1, id(5)), row(1, id(5)))
    }
}
