package app.parivaar.panchang.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Release stub: no override exists, nothing can be set. */
object DebugGate {
    val override: StateFlow<GateUi?> = MutableStateFlow(null)

    const val ENABLED = false
}
