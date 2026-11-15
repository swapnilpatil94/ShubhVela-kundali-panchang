@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.panchang.PanchangDay
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

class MonthModelTest {
    private class Fake : CalendarEngine {
        val calls = AtomicInteger()
        val firsts = CopyOnWriteArrayList<LocalDate>()

        @Volatile var gate: CountDownLatch? = null

        @Volatile var failOn: LocalDate? = null
        override fun month(gridFirst: LocalDate, n: Int, i: CalInputs): MonthBundle {
            gate?.await()
            calls.incrementAndGet()
            firsts.add(gridFirst)
            if (gridFirst == failOn) throw NonexistentCivilDayException()
            return Fx.synthetic(gridFirst, n)
        }
        override fun festivalsYear(year: Int, i: CalInputs): List<FestivalOccurrence> = error("unused")
        override fun eclipsesYear(year: Int, i: CalInputs): List<GlobalWithLocal> = error("unused")
        override fun transits(from: LocalDate, to: LocalDate, i: CalInputs): List<TransitEvent> = error("unused")
        override fun day(date: LocalDate, i: CalInputs): List<PanchangDay> = error("unused")
    }

    private val clock = object : Clock { override fun now(): Instant = Instant.parse("2026-11-15T06:00:00Z") }
    private val nov = YearMonth(2026, 11)

    private fun <T> withModel(
        engine: Fake, flow: MutableStateFlow<CalInputs?>, cache: MonthCache = MonthCache(), body: suspend (MonthModel) -> T,
    ): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val m = MonthModel(flow, engine, cache, clock, { DayOfWeek.SUNDAY }, scope, Dispatchers.Default)
            return runBlocking { withTimeout(TIMEOUT_MS) { body(m) } }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun MonthModel.content(ym: YearMonth): MonthUi.Content =
        ui.first { it is MonthUi.Content && it.key.ym == ym } as MonthUi.Content

    private suspend fun settled(e: Fake, calls: Int) {
        while (e.calls.get() < calls) delay(10)
        delay(100)
    }

    @Test fun `first month then neighbours are prefetched and re-subscribing recomputes nothing`() {
        val e = Fake()
        withModel(e, MutableStateFlow(Fx.inputs())) { m ->
            assertEquals(nov, m.content(nov).key.ym)
            settled(e, 3)
            assertEquals(3, e.calls.get()) // Nov, Dec, Oct
            // a second collector (a configuration change) and a back-and-forth navigation hit the cache
            assertEquals(nov, m.content(nov).key.ym)
            m.next()
            m.content(YearMonth(2026, 12))
            m.prev()
            m.content(nov)
            settled(e, 4)
            assertEquals(4, e.calls.get()) // only Jan 2027 was prefetched
        }
    }

    @Test fun `equal inputs with a new offset lambda do not recompute but a new place does`() {
        val e = Fake()
        val flow = MutableStateFlow<CalInputs?>(Fx.inputs())
        withModel(e, flow) { m ->
            m.content(nov)
            settled(e, 3)
            flow.value = Fx.inputs() // new instance, equal
            delay(300)
            assertEquals(3, e.calls.get())
            val other = Fx.inputs(id = RecordId("1".repeat(32)))
            flow.value = other
            val c = m.ui.first { it is MonthUi.Content && it.key.placeId == other.placeId } as MonthUi.Content
            assertEquals(nov, c.key.ym)
            settled(e, 4)
            assertNotNull(e.firsts)
        }
    }

    @Test fun `the grid stays visible while a new month loads`() {
        val e = Fake()
        withModel(e, MutableStateFlow(Fx.inputs())) { m ->
            m.content(nov)
            settled(e, 3)
            e.gate = CountDownLatch(1)
            m.show(YearMonth(2027, 4))
            val loading = m.ui.first { it is MonthUi.Loading } as MonthUi.Loading
            assertEquals(nov, loading.stale?.key?.ym)
            e.gate!!.countDown()
            m.content(YearMonth(2027, 4))
        }
    }

    @Test fun `a nonexistent civil day is an error state`() {
        val e = Fake()
        e.failOn = MonthBuilder.gridFirst(YearMonth(2027, 4), DayOfWeek.SUNDAY)
        withModel(e, MutableStateFlow(Fx.inputs())) { m ->
            m.content(nov)
            m.show(YearMonth(2027, 4))
            assertIs<MonthUi.Error>(m.ui.first { it is MonthUi.Error })
            assertEquals(MonthUi.Error(nonexistent = true), m.ui.value)
        }
    }

    @Test fun `no place and jump to a festival`() {
        val e = Fake()
        val flow = MutableStateFlow<CalInputs?>(null)
        withModel(e, flow) { m ->
            m.ui.first { it is MonthUi.NoPlace }
            flow.value = Fx.inputs()
            m.content(nov)
            val occ = FestivalOccurrence("holi", LocalDate(2027, 3, 22), null, null, null, null, null, null, emptySet())
            m.jumpTo(occ)
            m.content(YearMonth(2027, 3))
            assertEquals(LocalDate(2027, 3, 22), m.selected.value)
            m.today()
            m.content(nov)
            assertEquals(LocalDate(2026, 11, 15).plus(DatePeriod()), m.content(nov).today)
        }
    }

    private companion object { const val TIMEOUT_MS = 20_000L }
}
