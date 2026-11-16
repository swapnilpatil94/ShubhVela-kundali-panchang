package app.parivaar.panchang.content.pack

/**
 * The Ed25519 public keys (raw 32 bytes each) a [PackLoader] accepts, per [PackSource] (docs/specs/M11.md D3).
 *
 * @property bundled the per-build key from `content/bundled.pub`; the only key for [PackSource.BUNDLED]. It gives
 *   integrity, not authenticity (the APK signature gives that). Null means no bundled pack is accepted.
 * @property remote the keys for [PackSource.REMOTE] (downloaded packs, M18b); in production this is [PROD].
 *   A key of the wrong length never matches.
 */
class TrustedKeys(val bundled: ByteArray?, val remote: List<ByteArray>) {
    /** The keys trusted for [source]: bundled packs never accept a remote key, and the reverse. */
    internal fun forSource(source: PackSource): List<ByteArray> = when (source) {
        PackSource.BUNDLED -> listOfNotNull(bundled)
        PackSource.REMOTE -> remote
    }

    override fun toString(): String = "TrustedKeys(bundled=${bundled != null}, remote=${remote.size})"

    companion object {
        /**
         * Public keys for downloaded packs. Empty until the owner generates the production key pair offline with
         * `tools/sign/keygen` and pastes the PUBLIC key here; the private key never enters the repo or the app.
         * TODO(owner): add the production public key (MASTER Part 10 section B, M11 D3).
         */
        val PROD: List<ByteArray> = emptyList()
    }
}
