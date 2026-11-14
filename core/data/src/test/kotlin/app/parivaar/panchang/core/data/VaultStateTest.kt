package app.parivaar.panchang.core.data

import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import app.parivaar.panchang.core.data.keys.DekManager
import app.parivaar.panchang.core.data.keys.FakeKeyWrapper
import app.parivaar.panchang.core.data.repo.ProfileRepositoryImpl
import app.parivaar.panchang.core.data.repo.SettingsRepositoryImpl
import app.parivaar.panchang.core.data.store.InMemoryRecordStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultStateTest {
    private val dir: File = Files.createTempDirectory("pp-keys").toFile()
    private val wrapper = FakeKeyWrapper()
    private val store = InMemoryRecordStore()
    private val rng = SecureRandom()
    private val ok = DataResult.Ok(Unit)

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun snapshotFiles() =
        dir.listFiles()!!.sortedBy { it.name }.joinToString { it.name + it.readBytes().toList() }

    private fun keys() = DekManager(dir, wrapper, rng, retryDelay = {})

    private fun testWiper(k: DekManager) =
        object : DataWiper {
            override suspend fun deleteAll(): DataResult<Unit> {
                dir.listFiles()?.forEach { it.delete() }
                wrapper.delete()
                store.tx { clearAll() }
                k.close()
                return ok
            }
        }

    private class Restorer(private val can: Boolean, private val succeeds: Boolean) : SnapshotRestorer {
        var restored = 0
        override fun canRestore() = can

        override suspend fun restore(): Boolean {
            restored++
            return succeeds
        }
    }

    private fun vault(k: DekManager, restorer: SnapshotRestorer = SnapshotRestorer.None): DataVaultImpl =
         DataVaultImpl(store, k, { testWiper(k) }, restorer)

    private fun profiles(v: DataVaultImpl, k: DekManager) = ProfileRepositoryImpl(store, k.ring, v.state, rng)

    @Test
    fun freshOpenIsReadyAndCreatesFirstKey() = runBlocking {
        val k = keys()
        val v = vault(k)
        assertEquals(VaultState.Opening, v.state.value)
        assertEquals(VaultState.Ready, v.open())
        assertEquals(VaultState.Ready, v.state.value)
        assertTrue(File(dir, "dek.1.bin").isFile)
        assertEquals(ok, profiles(v, k).upsert(F.profileA))
    }

    @Test
    fun reopenWithSameKeysReadsData() = runBlocking {
        val k1 = keys()
        val v1 = vault(k1)
        v1.open()
        profiles(v1, k1).upsert(F.profileA)
        k1.close()
        val k2 = keys()
        val v2 = vault(k2)
        assertEquals(VaultState.Ready, v2.open())
        assertEquals(listOf(F.profileA), profiles(v2, k2).observeAll().first().items)
    }

    @Test
    fun lostKeyIsKeyLostAndTouchesNothing() = runBlocking {
        val k1 = keys()
        val v1 = vault(k1)
        v1.open()
        profiles(v1, k1).upsert(F.profileA)
        k1.close()
        val filesBefore = snapshotFiles()
        val rowsBefore = store.dump()
        wrapper.lost = true
        val k2 = keys()
        val v2 = vault(k2)
        assertEquals(VaultState.KeyLost, v2.open())
        assertEquals(filesBefore, snapshotFiles())
        assertEquals(rowsBefore, store.dump())
        assertEquals(DataResult.Err(DataError.LOCKED), profiles(v2, k2).upsert(F.profileB))
        val settings = SettingsRepositoryImpl(store, k2.ring, v2.state, rng)
        assertEquals(DataResult.Err(DataError.LOCKED), settings.update { it })
    }

    @Test
    fun rowsWithoutKeyFileAreKeyLost() = runBlocking {
        val k1 = keys()
        val v1 = vault(k1)
        v1.open()
        profiles(v1, k1).upsert(F.profileA)
        k1.close()
        File(dir, "dek.1.bin").delete()
        assertEquals(VaultState.KeyLost, vault(keys()).open())
        assertEquals(1, store.dump().size)
    }

    @Test
    fun unavailableTwiceIsKeyUnavailableOnceRetries() = runBlocking {
        val k1 = keys()
        vault(k1).open()
        k1.close()
        wrapper.unavailableCalls = 2
        assertEquals(VaultState.KeyUnavailable, vault(keys()).open())
        wrapper.unavailableCalls = 1
        assertEquals(VaultState.Ready, vault(keys()).open())
        assertTrue(File(dir, "dek.1.bin").isFile)
    }

    @Test
    fun startFreshWipesThenOpensReady() = runBlocking {
        val k1 = keys()
        val v1 = vault(k1)
        v1.open()
        profiles(v1, k1).upsert(F.profileA)
        k1.close()
        wrapper.lost = true
        val k2 = keys()
        val v2 = vault(k2)
        assertEquals(VaultState.KeyLost, v2.open())
        wrapper.lost = false
        assertEquals(ok, v2.startFresh())
        assertEquals(VaultState.Ready, v2.state.value)
        assertEquals(0, store.dump().size)
        assertTrue(profiles(v2, k2).observeAll().first().items.isEmpty())
        assertEquals(ok, profiles(v2, k2).upsert(F.profileB))
        assertFalse(File(dir, "dek.2.bin").exists())
    }

    @Test
    fun startFreshReportsWiperFailureWithoutOpening() = runBlocking {
        val k = keys()
        val failing = DataVaultImpl(store, k, { object : DataWiper {
            override suspend fun deleteAll() = DataResult.Err(DataError.IO)
        } })
        assertEquals(DataResult.Err(DataError.IO), failing.startFresh())
        assertEquals(VaultState.Opening, failing.state.value)
    }

    @Test
    fun restoreHookDecidesStateOnEmptyStore() = runBlocking {
        val good = Restorer(can = true, succeeds = true)
        assertEquals(VaultState.Ready, vault(keys(), good).open())
        assertEquals(1, good.restored)
        assertFalse(File(dir, "dek.1.bin").exists())
        val bad = Restorer(can = true, succeeds = false)
        assertEquals(VaultState.KeyLost, vault(keys(), bad).open())
        val none = Restorer(can = false, succeeds = true)
        assertEquals(VaultState.Ready, vault(keys(), none).open())
        assertEquals(0, none.restored)
    }

    @Test
    fun storeFailureOnOpenIsKeyUnavailableAndNeverWipes() = runBlocking {
        val k1 = keys()
        val v1 = vault(k1)
        v1.open()
        k1.close()
        val broken = object : app.parivaar.panchang.core.data.store.RecordStore {
            override suspend fun <T> tx(block: app.parivaar.panchang.core.data.store.RecordTx.() -> T): T =
                throw IllegalStateException("db newer")
            override val changes = store.changes
            override suspend fun closeAndDelete(deleteFiles: () -> Boolean) = deleteFiles()
            override suspend fun reopen() = Unit
        }
        val v = DataVaultImpl(broken, keys(), { error("never") })
        assertEquals(VaultState.KeyUnavailable, v.open())
        assertTrue(File(dir, "dek.1.bin").isFile)
    }

    @Test
    fun rotationKeepsDataReadable() = runBlocking {
        val k = keys()
        val v = vault(k)
        v.open()
        val repo = profiles(v, k)
        repo.upsert(F.profileA)
        assertEquals(2, k.rotate(store))
        assertFalse(File(dir, "dek.1.bin").exists())
        assertTrue(File(dir, "dek.2.bin").isFile)
        assertEquals(DataResult.Ok(F.profileA), repo.get(F.profileA.id))
        assertEquals(ok, repo.upsert(F.profileB))
        assertEquals(2, repo.observeAll().first().items.size)
    }
}
