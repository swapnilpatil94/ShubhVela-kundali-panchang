package app.parivaar.panchang.core.data.crypto

import java.security.SecureRandom

/**
 * Row envelope stored in column `blob` (spec section 4): u8 ver=1 | u32 keyId (DEK g) | nonce[12] | ct+tag.
 * AAD (27 B) = "PPR1" | u8 tbl | id[16] | u16 payloadVer | u32 keyId, binding the blob to its clear
 * columns so a row moved to another tbl/id or with a changed ver/keyId fails authentication.
 * Big-endian; any deviation is rejected (fail closed) with a [CryptoException].
 */
internal object RowEnvelope {
    const val VERSION = 1
    const val HEADER_BYTES = 1 + 4 + AesGcm.NONCE_BYTES
    const val MIN_BYTES = HEADER_BYTES + AesGcm.TAG_BYTES
    const val AAD_BYTES = 27
    const val ID_BYTES = 16
    const val TBL_PROFILE = 1
    const val TBL_PLACE = 2
    const val TBL_BOND = 3
    const val TBL_SETTINGS = 4
    private const val U16_MAX = 0xFFFF
    private const val OFF_KEY_ID = 1
    private const val OFF_NONCE = 5
    private const val AAD_OFF_TBL = 4
    private const val AAD_OFF_ID = 5
    private const val AAD_OFF_VER = 21
    private const val AAD_OFF_KEY_ID = 23
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'P'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())

    fun aad(tbl: Int, id: ByteArray, payloadVer: Int, keyId: Int): ByteArray {
        require(fieldsValid(tbl, id, payloadVer, keyId)) { "bad row fields" }
        val a = ByteArray(AAD_BYTES)
        MAGIC.copyInto(a)
        a[AAD_OFF_TBL] = tbl.toByte()
        id.copyInto(a, AAD_OFF_ID)
        Bytes.putU16(a, AAD_OFF_VER, payloadVer)
        Bytes.putU32(a, AAD_OFF_KEY_ID, keyId.toLong())
        return a
    }

    /** Seals [plaintext] under the ring's current key and a fresh random nonce. */
    fun seal(
        ring: DekRing, tbl: Int, id: ByteArray, payloadVer: Int, plaintext: ByteArray, rng: SecureRandom,
    ): ByteArray {
        val keyId = ring.currentId
        val key = checkNotNull(ring.key(keyId)) { "current key missing" }
        val sealed = try {
            // Fail closed: an all-zero key can only mean a wiped key, never a real DEK.
            check(!key.all { it == 0.toByte() }) { "key wiped" }
            AesGcm.encrypt(key, plaintext, aad(tbl, id, payloadVer, keyId), rng)
        } finally {
            Bytes.wipe(key)
        }
        val out = ByteArray(HEADER_BYTES + sealed.ciphertext.size)
        out[0] = VERSION.toByte()
        Bytes.putU32(out, OFF_KEY_ID, keyId.toLong())
        sealed.nonce.copyInto(out, OFF_NONCE)
        sealed.ciphertext.copyInto(out, HEADER_BYTES)
        return out
    }

    /** Key generation named by [blob]; throws [CryptoException] if the header is malformed. */
    fun keyIdOf(blob: ByteArray): Int {
        if (blob.isEmpty()) reject(CryptoException.Reason.FORMAT)
        if (Bytes.u8(blob, 0) != VERSION) reject(CryptoException.Reason.VERSION)
        if (blob.size < MIN_BYTES) reject(CryptoException.Reason.FORMAT)
        val keyId = Bytes.getU32(blob, OFF_KEY_ID)
        if (keyId < 1 || keyId > Int.MAX_VALUE) reject(CryptoException.Reason.FORMAT)
        return keyId.toInt()
    }

    /** Opens [blob] read from row (tbl, id, payloadVer); the caller wipes the returned plaintext. */
    fun open(ring: DekRing, tbl: Int, id: ByteArray, payloadVer: Int, blob: ByteArray): ByteArray {
        val keyId = keyIdOf(blob)
        if (!fieldsValid(tbl, id, payloadVer, keyId)) reject(CryptoException.Reason.FORMAT)
        val key = ring.key(keyId) ?: reject(CryptoException.Reason.AUTH)
        try {
            val nonce = blob.copyOfRange(OFF_NONCE, HEADER_BYTES)
            val ct = blob.copyOfRange(HEADER_BYTES, blob.size)
            return AesGcm.open(key, nonce, ct, aad(tbl, id, payloadVer, keyId))
        } finally {
            Bytes.wipe(key)
        }
    }

    private fun reject(reason: CryptoException.Reason): Nothing = throw CryptoException(reason)

    private fun fieldsValid(tbl: Int, id: ByteArray, payloadVer: Int, keyId: Int): Boolean =
        tbl in TBL_PROFILE..TBL_SETTINGS && id.size == ID_BYTES && payloadVer in 0..U16_MAX && keyId >= 1
}
