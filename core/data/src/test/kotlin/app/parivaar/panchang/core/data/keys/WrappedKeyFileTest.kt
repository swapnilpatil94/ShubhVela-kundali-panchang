package app.parivaar.panchang.core.data.keys

import app.parivaar.panchang.core.data.crypto.CryptoException
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

class WrappedKeyFileTest {
    private val rng = SecureRandom()
    private val wrapper = FakeKeyWrapper(rng).apply { create() }
    private val dek = ByteArray(32).also { rng.nextBytes(it) }
    private val dir: File = createTempDirectory("ppwk").toFile()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @AfterTest fun cleanup() {
        dir.deleteRecursively()
    }

    private fun sealed(purpose: Int = WrappedKeyFile.PURPOSE_DEK, g: Int = 1) =
        WrappedKeyFile.seal(wrapper, purpose, g, dek)

    @Test fun headerLayoutIsExact() {
        assertEquals("5050574b" + "01" + "01" + "00000001" + "01", hex(WrappedKeyFile.header(1, 1)))
        assertEquals("5050574b" + "01" + "02" + "00000203" + "01", hex(WrappedKeyFile.header(2, 0x203)))
        assertEquals(11, WrappedKeyFile.HEADER_BYTES)
        assertEquals(71, sealed().size)
        assertEquals("dek.1.bin", WrappedKeyFile.fileName(1, 1))
        assertEquals("kb.12.bin", WrappedKeyFile.fileName(2, 12))
        assertFailsWith<IllegalArgumentException> { WrappedKeyFile.header(3, 1) }
        assertFailsWith<IllegalArgumentException> { WrappedKeyFile.header(1, 0) }
    }

    @Test fun roundTrip() {
        val b = sealed(g = 5)
        assertContentEquals(dek, WrappedKeyFile.open(wrapper, b, WrappedKeyFile.PURPOSE_DEK, 5))
        assertContentEquals(dek, WrappedKeyFile.open(wrapper, sealed(2, 3), WrappedKeyFile.PURPOSE_KB, 3))
    }

    private fun headerReject(b: ByteArray, purpose: Int = 1, g: Int = 1): CryptoException.Reason {
        val e = assertFailsWith<CryptoException> { WrappedKeyFile.open(wrapper, b, purpose, g) }
        assertFalse(e.message!!.any { it.isDigit() })
        return e.reason
    }

    @Test fun tamperedFilesFailClosed() {
        val b = sealed()
        fun flip(i: Int) = b.copyOf().also { it[i] = (it[i].toInt() xor 0xFF).toByte() }
        assertEquals(CryptoException.Reason.FORMAT, headerReject(flip(0)))
        assertEquals(CryptoException.Reason.FORMAT, headerReject(flip(3)))
        assertEquals(CryptoException.Reason.VERSION, headerReject(flip(4)))
        assertEquals(CryptoException.Reason.FORMAT, headerReject(flip(5)))
        for (i in 6..9) assertEquals(CryptoException.Reason.FORMAT, headerReject(flip(i)))
        assertEquals(CryptoException.Reason.VERSION, headerReject(flip(10)))
        for (i in listOf(11, 22, 23, b.size - 17, b.size - 1)) {
            assertFailsWith<KeyLostException> { WrappedKeyFile.open(wrapper, flip(i), 1, 1) }
        }
        for (i in 11 until b.size) assertFailsWith<KeyLostException> { WrappedKeyFile.open(wrapper, flip(i), 1, 1) }
        val v2 = b.copyOf().also { it[4] = 2 }
        assertEquals(CryptoException.Reason.VERSION, headerReject(v2))
    }

    @Test fun truncatedExtendedOrMislabelledFailClosed() {
        val b = sealed()
        for (len in (0..minOf(b.size - 1, WrappedKeyFile.HEADER_BYTES + 16)) + (b.size - 1)) {
            assertEquals(CryptoException.Reason.FORMAT, headerReject(b.copyOf(len)), "len=$len")
        }
        assertEquals(CryptoException.Reason.FORMAT, headerReject(b + byteArrayOf(0)))
        assertEquals(CryptoException.Reason.FORMAT, headerReject(b, purpose = 2))
        assertEquals(CryptoException.Reason.FORMAT, headerReject(b, g = 2))
    }

    @Test fun wrongKekIsKeyLost() {
        val b = sealed()
        val other = FakeKeyWrapper(rng).apply { create() }
        assertFailsWith<KeyLostException> { WrappedKeyFile.open(other, b, 1, 1) }
        assertEquals("key lost", KeyLostException().message)
        assertEquals("key unavailable", KeyUnavailableException().message)
    }

    @Test fun listingIsStrictAndPerPurpose() {
        listOf("dek.1.bin", "dek.12.bin", "kb.2.bin", "dek.0.bin", "dek.01.bin", "dek.1.bin.tmp", "dek.x.bin",
            "DEK.3.bin", "dek.4.bin.bak", "dek.1234567890.bin").forEach { File(dir, it).writeBytes(byteArrayOf(1)) }
        File(dir, "dek.7.bin").mkdir()
        assertEquals(listOf(1, 12), WrappedKeyFile.list(dir, WrappedKeyFile.PURPOSE_DEK).keys.toList())
        assertEquals(listOf(2), WrappedKeyFile.list(dir, WrappedKeyFile.PURPOSE_KB).keys.toList())
        WrappedKeyFile.deleteTemps(dir)
        assertFalse(File(dir, "dek.1.bin.tmp").exists())
        assertTrue(File(dir, "dek.1.bin").exists())
    }

    @Test fun atomicWriteNeverReplaces() {
        val sub = File(dir, "keys")
        val written = sealed()
        WrappedKeyFile.writeAtomic(sub, "dek.1.bin", written)
        assertContentEquals(written, File(sub, "dek.1.bin").readBytes())
        assertEquals(listOf("dek.1.bin"), sub.list()!!.toList())
        val before = File(sub, "dek.1.bin").readBytes()
        assertFailsWith<IllegalStateException> { WrappedKeyFile.writeAtomic(sub, "dek.1.bin", sealed()) }
        assertContentEquals(before, File(sub, "dek.1.bin").readBytes())
        assertEquals(listOf("dek.1.bin"), sub.list()!!.toList())
    }
}
