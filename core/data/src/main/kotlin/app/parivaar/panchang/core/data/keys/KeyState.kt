package app.parivaar.panchang.core.data.keys

/**
 * Outcome of [DekManager.open] (spec section 4 "Key state"). The vault maps it to VaultState:
 * [Ready] -> Ready; [Empty] -> Restoring if a snapshot and backup key exist, else [DekManager.createFresh];
 * [Lost] -> KeyLost; [Unavailable] -> KeyUnavailable. No state ever wipes or overwrites a DEK (D8).
 */
internal sealed interface KeyState {
    /** Every wrapped DEK unwrapped; the ring is loaded. */
    data object Ready : KeyState

    /** No wrapped DEK and no rows: a first start, or a new device. */
    data object Empty : KeyState

    /** Wrapped DEKs (or rows) exist but cannot be unwrapped: KEK gone, invalidated, or a file damaged. */
    data object Lost : KeyState

    /** The keystore failed twice (once retried after 250 ms); try again later. */
    data object Unavailable : KeyState
}
