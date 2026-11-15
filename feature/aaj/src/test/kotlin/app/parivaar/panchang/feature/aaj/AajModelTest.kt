package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.toEnginePlace
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.LocalResolution
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.geo.TzContext
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.calendar.CalendarCalculator
import app.parivaar.panchang.engine.calendar.EclipseSearch
import app.parivaar.panchang.engine.calendar.FestivalEngine
import app.parivaar.panchang.engine.calendar.FestivalRules
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.PanchangCalculator
import app.parivaar.panchang.engine.panchang.PanchangError
import app.parivaar.panchang.engine.panchang.PanchangRules
import app.parivaar.panchang.engine.panchang.PanchangUnavailableException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate

private class FixedOffsets(val seconds: Int, val nonexistent: LocalDate? = null) : OffsetResolver {
    private fun r() = ResolvedOffset(seconds, OffsetSource.IANA, emptySet(), null)
    override fun offsetAt(instant: Instant, ctx: TzContext, manual: ManualOffset?) = r()
    override fun civilDayOffset(date: LocalDate, ctx: TzContext, manual: ManualOffset?): CivilDayOffset =
        if (date == nonexistent) CivilDayOffset.Nonexistent else CivilDayOffset.Valid(r())
    override fun localToInstant(
        local: kotlinx.datetime.LocalDateTime, ctx: TzContext, choice: AmbiguityChoice, manual: ManualOffset?,
    ): LocalResolution = error("unused")
}

class AajModelTest {
    private val root = File(System.getProperty("pp.root.dir"))
    private val eph = AstronomyEngineEphemeris()
    private val tables = PanchangRules.parse(
        File(root, "rules/durmuhurta.json").readText(), File(root, "rules/choghadiya.json").readText(),
    )
    private val festivalSet = FestivalRules.parse(
        "pan_india.json", File(root, "rules/festivals/pan_india.json").readText(),
    )
    private fun realEngine() = EngineAajEngine(
        PanchangCalculator(eph, tables), CalendarCalculator(eph), FestivalEngine(eph), EclipseSearch(eph),
        listOf(festivalSet),
    )

    private val pune = GeoPlace(1, "Pune", null, "IN", 18.5204, 73.8567, 560.0, 0, "Asia/Kolkata", null)
    private val tromso = GeoPlace(2, "Tromso", null, "NO", 69.65, 18.96, 0.0, 0, "Europe/Oslo", null)
    private val id = RecordId("0".repeat(32))
    private val s = CalcSettings()
    private val puneNow = Instant.parse("2026-11-15T04:30:00Z") // 10:00 IST
    private val polarNow = Instant.parse("2026-12-15T11:00:00Z")
    private fun clock(at: Instant) = object : Clock { override fun now() = at }
    private fun inputs(p: GeoPlace, pid: RecordId = id) = AajInputs(p, pid, false, s, MonthSystem.AMANTA, "all")

    private fun <T> withModel(
        engine: AajEngine, offsets: OffsetResolver, at: Instant, flow: MutableStateFlow<AajInputs?>,
        cache: AajCache<AajLoaded> = AajCache(), body: suspend (AajModel) -> T,
    ): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val m = AajModel(flow, engine, offsets, cache, clock(at), scope, Dispatchers.Default, Dispatchers.IO)
            return runBlocking { withTimeout(TIMEOUT_MS) { body(m) } }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun AajModel.full(): AajUi.Content =
        ui.first { it is AajUi.Content && it.festivals != null } as AajUi.Content

    @Test fun `content days equal the engine called directly`() {
        val d = LocalDate(2026, 11, 15)
        withModel(realEngine(), FixedOffsets(19_800), puneNow, MutableStateFlow(inputs(pune))) { m ->
            val c = m.full()
            val place = pune.toEnginePlace()
            val direct = PanchangCalculator(eph, tables).days(LocalDate(2026, 11, 14), 3, place, s) { 19_800 }
            assertEquals(direct, c.days)
            assertEquals(d, c.date)
            assertEquals(CalendarCalculator(eph).day(d, 19_800, pune.toEnginePlace(), s), c.calendar)
            assertTrue(c.highLatitude.isEmpty())
            assertEquals(560.0, c.proof.altitudeM)
            assertTrue(UnavailableIds.VIJAYA in c.unavailable)
            assertNotNull(c.festivals)
        }
    }

    @Test fun `second load hits the cache and a new place is a new key`() {
        val real = realEngine()
        var calls = 0
        val counting = object : AajEngine by real {
            override fun days(first: LocalDate, count: Int, place: Place, s: CalcSettings, off: (LocalDate) -> Int) =
                real.days(first, count, place, s, off).also { calls++ }
        }
        val cache = AajCache<AajLoaded>()
        val flow = MutableStateFlow<AajInputs?>(inputs(pune))
        withModel(counting, FixedOffsets(19_800), puneNow, flow, cache) { it.full() }
        assertEquals(PER_LOAD, calls)
        withModel(counting, FixedOffsets(19_800), puneNow, flow, cache) { it.full() }
        assertEquals(PER_LOAD, calls)
        val other = RecordId("1".repeat(32))
        withModel(counting, FixedOffsets(19_800), puneNow, MutableStateFlow(inputs(pune, other)), cache) { it.full() }
        assertEquals(2 * PER_LOAD, calls)
    }

