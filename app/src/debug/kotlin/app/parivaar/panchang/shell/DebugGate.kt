package app.parivaar.panchang.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Debug builds only: forces the gate screen shown (KeyLost or KeyUnavailable) for screenshots. It touches no data;
 * while forced, the screens' recovery actions are no-ops (see MainActivity), so nothing can be wiped from here.
 */
object DebugGate {
    private val state = MutableStateFlow<GateUi?>(null)
    val override: StateFlow<GateUi?> get() = state

    const val ENABLED = true

    fun set(name: String?) {
        state.value =
            when (name) {
                "keylost" -> GateUi.KeyLost
                "unavailable" -> GateUi.KeyUnavailable
                else -> null
            }
    }
}
