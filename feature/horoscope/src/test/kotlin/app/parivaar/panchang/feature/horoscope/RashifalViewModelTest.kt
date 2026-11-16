package app.parivaar.panchang.feature.horoscope

import app.parivaar.panchang.content.horoscope.Period
import app.parivaar.panchang.content.horoscope.Line
import app.parivaar.panchang.content.pack.ContentPack
import app.parivaar.panchang.content.pack.Terms
import app.parivaar.panchang.content.text.TemplateArg
import app.parivaar.panchang.content.text.TermKind
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class RashifalViewModelTest {
    private val terms = Terms(TermKind.entries.associateWith { k -> (1..k.count).map { "${k.key}$it" } })
    private val packs = mapOf(
        "en" to ContentPack("en", 1, mapOf("a.x" to "en {rashi:1}", "b.y" to "en {graha}", "c.z" to "en only"), terms),
        "hi" to ContentPack("hi", 1, mapOf("a.x" to "hi {rashi:1}", "b.y" to "hi {graha}"), terms),
        "mr" to ContentPack("mr", 1, emptyMap(), Terms.EMPTY),
    )

    private class FakeEngine(var place: Boolean = true) : RashifalEngine {
        val signs = mutableListOf<Rashi>()
        override fun h1(sign: Rashi, period: Period, now: Instant, variants: (String) -> Int): List<Line>? {
            signs += sign
            val graha = mapOf("graha" to TemplateArg.Term(TermKind.GRAHA, 3))
            return if (place) listOf(Line("a.x", emptyMap()), Line("b.y", graha)) else null
        }
        override fun h2(natal: NatalSnapshot, now: Instant, variants: (String) -> Int) = listOf(Line("c.z", emptyMap()))
    }

    private val natal = NatalSnapshot(Rashi.KARKA, Rashi.SIMHA, Rashi.TULA, 5, listOf(Graha.MOON, Graha.MARS))

    /** Unconfined: launches and engine work run inline, so state is final when a call returns. */
    private fun vm(flow: MutableStateFlow<NatalSnapshot?>, engine: RashifalEngine) =
        RashifalViewModel(
            flow, engine, { packs }, { _, _ -> "t" }, Clock.System, CoroutineScope(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )

    @Test fun noProfileDefaultsToMeshaWithoutYourDay() {
        val e = FakeEngine()
        val m = vm(MutableStateFlow(null), e)
        val s = m.state.value
        assertEquals(Rashi.MESHA, s.sign)
        assertNull(s.basis)
        assertEquals(YourDayStatus.NO_PROFILE, s.yourDayStatus)
        assertEquals(listOf("en rashi1", "en graha3"), s.reading?.lines)
        assertFalse(s.showFallbackNote)
    }

    @Test fun profileDefaultsToMoonSignAndShowsYourDay() {
        val e = FakeEngine()
        val m = vm(MutableStateFlow(natal), e)
        val s = m.state.value
        assertEquals(Rashi.KARKA, s.sign)
        assertEquals(Basis.MOON, s.basis)
        assertEquals(setOf(Basis.MOON, Basis.SUN, Basis.LAGNA), s.bases)
        assertEquals(YourDayStatus.SHOWN, s.yourDayStatus)
        m.onEvent(RashifalEvent.PickBasis(Basis.LAGNA))
        assertEquals(Rashi.TULA, m.state.value.sign)
        m.onEvent(RashifalEvent.Sign(Rashi.MEENA))
        assertNull(m.state.value.basis)
        assertEquals(Rashi.MEENA, e.signs.last())
    }

    @Test fun noonChartAsksForBirthTimeAndHidesLagna() {
        val m = vm(MutableStateFlow(natal.copy(lagna = null)), FakeEngine())
        assertEquals(YourDayStatus.NEEDS_BIRTH_TIME, m.state.value.yourDayStatus)
        assertFalse(Basis.LAGNA in m.state.value.bases)
    }

    @Test fun hindiDirectAndMarathiFallbackNote() {
        val m = vm(MutableStateFlow(natal), FakeEngine())
        m.onEvent(RashifalEvent.Language("hi"))
        assertEquals(listOf("hi rashi1", "hi graha3"), m.state.value.reading?.lines)
        // "c.z" exists only in en: Your day falls back, so the note shows.
        assertTrue(m.state.value.showFallbackNote)
        m.onEvent(RashifalEvent.Language("mr"))
        assertEquals(listOf("en rashi1", "en graha3"), m.state.value.reading?.lines)
        assertTrue(m.state.value.reading!!.usedFallback)
    }

    @Test fun noPlaceAndRedactedToString() {
        val m = vm(MutableStateFlow(natal), FakeEngine(place = false))
        assertTrue(m.state.value.noPlace)
        assertEquals("RashifalState(redacted)", m.state.value.toString())
        assertEquals("NatalSnapshot(redacted)", natal.toString())
    }

    @Test fun nothingRenderedIsAnError() {
        val e = object : RashifalEngine {
            override fun h1(sign: Rashi, period: Period, now: Instant, variants: (String) -> Int) =
                listOf(Line("missing.id", emptyMap()))
            override fun h2(natal: NatalSnapshot, now: Instant, variants: (String) -> Int): List<Line>? = null
        }
        val m = vm(MutableStateFlow(null), e)
        assertTrue(m.state.value.failed)
        assertNull(m.state.value.reading)
    }

    @Test fun windows() {
        val start = { d: LocalDate -> Instant.fromEpochSeconds(d.toEpochDays().toLong() * 86_400 + 3_600) }
        val d = LocalDate(2026, 11, 16) // a Monday, epochDay 20773
        assertEquals(20773L, PeriodWindows.window(Period.DAY, d, start).index)
        val w = PeriodWindows.window(Period.WEEK, LocalDate(2026, 11, 19), start)
        assertEquals(start(d), w.start)
        assertEquals(start(LocalDate(2026, 11, 23)), w.end)
        assertEquals(Math.floorDiv(20773L, 7L), w.index)
        assertEquals(2026L * 12 + 10, PeriodWindows.window(Period.MONTH, d, start).index)
        assertEquals(start(LocalDate(2027, 1, 1)), PeriodWindows.window(Period.YEAR, d, start).end)
        // Before sunrise the panchang date is the previous civil date.
        val before = start(d) - kotlin.time.Duration.parse("1s")
        assertEquals(LocalDate(2026, 11, 15), PeriodWindows.panchangDate(before, d, start))
        assertEquals(d, PeriodWindows.panchangDate(start(d), d, start))
    }
}
