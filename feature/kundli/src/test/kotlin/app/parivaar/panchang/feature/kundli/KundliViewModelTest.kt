package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class KundliViewModelTest {
    private val now = object : Clock { override fun now() = Instant.parse("2026-11-16T06:00:00Z") }

    private var profilesFlow = MutableStateFlow(emptyList<app.parivaar.panchang.core.model.profile.Profile>())

    private fun <T> withVm(
        p: app.parivaar.panchang.core.model.profile.Profile,
        block: suspend (KundliViewModel, KundliTestKit.CountingEngine) -> T,
    ): T {
        val pool = Executors.newSingleThreadExecutor { Thread(it, "kundli-compute") }
        val scope = CoroutineScope(SupervisorJob() + pool.asCoroutineDispatcher())
        val engine = KundliTestKit.CountingEngine(KundliTestKit.direct())
        val vm = KundliViewModel(
            MutableStateFlow(listOf(p)).also { profilesFlow = it },
            { TimeZone.UTC },
            { null },
            engine,
            KundliTestKit.FakeResolver(),
            KundliCache(),
            now, scope, pool.asCoroutineDispatcher(),
        )
        return try {
            runBlocking { block(vm, engine) }
        } finally {
            scope.cancel()
            pool.shutdownNow()
        }
    }

    private suspend fun KundliViewModel.loaded() =
        withTimeout(TIMEOUT) { state.first { it.bundle != null && !it.loading } }

    @Test fun computesOffMainAndHitsCache() = withVm(KundliTestKit.profile(1)) { vm, engine ->
        val s = vm.loaded()
        assertTrue(engine.lastThread.orEmpty().startsWith("kundli-compute"))
        assertEquals(4, s.bundle!!.currentDasha.size)
        assertTrue(s.expanded.isNotEmpty())
        val calls = engine.bundles.get()
        vm.load()
        vm.loaded()
        assertEquals(calls, engine.bundles.get()) // served from the LRU
        vm.evict(KundliTestKit.id(1))
        assertEquals(0, vm.cache.size())
    }

    private val raman = KundliTestKit.profile(2, calc = CalcSettings(ayanamsa = AyanamsaId.RAMAN))

    @Test fun disabledAyanamsaFallsBackWithNote() = withVm(raman) { vm, _ ->
        val s = vm.loaded()
        assertTrue(KundliNote.AYANAMSA_FALLBACK in s.notes)
        assertEquals(AyanamsaId.LAHIRI, s.bundle!!.calcUsed.ayanamsa)
    }

    @Test fun historicalOffsetNote() {
        val o = ResolvedOffset(19_800, OffsetSource.OVERLAY, setOf(OffsetFlag.HISTORICAL_VERIFY), null)
        assertEquals(setOf(KundliNote.HISTORICAL_OFFSET), offsetNotes(KundliTestKit.profile(3, offset = o)))
        assertTrue(offsetNotes(KundliTestKit.profile(3)).isEmpty())
    }

    @Test fun unknownTimeIsNoonChart() = withVm(KundliTestKit.profile(4, instant = null)) { vm, _ ->
        val s = vm.loaded()
        assertTrue(s.noon)
        assertTrue(KundliNote.NOON_CHART in s.notes)
        assertFalse(ChartModels.d1(s.bundle!!.facts, true).lagnaKnown)
    }

    @Test fun vaultLockRetainsNothing() = withVm(KundliTestKit.profile(5)) { vm, _ ->
        vm.loaded()
        profilesFlow.value = emptyList()
        val s = withTimeout(TIMEOUT) { vm.state.first { it.profiles.isEmpty() } }
        assertEquals(null, s.bundle)
        assertEquals(null, s.selectedId)
        assertTrue(s.children.isEmpty() && s.expanded.isEmpty() && s.notes.isEmpty())
    }

    private companion object {
        const val TIMEOUT = 60_000L
    }
}
