package app.parivaar.panchang.core.data

import app.parivaar.panchang.core.data.backup.BackupKeyStore
import app.parivaar.panchang.core.data.keys.DekManager
import app.parivaar.panchang.core.data.keys.FakeKeyWrapper
import app.parivaar.panchang.core.data.keys.KeyWrapper
import app.parivaar.panchang.core.data.store.InMemoryRecordStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataWiperTest {
    private val root: File = Files.createTempDirectory("pp-wipe").toFile()
    private val keysDir = File(root, "nobackup/keys")
    private val backupDir = File(root, "files/backup")
    private val shareDir = File(root, "cache/share")
    private val dbFile = File(root, "databases/pp_family.db")
    private val geoDb = File(root, "databases/geo_places.db")
    private val fake = FakeKeyWrapper()
    private val events = mutableListOf<String>()
    private val dbSuffixes = listOf("", "-journal", "-wal", "-shm")
    private var stateAfter: Boolean = false
    private val store = InMemoryRecordStore()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private val wrapper =
        object : KeyWrapper by fake {
            override fun delete() {
                events += "kek"
                fake.delete()
            }
        }

    private val backupKeys =
        object : BackupKeyStore {
            override fun isAvailable() = true

            override fun store(gen: Int, kb: ByteArray) = Unit

            override fun retrieve(): Pair<Int, ByteArray>? = null

            override fun clear() {
                events += "clearBackupKey"
                assertTrue(shareDir.listFiles().isNullOrEmpty())
            }
        }

    private fun touch(f: File) {
        f.parentFile!!.mkdirs()
        f.writeText("x")
    }

    private fun populate(): DekManager {
        val dek = DekManager(keysDir, wrapper, SecureRandom(), retryDelay = {})
        runBlocking { dek.createFresh() }
        events.clear()
        listOf(
            "kb.1.bin", "kb.2.bin.tmp", "dek.2.bin.tmp",
        ).forEach { touch(File(keysDir, it)) }
        listOf("snapshot.bin", "snapshot.bin.tmp").forEach { touch(File(backupDir, it)) }
        touch(File(shareDir, "export.ppx"))
        touch(File(shareDir, "sub/nested.ppx"))
        listOf("", "-journal", "-wal", "-shm").forEach { touch(File(dbFile.path + it)) }
        touch(geoDb)
        return dek
    }

    private fun wiper(dek: DekManager) =
        DataWiperImpl(
            wrapper = wrapper,
            keysDir = keysDir,
            store = store,
            deleteDatabase = {
                events += "deleteDb"
                assertTrue(keysDir.listFiles().isNullOrEmpty(), "keys deleted before the database")
                dbSuffixes.map { File(dbFile.path + it) }.all { !it.exists() || it.delete() }
            },
            backupDir = backupDir,
            shareCacheDir = shareDir,
            backupKeys = backupKeys,
            dek = dek,
            lockVault = {
                events += "lock"
                stateAfter = true
            },
            io = Dispatchers.Unconfined,
        )

    @Test
    fun deletesEverythingInOrderAndIsIdempotent() = runBlocking {
        val dek = populate()
        assertTrue(File(keysDir, "dek.1.bin").isFile)
        val w = wiper(dek)
        assertEquals(DataResult.Ok(Unit), w.deleteAll())
        assertEquals(listOf("lock", "kek", "deleteDb", "clearBackupKey"), events)
        assertTrue(keysDir.listFiles().isNullOrEmpty())
        assertTrue(backupDir.listFiles().isNullOrEmpty())
        assertTrue(shareDir.listFiles().isNullOrEmpty())
        listOf("", "-journal", "-wal", "-shm").forEach { assertFalse(File(dbFile.path + it).exists()) }
        assertTrue(geoDb.isFile, "non-personal geo database is kept")
        assertFalse(fake.exists())
        assertFailsWith<IllegalStateException> { dek.ring.currentId }
        assertTrue(stateAfter)
        assertEquals(DataResult.Ok(Unit), w.deleteAll())
    }

    @Test
    fun continuesPastKeystoreFailureAndReportsFirstError() = runBlocking {
        val dek = populate()
        fake.unavailableCalls = 1
        assertEquals(DataResult.Err(DataError.KEY_UNAVAILABLE), wiper(dek).deleteAll())
        assertTrue(keysDir.listFiles().isNullOrEmpty())
        assertTrue(backupDir.listFiles().isNullOrEmpty())
        assertTrue(shareDir.listFiles().isNullOrEmpty())
        assertFalse(dbFile.exists())
        assertTrue("clearBackupKey" in events)
    }

    @Test
    fun lostKeyCountsAsAlreadyDeleted() = runBlocking {
        val dek = populate()
        fake.lost = true
        assertEquals(DataResult.Ok(Unit), wiper(dek).deleteAll())
    }

    @Test
    fun storeFailureIsIoButLaterStepsStillRun() = runBlocking {
        val dek = populate()
        val w =
            DataWiperImpl(
                wrapper, keysDir, store = store, deleteDatabase = { error("x") }, backupDir = backupDir,
                shareCacheDir = shareDir, backupKeys = backupKeys, dek = dek, io = Dispatchers.Unconfined,
            )
        assertEquals(DataResult.Err(DataError.IO), w.deleteAll())
        assertTrue(backupDir.listFiles().isNullOrEmpty())
        assertTrue("clearBackupKey" in events)
        assertEquals(InMemoryRecordStore().dump().size, 0)
    }
}
