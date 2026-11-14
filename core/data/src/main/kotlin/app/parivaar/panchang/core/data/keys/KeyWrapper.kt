package app.parivaar.panchang.core.data.keys

/**
 * Wraps the 32-byte DEK and K_b under the device KEK (spec section 3 internal contracts, section 4
 * "Hierarchy"). The KEK never leaves its holder. Every method may throw [KeyLostException] (the KEK is
 * gone or invalidated, or the wrap does not authenticate) or [KeyUnavailableException] (transient
 * keystore failure; retry later). Neither carries a cause or data in its message.
 */
internal interface KeyWrapper {
    fun exists(): Boolean
    fun create()
    fun delete()

    /** Returns (iv[12], ct+tag); the wrapper chooses the IV. */
    fun wrap(plain: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray>

    /** Returns the unwrapped key; the caller wipes it. */
    fun unwrap(iv: ByteArray, ct: ByteArray, aad: ByteArray): ByteArray
}

/** The KEK is permanently unusable for this data; recovery is the user's choice (D8). */
internal class KeyLostException : Exception("key lost")

/** The keystore failed transiently; nothing is lost. */
internal class KeyUnavailableException : Exception("key unavailable")
