package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.FamilyFixture
import app.parivaar.panchang.core.data.crypto.CryptoException
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MASTER "snapshot restore with a simulated new device", JVM substitute for the instrumented test (D12). */
class NewDeviceRestoreTest {
    private val root: File = createTempDirectory("ppnew").toFile()

    @AfterTest fun cleanup() {
        root.deleteRecursively()
    }

    private fun deviceA(keys: BackupKeyStore = FakeBackupKeyStore()) = Device(File(root, "a"), keys)

    private suspend fun writtenOnA(): Device = deviceA().apply {
        assertEquals(VaultState.Ready, vault.open())
        seed(FamilyFixture.doc)
        assertTrue(snapshots.writeNow())
    }

    private fun deviceBFrom(a: Device, keys: BackupKeyStore = (a.backupKeys as FakeBackupKeyStore).copy()): Device {
        val b = Device(File(root, "b"), keys)
        b.backupDir.mkdirs()
        a.snapshots.snapshotFile.copyTo(b.snapshots.snapshotFile)
        return b
    }

    @Test fun newDeviceRestoresEqualDataWithFreshKeys() = runBlocking {
        val a = writtenOnA()
        val keysA = a.backupKeys as FakeBackupKeyStore
        assertEquals(1, keysA.entry!!.first)
        assertEquals(setOf("dek.1.bin", "kb.1.bin"), a.files(a.keysDir))
        val oldKb = keysA.entry!!.second.copyOf()

        val b = deviceBFrom(a)
        assertTrue(b.snapshots.canRestore())
        assertEquals(VaultState.Ready, b.vault.open())
        assertEquals(VaultState.Ready, b.vault.state.value)
        assertEquals(FamilyFixture.doc, b.readDoc())
        assertFalse(a.dek.ring.key(1)!!.contentEquals(b.dek.ring.key(1)!!), "B has its own DEK")

        val keysB = b.backupKeys as FakeBackupKeyStore
        assertEquals(2, keysB.entry!!.first, "K_b rotated to generation 2")
        assertEquals(setOf("dek.1.bin", "kb.2.bin"), b.files(b.keysDir))
        val newSnapshot = b.snapshots.snapshotFile.readBytes()
        assertEquals(2, SnapshotFormat.generationOf(newSnapshot))
        val e = assertFailsWith<CryptoException> { SnapshotFormat.open(oldKb, 2, newSnapshot) }
        assertEquals(CryptoException.Reason.AUTH, e.reason)
        val plain = SnapshotFormat.open(keysB.entry!!.second, 2, newSnapshot)
        assertEquals(FamilyFixture.doc, app.parivaar.panchang.core.data.codec.FamilyDocument.fromBytes(plain))
    }

    @Test fun damagedSnapshotFailsClosedAndKeepsSnapshotAndKey() = runBlocking {
        val a = writtenOnA()
        val b = deviceBFrom(a)
        val f = b.snapshots.snapshotFile
        val damaged = f.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        f.writeBytes(damaged)
        val keysB = b.backupKeys as FakeBackupKeyStore
        val kbBefore = keysB.entry!!.second.copyOf()
        assertEquals(VaultState.KeyLost, b.vault.open())
        assertContentEquals(damaged, f.readBytes())
        assertEquals(1, keysB.entry!!.first)
        assertContentEquals(kbBefore, keysB.entry!!.second)
        assertTrue(b.files(b.keysDir).isEmpty(), "new DEK deleted")
        assertTrue(b.store.dump().isEmpty(), "no partial rows")
    }

    @Test fun keystoreFailureDuringRestoreCleansUp() = runBlocking {
        val a = writtenOnA()
        val b = deviceBFrom(a)
        b.wrapper.lost = true
        assertEquals(VaultState.KeyLost, b.vault.open())
        assertTrue(b.files(b.keysDir).isEmpty())
        assertTrue(b.store.dump().isEmpty())
        assertTrue(b.snapshots.snapshotFile.isFile)
        // the user retries once the keystore works: restore succeeds
        b.wrapper.lost = false
        assertEquals(VaultState.Ready, b.vault.open())
        assertEquals(FamilyFixture.doc, b.readDoc())
    }

    @Test fun unavailableBackupKeyStoreWritesNoSnapshot() = runBlocking {
        val a = deviceA(UnavailableBackupKeyStore)
        assertEquals(VaultState.Ready, a.vault.open())
        a.seed(FamilyFixture.doc)
        assertFalse(a.snapshots.writeNow())
        assertFalse(a.snapshots.writeIfStale())
        assertFalse(a.backupDir.exists())
        assertEquals(setOf("dek.1.bin"), a.files(a.keysDir))
        a.snapshots.watch() // returns at once
        val b = Device(File(root, "b"), UnavailableBackupKeyStore)
        assertFalse(b.snapshots.canRestore())
        assertEquals(VaultState.Ready, b.vault.open())
        assertTrue(b.readDoc().profiles.isEmpty())
    }
}
