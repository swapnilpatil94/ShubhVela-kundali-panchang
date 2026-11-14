package app.parivaar.panchang.core.data.keys

import app.parivaar.panchang.core.data.crypto.CryptoException
import app.parivaar.panchang.core.data.crypto.RowEnvelope
import app.parivaar.panchang.core.data.store.InMemoryRecordStore
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.RecordTx
import app.parivaar.panchang.core.data.store.StoredRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DekManagerTest {
    private val rng = SecureRandom()
    private val root: File = createTempDirectory("ppkeys").toFile()
    private val dir = File(root, "keys")
    private val wrapper = FakeKeyWrapper(rng)
    private val store = InMemoryRecordStore()
    private val delays = mutableListOf<Long>()

    @AfterTest fun cleanup() {
        root.deleteRecursively()
    }

    private fun manager(w: KeyWrapper = wrapper) = DekManager(dir, w, rng) { delays += it }
    private fun files(): Map<String, List<Byte>> =
        (dir.listFiles() ?: emptyArray()).associate { it.name to it.readBytes().toList() }

    private fun id(n: Int) = ByteArray(16) { if (it == 15) n.toByte() else 0x11 }
    private fun payload(n: Int) = """{"name":"Person $n"}""".toByteArray()

    private suspend fun seedRows(m: DekManager, tbl: Int = RowEnvelope.TBL_PROFILE, n: Int = 3) = store.tx {
        for (i in 1..n) put(StoredRow(tbl, id(i), i, 1, RowEnvelope.seal(m.ring, tbl, id(i), 1, payload(i), rng)))
    }

    private suspend fun readAll(m: DekManager): List<String> = store.tx {
        listOf(1, 2, 3, 4).flatMap { list(it) }.map { String(RowEnvelope.open(m.ring, it.tbl, it.id, it.ver, it.blob)) }
    }

    private suspend fun keyIds(): Set<Int> =
        store.tx { listOf(1, 2, 3, 4).flatMap { list(it) }.map { RowEnvelope.keyIdOf(it.blob) }.toSet() }

    @Test fun freshStartCreatesDekGenerationOne() = runBlocking {
        val m = manager()
        assertEquals(KeyState.Empty, m.open(store))
        assertTrue(files().isEmpty(), "open never creates keys")
        assertEquals(KeyState.Ready, m.createFresh())
        assertEquals(listOf("dek.1.bin"), files().keys.toList())
        assertEquals(1, m.ring.currentId)
        seedRows(m)
        val m2 = manager()
        assertEquals(KeyState.Ready, m2.open(store))
        assertEquals((1..3).map { """{"name":"Person $it"}""" }, readAll(m2))
        assertFailsWith<IllegalStateException> { m2.createFresh() }
        assertEquals("DekManager(redacted)", m2.toString())
    }

    @Test fun createFreshKeepsAnExistingKek() = runBlocking {
        wrapper.create()
        assertEquals(KeyState.Ready, manager().createFresh())
        assertEquals(1, wrapper.createCount)
    }

    @Test fun rowsWithoutAnyWrappedDekAreLostNotOverwritten() = runBlocking {
        val m = manager().apply { createFresh() }
        seedRows(m)
        File(dir, "dek.1.bin").delete()
        val rowsBefore = store.dump()
        assertEquals(KeyState.Lost, manager().open(store))
        assertTrue(files().isEmpty())
        assertEquals(rowsBefore, store.dump())
    }

    @Test fun lostKekIsKeyLostAndTouchesNothing() = runBlocking {
        manager().createFresh().also { assertEquals(KeyState.Ready, it) }
        val before = files()
        wrapper.lost = true
        assertEquals(KeyState.Lost, manager().open(store))
        assertEquals(before, files())
        wrapper.lost = false
        wrapper.delete()
        assertEquals(KeyState.Lost, manager().open(store))
        assertEquals(before, files())
        val replaced = FakeKeyWrapper(rng).apply { create() }
        val m = manager(replaced)
        assertEquals(KeyState.Lost, m.open(store))
        assertNull(m.ring.key(1))
        assertEquals(before, files())
    }

    @Test fun damagedKeyFileIsKeyLostAndKept() = runBlocking {
        manager().createFresh()
        val f = File(dir, "dek.1.bin")
        val damaged = f.readBytes().also { it[30] = (it[30].toInt() xor 1).toByte() }
        f.writeBytes(damaged)
        assertEquals(KeyState.Lost, manager().open(store))
        assertContentEquals(damaged, f.readBytes())
        f.writeBytes(damaged.copyOf(70))
        assertEquals(KeyState.Lost, manager().open(store))
        assertTrue(f.exists())
    }

    @Test fun unavailableIsRetriedOnceAfter250ms() = runBlocking {
        manager().createFresh()
        wrapper.unavailableCalls = 2
        assertEquals(KeyState.Unavailable, manager().open(store))
        assertEquals(listOf(250L), delays)
        wrapper.unavailableCalls = 1
        assertEquals(KeyState.Ready, manager().open(store))
        assertEquals(listOf(250L, 250L), delays)
        assertEquals(listOf("dek.1.bin"), files().keys.toList())
    }

    @Test fun createFreshMapsKeystoreErrors() = runBlocking {
        wrapper.unavailableCalls = 1
        assertEquals(KeyState.Unavailable, manager().createFresh())
        wrapper.lost = true
        assertEquals(KeyState.Lost, manager().createFresh())
        assertTrue(files().isEmpty())
        wrapper.lost = false
        assertEquals(KeyState.Ready, manager().createFresh())
    }

    @Test fun rotationReencryptsAndRemovesOldKey() = runBlocking {
        val m = manager().apply { createFresh() }
        seedRows(m)
        seedRows(m, RowEnvelope.TBL_SETTINGS, 1)
        val before = readAll(m)
        val oldKey = m.ring.key(1)!!.copyOf()
        assertEquals(2, m.rotate(store))
        assertEquals(setOf(2), keyIds())
        assertEquals(before, readAll(m))
        assertEquals(listOf("dek.2.bin"), files().keys.toList())
        assertNull(m.ring.key(1))
        assertFalse(oldKey.contentEquals(m.ring.key(2)!!))
        val m2 = manager()
        assertEquals(KeyState.Ready, m2.open(store))
        assertEquals(2, m2.ring.currentId)
        assertEquals(before, readAll(m2))
        assertEquals(3, m2.rotate(store))
        assertEquals(listOf("dek.3.bin"), files().keys.toList())
    }

    @Test fun crashAfterKeyWriteBeforeCommitRecovers() = runBlocking {
        val m = manager().apply { createFresh() }
        seedRows(m)
        val before = readAll(m)
        val failing = FailingTxStore(store)
        assertFailsWith<IOException> { m.rotate(failing) }
        assertEquals(setOf(1), keyIds(), "nothing committed")
        assertEquals(before, readAll(m), "old key still loaded after the failure")
        assertEquals(setOf("dek.1.bin", "dek.2.bin"), files().keys)
        // process restart
        val m2 = manager()
        assertEquals(KeyState.Ready, m2.open(store))
        assertEquals(before, readAll(m2))
        assertEquals(setOf("dek.1.bin", "dek.2.bin"), files().keys, "dek.1 still used by rows")
        assertEquals(3, m2.rotate(store))
        assertEquals(before, readAll(m2))
        assertEquals(setOf("dek.3.bin"), files().keys)
    }

    @Test fun crashAfterCommitBeforeDeletePrunesOnOpen() = runBlocking {
        val m = manager().apply { createFresh() }
        seedRows(m)
        val oldFile = File(dir, "dek.1.bin").readBytes()
        m.rotate(store)
        File(dir, "dek.1.bin").writeBytes(oldFile) // the delete never happened
        File(dir, "dek.3.bin.tmp").writeBytes(byteArrayOf(1, 2)) // interrupted temp write
        val m2 = manager()
        assertEquals(KeyState.Ready, m2.open(store))
        assertEquals(setOf("dek.2.bin"), files().keys)
        assertEquals(3, readAll(m2).size)
    }

    @Test fun tamperedRowKeepsItsKeyFileThroughRotation() = runBlocking {
        val m = manager().apply { createFresh() }
        seedRows(m)
        val victim = store.tx { get(RowEnvelope.TBL_PROFILE, id(2))!! }
        val bad = victim.blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        store.tx { put(StoredRow(victim.tbl, victim.id, victim.pos, victim.ver, bad)) }
        m.rotate(store)
        assertEquals(setOf(1, 2), keyIds())
        assertEquals(setOf("dek.1.bin", "dek.2.bin"), files().keys)
        assertContentEquals(bad, store.tx { get(RowEnvelope.TBL_PROFILE, id(2))!! }.blob)
        assertFailsWith<CryptoException> { RowEnvelope.open(m.ring, 1, id(2), 1, bad) }
        assertNotNull(m.ring.key(1))
    }

    @Test fun closeWipesKeys() = runBlocking {
        val m = manager().apply { createFresh() }
        val k = m.ring.key(1)!!
        k.fill(0)
        assertFalse(m.ring.key(1)!!.all { it == 0.toByte() }, "key() hands out a copy")
        m.close()
        assertNull(m.ring.key(1))
    }

    /** Store whose transactions fail before commit, as a crash would. */
    private class FailingTxStore(private val inner: RecordStore) : RecordStore {
        override val changes: Flow<Int> get() = inner.changes
        override suspend fun closeAndDelete(deleteFiles: () -> Boolean) = inner.closeAndDelete(deleteFiles)
        override suspend fun reopen() = inner.reopen()
        override suspend fun <T> tx(block: RecordTx.() -> T): T = inner.tx {
            block()
            throw IOException("simulated crash")
        }
    }
}
