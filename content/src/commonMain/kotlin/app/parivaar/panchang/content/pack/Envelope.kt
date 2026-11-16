@file:Suppress("MagicNumber", "MatchingDeclarationName") // envelope byte offsets; file name per M11 spec

package app.parivaar.panchang.content.pack

import app.parivaar.panchang.content.crypto.Sha512

/**
 * A signed content pack envelope (docs/specs/M11.md section 3), big-endian:
 * `0..3` magic `PPCK`; `4` envelopeVersion = 1; `5` codec = 1 (UTF-8 JSON, M11 D2); `6..13` keyId = the first 8
 * bytes of SHA-512(publicKey); `14..17` payloadLen u32; the payload; then a 64-byte Ed25519 signature over bytes
 * `[0, 18 + payloadLen)`. The total length is exactly `18 + payloadLen + 64`.
 *
 * Parsing only splits the bytes; it never looks inside the payload (that happens after the signature check).
 */
internal class PackEnvelope private constructor(
    val keyId: ByteArray,
    /** Bytes `[0, 18 + payloadLen)`: what the signature covers. */
    val signed: ByteArray,
    val signature: ByteArray,
) {
    /** The payload, still unverified until [signature] has been checked against [signed]. */
    val payload: ByteArray get() = signed.copyOfRange(HEADER, signed.size)

    override fun toString(): String = "PackEnvelope(${signed.size - HEADER} bytes)"

    /** Why [parse] failed. Messages are static and never carry input bytes. */
    class Failure(val kind: Kind) : Exception(kind.name) {
        enum class Kind { MALFORMED, UNSUPPORTED_VERSION }
    }

    companion object {
        /** Packs above this size are refused before any parsing (24 MiB). */
        const val MAX_BYTES: Int = 24 * 1024 * 1024
        const val HEADER: Int = 18
        const val SIGNATURE: Int = 64
        const val KEY_ID: Int = 8
        const val VERSION: Int = 1
        const val CODEC_JSON: Int = 1
        private val MAGIC = "PPCK".encodeToByteArray()

        /**
         * Splits [bytes] into header fields, signed part and signature. Fails with [Failure.Kind.MALFORMED] on a
         * short input, bad magic or a length that is not exact, and with [Failure.Kind.UNSUPPORTED_VERSION] on an
         * envelope version or codec other than 1. Size limits are the caller's job ([MAX_BYTES]).
         */
        @Suppress("ReturnCount") // guard clauses mirror the RFC/spec rejection steps
        fun parse(bytes: ByteArray): Result<PackEnvelope> {
            if (bytes.size < HEADER + SIGNATURE) return fail(Failure.Kind.MALFORMED)
            for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return fail(Failure.Kind.MALFORMED)
            val len = ((bytes[14].toLong() and 0xff) shl 24) or ((bytes[15].toLong() and 0xff) shl 16) or
                ((bytes[16].toLong() and 0xff) shl 8) or (bytes[17].toLong() and 0xff)
            if (HEADER + len + SIGNATURE != bytes.size.toLong()) return fail(Failure.Kind.MALFORMED)
            if (bytes[4].toInt() != VERSION || bytes[5].toInt() != CODEC_JSON) {
                return fail(Failure.Kind.UNSUPPORTED_VERSION)
            }
            val end = HEADER + len.toInt()
            return Result.success(
                PackEnvelope(
                    keyId = bytes.copyOfRange(6, 6 + KEY_ID),
                    signed = bytes.copyOfRange(0, end),
                    signature = bytes.copyOfRange(end, end + SIGNATURE),
                ),
            )
        }

        /** The key id of a raw 32-byte Ed25519 public key: the first 8 bytes of its SHA-512. */
        fun keyIdOf(publicKey: ByteArray): ByteArray = Sha512.digest(publicKey).copyOf(KEY_ID)

        /** Header plus [payload] for the key [publicKey]: the exact bytes a build-time signer must sign. */
        fun unsigned(publicKey: ByteArray, payload: ByteArray): ByteArray {
            val head = ByteArray(HEADER)
            MAGIC.copyInto(head)
            head[4] = VERSION.toByte()
            head[5] = CODEC_JSON.toByte()
            keyIdOf(publicKey).copyInto(head, 6)
            val n = payload.size
            head[14] = (n ushr 24).toByte()
            head[15] = (n ushr 16).toByte()
            head[16] = (n ushr 8).toByte()
            head[17] = n.toByte()
            return head + payload
        }

        /** [unsigned] followed by its 64-byte [signature]: a complete envelope. */
        fun seal(unsigned: ByteArray, signature: ByteArray): ByteArray {
            require(signature.size == SIGNATURE) { "signature must be 64 bytes" }
            return unsigned + signature
        }

        private fun fail(kind: Failure.Kind): Result<PackEnvelope> = Result.failure(Failure(kind))
    }
}
