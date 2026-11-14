package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.ImportMode
import app.parivaar.panchang.core.data.TransferSummary
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.FamilyDocument
import app.parivaar.panchang.core.data.codec.FamilyFixture
import app.parivaar.panchang.core.data.crypto.Bytes
import app.parivaar.panchang.core.data.repo.RepoTestKit
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ImportTamperTest {
    private val pass = "family-test-passphrase"

    private class Side {
        val kit = RepoTestKit()
        val transfer = FamilyTransferImpl(kit.store, kit.ring, kit.state, kit.rng, Dispatchers.Default)
        suspend fun doc() = kit.store.tx { FamilyRows.read(this, kit.ring) }
        suspend fun seed(doc: FamilyDocument) = kit.store.tx { FamilyRows.replaceAll(this, kit.ring, kit.rng, doc) }
    }

    private suspend fun sideWith(vararg p: Profile, settings: AppSettings = FamilyFixture.settings) =
        Side().apply { seed(FamilyDocument(p.toList(), emptyList(), emptyList(), settings)) }

    private val exported: ByteArray by lazy {
        runBlocking {
            val src = Side().apply { seed(FamilyFixture.doc) }
            val out = ByteArrayOutputStream()
            assertEquals(DataResult.Ok(TransferSummary(2, 1, 1)), src.transfer.export(out, pass.toCharArray()))
            out.toByteArray()
        }
    }

    /** Imports into a target holding one local profile; asserts the error, an unchanged store, a zeroed passphrase. */
    private fun rejects(want: DataError, input: InputStream, passphrase: String = pass) = runBlocking {
        val target = sideWith(FamilyFixture.profileB)
        val before = target.kit.store.dump()
        val p = passphrase.toCharArray()
        val r = target.transfer.import(input, p, ImportMode.REPLACE_ALL)
        assertEquals(DataResult.Err(want), r)
        assertEquals(before, target.kit.store.dump(), "nothing written on error")
        assertTrue(p.all { it == '\u0000' }, "passphrase zeroed")
    }

    private fun rejects(want: DataError, bytes: ByteArray, passphrase: String = pass) =
        rejects(want, ByteArrayInputStream(bytes), passphrase)

    private fun patched(f: (ByteArray) -> Unit) = exported.copyOf().also(f)
    private fun flip(i: Int) = patched { it[i] = (it[i].toInt() xor 0xFF).toByte() }
    private fun iterations(n: Long) = patched { Bytes.putU32(it, 6, n) }

    @Test fun wrongPassphraseIsAuth() = rejects(DataError.FILE_AUTH, exported, "family-test-passphrasE")

    @Test fun headerFieldsFailClosed() {
        rejects(DataError.FILE_FORMAT, flip(0))
        rejects(DataError.FILE_FORMAT, flip(3))
        rejects(DataError.FILE_VERSION, flip(4))
        rejects(DataError.FILE_VERSION, patched { it[4] = 2 })
        rejects(DataError.FILE_VERSION, patched { it[5] = 2 })
        rejects(DataError.FILE_FORMAT, iterations(599_999))
        rejects(DataError.FILE_FORMAT, iterations(5_000_001))
        rejects(DataError.FILE_AUTH, iterations(600_001)) // in range, but bound by the AAD
        rejects(DataError.FILE_FORMAT, patched { it[10] = 15 })
        rejects(DataError.FILE_AUTH, flip(11)) // salt
    }

    @Test fun bodyBytesFailClosed() {
        for (i in listOf(27, 38, 39, exported.size - 17, exported.size - 1)) rejects(DataError.FILE_AUTH, flip(i))
    }

    @Test fun truncatedEmptyExtendedAndOversizeFailClosed() {
        for (len in (0..minOf(exported.size - 1, ExportFormat.HEADER_BYTES + 16)) + (exported.size - 1)) {
            val want = if (len < ExportFormat.MIN_BYTES) DataError.FILE_FORMAT else DataError.FILE_AUTH
            rejects(want, exported.copyOf(len))
        }
        rejects(DataError.FILE_AUTH, exported + byteArrayOf(0))
        rejects(DataError.FILE_TOO_LARGE, ByteArray(ExportFormat.MAX_BYTES + 1))
        rejects(DataError.FILE_FORMAT, ByteArray(ExportFormat.MAX_BYTES))
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { served += it }
        }
        rejects(DataError.FILE_TOO_LARGE, endless)
        assertTrue(served <= ExportFormat.MAX_BYTES + 1, "reads at most 8 MiB + 1 B")
    }

    @Test fun authenticPlaintextThatDoesNotDecodeIsFormat() {
        val salt = ByteArray(16)
        val nonce = ByteArray(12)
        val two = FamilyFixture.doc.copy(
            profiles = FamilyFixture.doc.profiles.map { it.copy(relation = Relation.SELF) },
        )
        for (plain in listOf("{}".toByteArray(), "not json".toByteArray(), two.toBytes())) {
            val file = ExportFormat.sealWithFixedParams(pass.toCharArray(), plain, salt, nonce, 600_000)
            rejects(DataError.FILE_FORMAT, file)
        }
    }

    @Test fun lockedVaultAndWeakPassphrase() = runBlocking {
        val side = Side().apply { seed(FamilyFixture.doc) }
        val out = ByteArrayOutputStream()
        val short = "short".toCharArray()
        assertEquals(DataResult.Err(DataError.PASSPHRASE_POLICY), side.transfer.export(out, short))
        assertTrue(short.all { it == '\u0000' })
        assertEquals(0, out.size())
        side.kit.state.value = VaultState.KeyLost
        val p = pass.toCharArray()
        assertEquals(DataResult.Err(DataError.LOCKED), side.transfer.export(out, p))
        assertTrue(p.all { it == '\u0000' })
        val q = pass.toCharArray()
        val locked = side.transfer.import(ByteArrayInputStream(exported), q, ImportMode.MERGE)
        assertEquals(DataResult.Err(DataError.LOCKED), locked)
        assertTrue(q.all { it == '\u0000' })
    }

    @Test fun replaceAllRestoresTheExactDocument() = runBlocking {
        val flipped = FamilyFixture.settings.copy(privacyScreen = !FamilyFixture.settings.privacyScreen)
        val target = sideWith(FamilyFixture.profileB, settings = flipped)
        val r = target.transfer.import(ByteArrayInputStream(exported), pass.toCharArray(), ImportMode.REPLACE_ALL)
        assertEquals(DataResult.Ok(TransferSummary(2, 1, 1)), r)
        assertEquals(FamilyFixture.doc, target.doc())
    }

    @Test fun mergeTakesStrictlyLaterRecordsAndKeepsLocalSettings() = runBlocking {
        val a = FamilyFixture.profileA
        val b = FamilyFixture.profileB
        val localA = a.copy(displayName = "Local Alpha", updatedAt = a.updatedAt + 1.days) // local newer: kept
        val localB = b.copy(displayName = "Local Beta", updatedAt = b.updatedAt - 1.days) // local older: replaced
        val localSettings = FamilyFixture.settings.copy(privacyScreen = !FamilyFixture.settings.privacyScreen)
        val target = sideWith(localA, localB, settings = localSettings)
        val r = target.transfer.import(ByteArrayInputStream(exported), pass.toCharArray(), ImportMode.MERGE)
        assertEquals(DataResult.Ok(TransferSummary(1, 1, 1)), r)
        val doc = target.doc()
        assertEquals(listOf(localA, b), doc.profiles)
        assertEquals(FamilyFixture.doc.places, doc.places)
        assertEquals(FamilyFixture.doc.bonds, doc.bonds)
        assertEquals(localSettings, doc.settings)
    }

    @Test fun mergeRejectsASecondSelfAndWritesNothing() = runBlocking {
        val otherSelf = FamilyFixture.profileB.copy(id = FamilyFixture.id(99), relation = Relation.SELF)
        val target = sideWith(otherSelf)
        val before = target.kit.store.dump()
        val r = target.transfer.import(ByteArrayInputStream(exported), pass.toCharArray(), ImportMode.MERGE)
        assertEquals(DataResult.Err(DataError.SELF_EXISTS), r)
        assertEquals(before, target.kit.store.dump())
    }
}