    private class Counting(val real: AajEngine) : AajEngine by real {
        var days = 0
        var gate: java.util.concurrent.CountDownLatch? = null
        override fun days(first: LocalDate, count: Int, place: Place, s: CalcSettings, off: (LocalDate) -> Int) =
            real.days(first, count, place, s, off).also { days++ }
        override fun festivals(
            date: LocalDate, place: Place, s: CalcSettings, region: String,
            ekadashi: app.parivaar.panchang.engine.calendar.Tradition, off: (LocalDate) -> Int,
        ): List<app.parivaar.panchang.engine.calendar.FestivalOccurrence> {
            gate?.await()
            return real.festivals(date, place, s, region, ekadashi, off)
        }
    }

    @Test fun `an equal inputs emission does not recompute, a changed setting does`() {
        val e = Counting(realEngine())
        val flow = MutableStateFlow<AajInputs?>(inputs(pune))
        withModel(e, FixedOffsets(19_800), puneNow, flow) { m ->
            m.full()
            assertEquals(PER_LOAD, e.days)
            flow.value = inputs(pune).copy() // for example a theme change: same calculation inputs
            kotlinx.coroutines.delay(300)
            assertEquals(PER_LOAD, e.days)
            flow.value = inputs(pune).copy(settings = CalcSettings(abhijitOnWednesday = true))
            while (e.days < 2 * PER_LOAD) kotlinx.coroutines.delay(50)
            kotlinx.coroutines.delay(300)
            assertEquals(2 * PER_LOAD, e.days)
        }
    }

    @Test fun `the panchang day shows first and the calendar and festivals fill in afterwards`() {
        val e = Counting(realEngine())
        e.gate = java.util.concurrent.CountDownLatch(1)
        withModel(e, FixedOffsets(19_800), puneNow, MutableStateFlow(inputs(pune))) { m ->
            val first = m.ui.first { it is AajUi.Content } as AajUi.Content
            assertEquals(null, first.festivals)
            assertEquals(null, first.calendar)
            assertEquals(3, first.days.size)
            e.gate!!.countDown()
            val full = m.full()
            assertNotNull(full.calendar)
            assertEquals(first.days, full.days)
        }
    }

    @Test fun `a second model on the same cache shows full content without computing`() {
        val e = Counting(realEngine())
        val cache = AajCache<AajLoaded>()
        withModel(e, FixedOffsets(19_800), puneNow, MutableStateFlow(inputs(pune)), cache) { it.full() }
        val before = e.days
        withModel(e, FixedOffsets(19_800), puneNow, MutableStateFlow(inputs(pune)), cache) { m ->
            val c = m.ui.first { it is AajUi.Content } as AajUi.Content
            assertNotNull(c.festivals)
            assertNotNull(c.calendar)
        }
        assertEquals(before, e.days)
    }

    private class Throwing(val real: AajEngine, val bad: LocalDate) : AajEngine by real {
        override fun days(first: LocalDate, count: Int, place: Place, s: CalcSettings, off: (LocalDate) -> Int):
            List<app.parivaar.panchang.engine.panchang.PanchangDay> {
            if (first == bad) throw PanchangUnavailableException(PanchangError.NO_SUN_DAY)
            return real.days(first, count, place, s, off)
        }
    }

    @Test fun `a polar neighbour does not turn a normal day into a polar day`() {
        val e = Throwing(realEngine(), LocalDate(2026, 11, 14)) // D-1 has no sun, D is fine
        withModel(e, FixedOffsets(19_800), puneNow, MutableStateFlow(inputs(pune))) { m ->
            val c = assertIs<AajUi.Content>(m.ui.first { it != AajUi.Loading })
            assertEquals(2, c.days.size)
            assertEquals(LocalDate(2026, 11, 15), c.day.date)
        }
    }

    @Test fun `no sun on the day itself is the polar state`() {
        val e = Throwing(realEngine(), LocalDate(2026, 11, 15))
        withModel(e, FixedOffsets(19_800), puneNow, MutableStateFlow(inputs(pune))) { m ->
            assertIs<AajUi.PolarDay>(m.ui.first { it != AajUi.Loading })
        }
    }

    @Test fun `no place and a nonexistent date`() {
        withModel(realEngine(), FixedOffsets(19_800), puneNow, MutableStateFlow(null)) { m ->
            assertEquals(AajUi.NoPlace, m.ui.first { it != AajUi.Loading })
        }
        withModel(
            realEngine(), FixedOffsets(19_800, nonexistent = LocalDate(2026, 11, 15)), puneNow,
            MutableStateFlow(inputs(pune)),
        ) { m -> assertEquals(AajUi.NonexistentDate, m.ui.first { it != AajUi.Loading }) }
    }

    @Test fun `no sun day shows only limbs`() {
        val real = realEngine()
        val polar = object : AajEngine by real {
            override fun days(first: LocalDate, count: Int, place: Place, s: CalcSettings, off: (LocalDate) -> Int):
                List<app.parivaar.panchang.engine.panchang.PanchangDay> =
                throw PanchangUnavailableException(PanchangError.NO_SUN_DAY)
        }
        withModel(polar, FixedOffsets(3_600), polarNow, MutableStateFlow(inputs(tromso))) { m ->
            val p = assertIs<AajUi.PolarDay>(m.ui.first { it != AajUi.Loading })
            assertTrue(p.limbs.values.any { it.isNotEmpty() })
        }
    }

    @Test fun `Tromso polar night is content with the fallback flag`() {
        withModel(realEngine(), FixedOffsets(3_600), polarNow, MutableStateFlow(inputs(tromso))) { m ->
            val c = m.full()
            assertTrue(DayFlag.SUN_FALLBACK in c.highLatitude)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 120_000L
        const val PER_LOAD = 3 // one engine call per day: D-1, D, D+1
    }
}
