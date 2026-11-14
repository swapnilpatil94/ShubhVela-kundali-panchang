package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.FamilyDocument
import app.parivaar.panchang.core.data.codec.FamilyFixture
import app.parivaar.panchang.core.data.crypto.CryptoException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.SecureRandom
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SnapshotTest {
    private val rng = SecureRandom()
    private val root: File = createTempDirectory("ppsnap").toFile()
    private val kb = ByteArray(32).also { rng.nextBytes(it) }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @AfterTest fun cleanup() {
        root.deleteRecursively()
    }

    private fun rejected(bytes: ByteArray, g: Int = 3, key: ByteArray = kb): CryptoException.Reason =
        assertFailsWith<CryptoException> { SnapshotFormat.open(key, g, bytes) }.also {
            assertFalse(it.message!!.any { c -> c.isDigit() })
        }.reason

    @Test fun headerLayoutIsExact() {
        val nonce = ByteArray(12) { (0x20 + it).toByte() }
        assertEquals("5050534e" + "01" + "00000003" + "202122232425262728292a2b", hex(SnapshotFormat.header(3, nonce)))
        assertEquals(21, SnapshotFormat.HEADER_BYTES)
        assertEquals(37 + 5, SnapshotFormat.seal(kb, 3, "hello".toByteArray(), rng).size)
    }

    @Test fun roundTripUsesFreshNonces() {
        val a = SnapshotFormat.seal(kb, 3, FrozenFixtures.familyJson, rng)
        val b = SnapshotFormat.seal(kb, 3, FrozenFixtures.familyJson, rng)
        assertFalse(a.copyOfRange(9, 21).contentEquals(b.copyOfRange(9, 21)))
        assertContentEquals(FrozenFixtures.familyJson, SnapshotFormat.open(kb, 3, a))
    }

    @Test fun tamperedSnapshotsFailClosed() {
        val s = SnapshotFormat.seal(kb, 3, "{\"v\":1}".toByteArray(), rng)
        fun flip(i: Int) = s.copyOf().also { it[i] = (it[i].toInt() xor 0xFF).toByte() }
        for (i in 0..3) assertEquals(CryptoException.Reason.FORMAT, rejected(flip(i)))
        assertEquals(CryptoException.Reason.VERSION, rejected(flip(4)))
        assertEquals(CryptoException.Reason.VERSION, rejected(s.copyOf().also { it[4] = 2 }))
        for (i in 5..8) assertEquals(CryptoException.Reason.FORMAT, rejected(flip(i)), "gen byte $i")
        for (i in listOf(9, 20, 21, s.size - 17, s.size - 1)) {
            assertEquals(CryptoException.Reason.AUTH, rejected(flip(i)))
        }
        for (i in 9 until s.size) assertEquals(CryptoException.Reason.AUTH, rejected(flip(i)))
        for (len in (0..minOf(s.size - 1, SnapshotFormat.HEADER_BYTES + 16)) + (s.size - 1)) {
            val short = len < SnapshotFormat.MIN_BYTES
            val want = if (short) CryptoException.Reason.FORMAT else CryptoException.Reason.AUTH
            assertEquals(want, rejected(s.copyOf(len)), "len=$len")
        }
        assertEquals(CryptoException.Reason.AUTH, rejected(s + byteArrayOf(0)))
        assertEquals(CryptoException.Reason.FORMAT, rejected(s, g = 4), "wrong K_b generation")
        val otherKey = ByteArray(32).also { rng.nextBytes(it) }
        assertEquals(CryptoException.Reason.AUTH, rejected(s, key = otherKey))
        val huge = s.copyOf(SnapshotFormat.MAX_BYTES + 1)
        assertEquals(CryptoException.Reason.FORMAT, rejected(huge))
    }

    @Test fun frozenSnapshotDecodesToTheFixtureDocument() {
        FrozenFixtures.writeIfRequested()
        val frozen = FrozenFixtures.snapshotFile.readBytes()
        assertContentEquals(FrozenFixtures.snapshot(), frozen, "format drift: snapshot_v1.bin no longer reproduces")
        val plain = SnapshotFormat.open(FrozenFixtures.SNAPSHOT_KB, FrozenFixtures.SNAPSHOT_GEN, frozen)
        assertContentEquals(FrozenFixtures.familyJson, plain)
        assertEquals(FamilyFixture.doc, FamilyDocument.fromBytes(plain))
    }

    @Test fun writeNowCreatesKbOnceAndReusesIt() = runBlocking {
        val keys = FakeBackupKeyStore()
        val d = Device(File(root, "d"), keys)
        assertFalse(d.snapshots.writeNow(), "locked before open")
        assertEquals(VaultState.Ready, d.vault.open())
        d.seed(FamilyFixture.doc)
        assertTrue(d.snapshots.writeNow())
        assertTrue(d.snapshots.writeNow())
        assertEquals(1, keys.storeCalls)
        assertEquals(setOf("snapshot.bin"), d.files(d.backupDir))
        assertEquals(setOf("dek.1.bin", "kb.1.bin"), d.files(d.keysDir))
        val plain = SnapshotFormat.open(keys.entry!!.second, 1, d.snapshots.snapshotFile.readBytes())
        assertEquals(FamilyFixture.doc, FamilyDocument.fromBytes(plain))
        // the off-device copy went missing: next write rotates instead of writing under an unknown key
        keys.clear()
        assertTrue(d.snapshots.writeNow())
        assertEquals(2, keys.entry!!.first)
        assertEquals(setOf("dek.1.bin", "kb.2.bin"), d.files(d.keysDir))
        assertEquals(2, SnapshotFormat.generationOf(d.snapshots.snapshotFile.readBytes()))
    }

    @Test fun staleAfter24Hours() = runBlocking {
        val d = Device(File(root, "s"), FakeBackupKeyStore())
        d.vault.open()
        assertTrue(d.snapshots.isStale(), "missing snapshot is stale")
        assertTrue(d.snapshots.writeIfStale())
        val written = Instant.fromEpochMilliseconds(d.snapshots.snapshotFile.lastModified())
        d.now = written + 1.hours
        assertFalse(d.snapshots.isStale())
        assertFalse(d.snapshots.writeIfStale())
        d.now = written + 25.hours
        assertTrue(d.snapshots.isStale())
    }

    @Test fun watchRewritesAfterChanges() = runBlocking {
        val d = Device(File(root, "w"), FakeBackupKeyStore())
        d.vault.open()
        val job = launch { d.snapshots.watch() }
        delay(100)
        assertFalse(d.snapshots.snapshotFile.exists(), "no write without a change")
        d.seed(FamilyFixture.doc)
        withTimeout(5_000) { while (!d.snapshots.snapshotFile.exists()) delay(20) }
        job.cancel()
        assertEquals("SnapshotManager(redacted)", d.snapshots.toString())
    }
}
