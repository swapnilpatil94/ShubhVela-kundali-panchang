package app.parivaar.panchang.core.data.keys

import app.parivaar.panchang.core.data.crypto.AesGcm
import app.parivaar.panchang.core.data.crypto.Bytes
import app.parivaar.panchang.core.data.crypto.CryptoException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Wrapped key file `noBackupFilesDir/keys/{dek|kb}.<g>.bin` (spec section 4):
 * "PPWK" | u8 ver=1 | u8 purpose (1 DEK, 2 K_b) | u32 g | u8 kekVer=1 | iv[12] | ct+tag[48];
 * AAD = the 11 header bytes. Header problems throw [CryptoException]; a wrap that does not authenticate
 * surfaces from the [KeyWrapper] as [KeyLostException]. Writes are temp file + fsync + rename and never
 * replace an existing key file (D8).
 */
internal object WrappedKeyFile {
    const val PURPOSE_DEK = 1
    const val PURPOSE_KB = 2
    const val VERSION = 1
    const val KEK_VERSION = 1
    const val HEADER_BYTES = 11
    const val WRAPPED_BYTES = AesGcm.KEY_BYTES + AesGcm.TAG_BYTES
    const val TOTAL_BYTES = HEADER_BYTES + AesGcm.NONCE_BYTES + WRAPPED_BYTES
    private const val OFF_VER = 4
    private const val OFF_PURPOSE = 5
    private const val OFF_GEN = 6
    private const val OFF_KEK_VER = 10
    private const val TMP_SUFFIX = ".tmp"
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'P'.code.toByte(), 'W'.code.toByte(), 'K'.code.toByte())
    private val NAME = Regex("""^(dek|kb)\.([1-9][0-9]{0,8})\.bin$""")

    fun fileName(purpose: Int, g: Int): String {
        require(g >= 1) { "bad generation" }
        return prefix(purpose) + "." + g + ".bin"
    }

    fun header(purpose: Int, g: Int): ByteArray {
        require(g >= 1) { "bad generation" }
        prefix(purpose)
        val h = ByteArray(HEADER_BYTES)
        MAGIC.copyInto(h)
        h[OFF_VER] = VERSION.toByte()
        h[OFF_PURPOSE] = purpose.toByte()
        Bytes.putU32(h, OFF_GEN, g.toLong())
        h[OFF_KEK_VER] = KEK_VERSION.toByte()
        return h
    }

    /** Wraps [key] (32 bytes) for generation [g]; returns the file bytes. [key] is not modified. */
    fun seal(wrapper: KeyWrapper, purpose: Int, g: Int, key: ByteArray): ByteArray {
        require(key.size == AesGcm.KEY_BYTES) { "bad key length" }
        val header = header(purpose, g)
        val (iv, ct) = wrapper.wrap(key, header)
        check(iv.size == AesGcm.NONCE_BYTES && ct.size == WRAPPED_BYTES) { "bad wrap output" }
        return header + iv + ct
    }

    /** Unwraps file [bytes] that must carry [purpose] and generation [g]; the caller wipes the result. */
    fun open(wrapper: KeyWrapper, bytes: ByteArray, purpose: Int, g: Int): ByteArray {
        val header = checkHeader(bytes, purpose, g)
        val iv = bytes.copyOfRange(HEADER_BYTES, HEADER_BYTES + AesGcm.NONCE_BYTES)
        val ct = bytes.copyOfRange(HEADER_BYTES + AesGcm.NONCE_BYTES, TOTAL_BYTES)
        val key = wrapper.unwrap(iv, ct, header)
        if (key.size != AesGcm.KEY_BYTES) {
            Bytes.wipe(key)
            reject(CryptoException.Reason.FORMAT)
        }
        return key
    }

    /** Validates the header of [bytes]; returns the 11 header bytes (the AAD). */
    fun checkHeader(bytes: ByteArray, purpose: Int, g: Int): ByteArray {
        if (bytes.size < OFF_PURPOSE || !Bytes.constantTimeEquals(bytes.copyOf(MAGIC.size), MAGIC)) {
            reject(CryptoException.Reason.FORMAT)
        }
        if (Bytes.u8(bytes, OFF_VER) != VERSION) reject(CryptoException.Reason.VERSION)
        if (bytes.size != TOTAL_BYTES) reject(CryptoException.Reason.FORMAT)
        if (Bytes.u8(bytes, OFF_KEK_VER) != KEK_VERSION) reject(CryptoException.Reason.VERSION)
        val header = bytes.copyOf(HEADER_BYTES)
        if (!header.contentEquals(header(purpose, g))) reject(CryptoException.Reason.FORMAT)
        return header
    }

    /** Key files of [purpose] in [dir] by generation; names that do not match exactly are ignored. */
    fun list(dir: File, purpose: Int): Map<Int, File> {
        val want = prefix(purpose)
        val out = sortedMapOf<Int, File>()
        dir.listFiles()?.forEach { f ->
            val m = NAME.matchEntire(f.name)
            if (m != null && m.groupValues[1] == want && f.isFile) out[m.groupValues[2].toInt()] = f
        }
        return out
    }

    /** Deletes leftover temp files from an interrupted write (they were never renamed into place). */
    fun deleteTemps(dir: File) {
        dir.listFiles()?.filter { it.name.endsWith(TMP_SUFFIX) }?.forEach { it.delete() }
    }

    /** Writes `<name>.tmp`, fsyncs, renames to [name]. Refuses to replace an existing file. */
    fun writeAtomic(dir: File, name: String, bytes: ByteArray) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("key dir unavailable")
        val target = File(dir, name)
        check(!target.exists()) { "key file exists" }
        val tmp = File(dir, name + TMP_SUFFIX)
        try {
            FileOutputStream(tmp).use {
                it.write(bytes)
                it.fd.sync()
            }
            if (!tmp.renameTo(target)) throw IOException("key file rename failed")
            DirSync.fsync(dir)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun prefix(purpose: Int): String = when (purpose) {
        PURPOSE_DEK -> "dek"
        PURPOSE_KB -> "kb"
        else -> throw IllegalArgumentException("bad purpose")
    }

    private fun reject(reason: CryptoException.Reason): Nothing = throw CryptoException(reason)
}
