package app.parivaar.panchang.core.data

import app.parivaar.panchang.core.data.backup.UnavailableBackupKeyStore
import app.parivaar.panchang.core.data.codec.FamilyFixture
import app.parivaar.panchang.core.data.crypto.CryptoException
import app.parivaar.panchang.core.data.crypto.DekRing
import app.parivaar.panchang.core.data.crypto.MutableDekRing
import app.parivaar.panchang.core.data.crypto.RowEnvelope
import app.parivaar.panchang.core.data.keys.DekManager
import app.parivaar.panchang.core.data.keys.FakeKeyWrapper
import app.parivaar.panchang.core.data.repo.ProfileRepositoryImpl
import app.parivaar.panchang.core.data.store.InMemoryRecordStore
import app.parivaar.panchang.core.data.store.RecordStore
import app.parivaar.panchang.core.data.store.RecordTx
import app.parivaar.panchang.core.data.store.StoreClosedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Delete-all is serialized against in-flight writes, and a closing key ring can never yield an all-zero key. */
class WipeRaceTest {
    private val root: File = Files.createTempDirectory("pp-race").toFile()
    private val rng = SecureRandom()

    @AfterTest fun cleanup() {
        root.deleteRecursively()
    }

    /** Store whose next transaction, once [arm]ed, parks inside the store lock until [release]. */
    private class LatchStore(val inner: InMemoryRecordStore) : RecordStore by inner {
        @Volatile private var armed = false
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun arm() {
            armed = true
        }

        override suspend fun <T> tx(block: RecordTx.() -> T): T = inner.tx {
            if (armed) {
                armed = false
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "test latch timed out" }
            }
            block()
        }
    }

    @Test fun writeInFlightDuringWipeLeavesNoRowsAndLaterWritesAreLocked() = runBlocking {
        val keysDir = File(root, "keys")
        val store = LatchStore(InMemoryRecordStore())
        val wrapper = FakeKeyWrapper(rng)
        val dek = DekManager(keysDir, wrapper, rng) { }
        var dbFilesDeleted = false
        lateinit var vault: DataVaultImpl
        val wiper = DataWiperImpl(
            wrapper, keysDir, store, deleteDatabase = { true.also { dbFilesDeleted = true } },
            backupDir = File(root, "backup"), shareCacheDir = File(root, "share"),
            backupKeys = UnavailableBackupKeyStore, dek = dek, lockVault = { vault.markClosed() },
            io = Dispatchers.IO,
        )
        vault = DataVaultImpl(store, dek, { wiper })
        val profiles = ProfileRepositoryImpl(store, dek.ring, vault.state, rng)
        assertEquals(VaultState.Ready, vault.open())
        assertEquals(DataResult.Ok(Unit), profiles.upsert(FamilyFixture.profileA))

        store.arm()
        val write = async(Dispatchers.IO) { profiles.upsert(FamilyFixture.profileB) }
        assertTrue(store.entered.await(10, TimeUnit.SECONDS), "write parked inside the store lock")
        val wipe = async(Dispatchers.IO) { wiper.deleteAll() }
        withTimeout(10_000) {
            while (vault.state.value == VaultState.Ready || !keysDir.listFiles().isNullOrEmpty()) delay(5)
        }
        // The vault is locked first: a new write is refused before it reaches the store.
        val third = FamilyFixture.profileB.copy(id = FamilyFixture.id(7))
        assertEquals(DataResult.Err(DataError.LOCKED), profiles.upsert(third))
        assertTrue(!dbFilesDeleted, "database deletion waits for the in-flight transaction")

        store.release.countDown()
        write.await() // committed before the database was deleted, then erased with it
        assertEquals(DataResult.Ok(Unit), wipe.await())
        assertTrue(dbFilesDeleted)
        assertTrue(store.inner.dump().isEmpty(), "no residual rows after the wipe")

        // A write that passed the state check before the lock still cannot write or recreate the database.
        val stale = ProfileRepositoryImpl(store, dek.ring, MutableStateFlow(VaultState.Ready), rng)
        assertEquals(DataResult.Err(DataError.LOCKED), stale.upsert(third))
        val closed = assertFailsWith<StoreClosedException> { store.tx { count(1) } }
        assertEquals("store closed", closed.message)
        assertTrue(store.inner.dump().isEmpty())

        // Start fresh after the wipe: Ready with an empty store, never KeyLost.
        assertEquals(VaultState.Ready, vault.open())
        assertEquals(0, store.tx { count(1) })
    }

    private val id = ByteArray(16) { 0x5A }
    private val plain = """{"name":"Testperson Alpha"}""".toByteArray()

    @Test fun sealRacingRingCloseUsesTheRealKey() {
        val key = ByteArray(32).also { rng.nextBytes(it) }
        val ring = MutableDekRing().apply { put(1, key.copyOf(), makeCurrent = true) }
        // Deterministic worst case: the ring is closed (keys zeroed) right after the key was fetched.
        val closing = object : DekRing {
            override val currentId: Int = 1
            override fun key(id: Int): ByteArray? = ring.key(id).also { ring.close() }
        }
        val blob = RowEnvelope.seal(closing, 1, id, 1, plain, rng)
        val good = MutableDekRing().apply { put(1, key.copyOf(), makeCurrent = true) }
        assertContentEquals(plain, RowEnvelope.open(good, 1, id, 1, blob))
        val zero = MutableDekRing().apply { put(1, ByteArray(32), makeCurrent = true) }
        assertFailsWith<CryptoException> { RowEnvelope.open(zero, 1, id, 1, blob) }
        assertFailsWith<IllegalStateException> { RowEnvelope.seal(zero, 1, id, 1, plain, rng) }
    }

    @Test fun concurrentSealsAndCloseNeverProduceZeroKeyRows() {
        val key = ByteArray(32).also { rng.nextBytes(it) }
        val good = MutableDekRing().apply { put(1, key.copyOf(), makeCurrent = true) }
        val zero = MutableDekRing().apply { put(1, ByteArray(32), makeCurrent = true) }
        repeat(200) {
            val ring = MutableDekRing().apply { put(1, key.copyOf(), makeCurrent = true) }
            val blobs = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
            val start = CountDownLatch(1)
            val sealers = (1..3).map {
                thread {
                    start.await()
                    repeat(20) {
                        try {
                            blobs += RowEnvelope.seal(ring, 1, id, 1, plain, rng)
                        } catch (_: IllegalStateException) {
                            // ring already closed: refused, never sealed
                        }
                    }
                }
            }
            val closer = thread {
                start.await()
                ring.close()
            }
            start.countDown()
            (sealers + closer).forEach { it.join() }
            for (b in blobs) {
                assertContentEquals(plain, RowEnvelope.open(good, 1, id, 1, b))
                assertFailsWith<CryptoException> { RowEnvelope.open(zero, 1, id, 1, b) }
            }
        }
    }
}
