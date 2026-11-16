package app.parivaar.panchang.core.data.store

import app.parivaar.panchang.core.data.backup.BackupKeyStore
import app.parivaar.panchang.core.data.backup.Device
import app.parivaar.panchang.core.data.backup.FakeBackupKeyStore
import app.parivaar.panchang.core.data.codec.FamilyFixture
import app.parivaar.panchang.core.data.repo.RepoTestKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fable review item 1: a read must never count as a change, or observe flows wake themselves forever. */
class ChangeCounterTest {
    @Test fun readOnlyTransactionsDoNotEmit() = runBlocking {
        val store = InMemoryRecordStore()
        val seen = AtomicInteger()
        val job = launch(Dispatchers.Default) { store.changes.collect { seen.incrementAndGet() } }
        delay(QUIET_MS)
        repeat(20) { store.tx { list(Tbl.PLACE) } }
        store.tx { delete(Tbl.PLACE, ByteArray(16)) } // removes nothing: not a write
        delay(QUIET_MS)
        assertEquals(1, seen.get(), "only the initial emission")
        store.tx { put(StoredRow(Tbl.PLACE, ByteArray(16), 0, 1, byteArrayOf(1))) }
        withTimeout(TIMEOUT_MS) { while (seen.get() < 2) delay(10) }
        job.cancel()
    }

    @Test fun observeFlowIsQuietWithoutWritesAndEmitsOncePerWrite() = runBlocking {
        val kit = RepoTestKit()
        val emissions = AtomicInteger()
        val job = launch(Dispatchers.Default) { kit.places.observeAll().collect { emissions.incrementAndGet() } }
        delay(QUIET_MS)
        val idle = emissions.get()
        println("observe emissions in ${QUIET_MS}ms without writes: $idle")
        assertTrue(idle in 1..2, "observe must not self-trigger (got $idle emissions)")
        kit.places.upsert(FamilyFixture.savedPlace)
        withTimeout(TIMEOUT_MS) { while (emissions.get() < idle + 1) delay(10) }
        delay(QUIET_MS)
        assertEquals(idle + 1, emissions.get(), "a write produces exactly one more emission")
        job.cancel()
    }

    @Test fun snapshotWatchGoesQuietAfterOneRewrite() = runBlocking {
        val root = createTempDirectory("ppwatch").toFile()
        try {
            val keys = CountingKeys(FakeBackupKeyStore())
            val d = Device(root, keys)
            d.vault.open()
            val job = launch(Dispatchers.Default) { d.snapshots.watch() }
            delay(QUIET_MS)
            d.seed(FamilyFixture.doc)
            withTimeout(TIMEOUT_MS) { while (!d.snapshots.snapshotFile.exists()) delay(10) }
            delay(QUIET_MS) // the debounce is 50 ms: a self-triggering watch would rewrite several times here
            val settled = keys.retrieves.get()
            delay(QUIET_MS)
            val extra = keys.retrieves.get() - settled
            println("snapshot key retrievals in a further ${QUIET_MS}ms quiet window: $extra")
            assertEquals(settled, keys.retrieves.get(), "watch() must not rewrite without a change")
            job.cancel()
        } finally {
            root.deleteRecursively()
        }
    }

    private class CountingKeys(private val inner: BackupKeyStore) : BackupKeyStore by inner {
        val retrieves = AtomicInteger()

        override fun retrieve(): Pair<Int, ByteArray>? = inner.retrieve().also { retrieves.incrementAndGet() }
    }

    private companion object {
        const val QUIET_MS = 300L
        const val TIMEOUT_MS = 5_000L
    }
}
