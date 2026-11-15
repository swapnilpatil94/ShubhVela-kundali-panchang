package app.parivaar.panchang.shell

import app.parivaar.panchang.core.data.DataVault
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.data.VaultState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface GateUi {
    data object Loading : GateUi

    data object Onboarding : GateUi

    data object Shell : GateUi

    data object KeyLost : GateUi

    data object KeyUnavailable : GateUi
}

/**
 * Maps vault state to what the app shows. Recovery is always user-chosen: nothing here wipes data except
 * [startFreshConfirmed], which the UI calls only after an explicit confirm dialog. Both actions are single-flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGateModel(
    private val vault: DataVault,
    settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val running = AtomicBoolean(false)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> get() = _busy

    val ui: StateFlow<GateUi> =
        vault.state
            .flatMapLatest { s ->
                when (s) {
                    VaultState.Opening, VaultState.Restoring -> flowOf(GateUi.Loading)
                    VaultState.KeyLost -> flowOf(GateUi.KeyLost)
                    VaultState.KeyUnavailable -> flowOf(GateUi.KeyUnavailable)
                    VaultState.Ready ->
                        settings.settings
                            .map { if (it.onboardingDone) GateUi.Shell else GateUi.Onboarding }
                            .catch { emit(GateUi.KeyUnavailable) }
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, GateUi.Loading)

    fun retry() = singleFlight { vault.open() }

    fun startFreshConfirmed() = singleFlight { vault.startFresh() }

    private fun singleFlight(block: suspend () -> Unit) {
        if (!running.compareAndSet(false, true)) return
        _busy.value = true
        scope.launch(io) {
            try {
                block()
            } finally {
                _busy.value = false
                running.set(false)
            }
        }
    }
}
