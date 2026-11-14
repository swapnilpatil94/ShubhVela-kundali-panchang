package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.crypto.MutableDekRing
import app.parivaar.panchang.core.data.store.InMemoryRecordStore
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.SecureRandom

/** Repositories over an in-memory store with a fixed all-0x42 test key as DEK generation 1. */
internal class RepoTestKit(ready: Boolean = true) {
    val store = InMemoryRecordStore()
    val ring = MutableDekRing().also { it.put(1, ByteArray(32) { 0x42 }, makeCurrent = true) }
    val state = MutableStateFlow<VaultState>(if (ready) VaultState.Ready else VaultState.Opening)
    val rng = SecureRandom()
    val profiles = ProfileRepositoryImpl(store, ring, state, rng)
    val places = PlaceRepositoryImpl(store, ring, state, rng)
    val bonds = BondRepositoryImpl(store, ring, state, rng)
    val settings = SettingsRepositoryImpl(store, ring, state, rng)
}
