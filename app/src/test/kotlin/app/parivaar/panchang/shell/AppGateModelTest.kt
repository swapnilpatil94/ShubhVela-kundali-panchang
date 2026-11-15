package app.parivaar.panchang.shell

import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.DataVault
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeVault(initial: VaultState) : DataVault {
    val st = MutableStateFlow(initial)
    override val state: StateFlow<VaultState> get() = st
    val opens = AtomicInteger()
    val fresh = AtomicInteger()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun open(): VaultState {
        opens.incrementAndGet()
        gate?.await()
        return st.value
    }

    override suspend fun startFresh(): DataResult<Unit> {
        fresh.incrementAndGet()
        gate?.await()
        st.value = VaultState.Ready
        return DataResult.Ok(Unit)
    }
}

private class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    val s = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> get() = s

    override suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit> {
        s.value = f(s.value)
        return DataResult.Ok(Unit)
    }
}

class AppGateModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterTest fun tearDown() = scope.cancel()

    private fun model(v: FakeVault, s: SettingsRepository = FakeSettings()) =
        AppGateModel(v, s, scope, Dispatchers.Unconfined)

    @Test fun vaultStatesMapToGate() {
        val v = FakeVault(VaultState.Opening)
        val m = model(v)
        assertEquals(GateUi.Loading, m.ui.value)
        v.st.value = VaultState.Restoring
        assertEquals(GateUi.Loading, m.ui.value)
        v.st.value = VaultState.KeyLost
        assertEquals(GateUi.KeyLost, m.ui.value)
        v.st.value = VaultState.KeyUnavailable
        assertEquals(GateUi.KeyUnavailable, m.ui.value)
    }

    @Test fun readyRoutesByOnboardingDone() {
        val s = FakeSettings()
        val v = FakeVault(VaultState.Ready)
        val m = model(v, s)
        assertEquals(GateUi.Onboarding, m.ui.value)
        s.s.value = AppSettings(onboardingDone = true)
        assertEquals(GateUi.Shell, m.ui.value)
    }

    @Test fun keyLostNeverWipesByItself() {
        val v = FakeVault(VaultState.KeyLost)
        val m = model(v)
        assertEquals(GateUi.KeyLost, m.ui.value)
        m.retry()
        assertEquals(0, v.fresh.get())
        assertEquals(1, v.opens.get())
    }

    @Test fun startFreshOnlyOnConfirm() {
        val v = FakeVault(VaultState.KeyLost)
        val m = model(v)
        m.startFreshConfirmed()
        assertEquals(1, v.fresh.get())
        assertEquals(GateUi.Onboarding, m.ui.value)
        assertFalse(m.busy.value)
    }

    @Test fun singleFlight() {
        val v = FakeVault(VaultState.KeyUnavailable)
        v.gate = CompletableDeferred()
        val m = model(v)
        m.retry()
        assertTrue(m.busy.value)
        m.retry()
        m.startFreshConfirmed()
        assertEquals(1, v.opens.get())
        assertEquals(0, v.fresh.get())
        v.gate!!.complete(Unit)
        assertFalse(m.busy.value)
        m.startFreshConfirmed()
        assertEquals(1, v.fresh.get())
    }

    @Test fun settingsFailureDoesNotCrashOrOnboard() {
        val failing = object : SettingsRepository {
            override val settings: Flow<AppSettings> = flow { error("boom") }

            override suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit> = DataResult.Ok(Unit)
        }
        assertEquals(GateUi.KeyUnavailable, model(FakeVault(VaultState.Ready), failing).ui.value)
    }

    @Test fun busyClearsEvenIfOpenThrows() = runBlocking {
        val v = object : DataVault {
            override val state: StateFlow<VaultState> = MutableStateFlow(VaultState.KeyUnavailable)

            override suspend fun open(): VaultState = error("x")

            override suspend fun startFresh(): DataResult<Unit> = DataResult.Ok(Unit)
        }
        val quiet = kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
        val handler = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + quiet)
        val m = AppGateModel(v, FakeSettings(), handler, Dispatchers.Unconfined)
        m.retry()
        assertFalse(m.busy.value)
        handler.cancel()
    }
}
