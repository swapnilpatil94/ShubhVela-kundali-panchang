package app.parivaar.panchang.content.horoscope

import app.parivaar.panchang.content.text.TemplateArg
import app.parivaar.panchang.content.text.TermKind
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Ingress
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * docs/specs/M11.md section 4, `RashifalRulesTest`. Expected ids are computed by hand from the spec formulas:
 * epochDay 20773 = 2026-11-16; 20773 mod 8 = 5 (SEC[5] = SATURN for MESHA); (20773 + 6) mod 8 = 3 (JUPITER for
 * TULA); with n = 3: Moon k = (20773 + 0 + 1) mod 3 + 1 = 3, Saturn k = (20773 + 0 + 6) mod 3 + 1 = 2.
 */
class RashifalRulesTest {
    private val start = Instant.parse("2026-11-16T00:50:00Z")
    private val day = PeriodWindow(start, start + 24.hours, 20773)

    /** Moon in KARKA all day, Saturn in MEENA direct, everything else in MESHA direct; no ingress unless given. */
    private class FakeTransitSource(
        val signs: Map<Graha, Rashi> = mapOf(Graha.MOON to Rashi.KARKA, Graha.SATURN to Rashi.MEENA),
        val retro: Set<Graha> = emptySet(),
        val moonNak: Int = 10,
        val ingress: List<Ingress> = emptyList(),
    ) : TransitSource {
        override fun rashi(g: Graha, at: Instant) = signs[g] ?: Rashi.MESHA

        override fun retrograde(g: Graha, at: Instant) = g in retro

        override fun nakshatra(at: Instant) = moonNak

        override fun ingresses(g: Graha, from: Instant, to: Instant) =
            ingress.filter { it.graha == g && it.at >= from && it.at <= to }
    }

    private fun ids(r: Reading) = r.lines.map { it.id }

    private val one: (String) -> Int = { 1 }
    private val three: (String) -> Int = { 3 }

    @Test
    fun meshaDayWithOneVariant() {
        val r = RashifalRules(FakeTransitSource(), one).h1(Rashi.MESHA, Period.DAY, day)
        assertEquals(listOf("gochar.mo.h4.v1", "gochar.sa.h12.v1"), ids(r))
        assertEquals(Period.DAY, r.period)
    }

    @Test
    fun meshaDayWithThreeVariants() {
        val r = RashifalRules(FakeTransitSource(), three).h1(Rashi.MESHA, Period.DAY, day)
        assertEquals(listOf("gochar.mo.h4.v3", "gochar.sa.h12.v2"), ids(r))
    }

    @Test
    fun zeroVariantsCountAsOne() {
        val r = RashifalRules(FakeTransitSource()) { 0 }.h1(Rashi.MESHA, Period.DAY, day)
        assertEquals(listOf("gochar.mo.h4.v1", "gochar.sa.h12.v1"), ids(r))
    }

    @Test
    fun tulaGetsJupiterAsSecondPlanet() {
        val r = RashifalRules(FakeTransitSource(), one).h1(Rashi.TULA, Period.DAY, day)
        // Jupiter (fake) in MESHA: 7th from TULA.
        assertEquals(listOf("gochar.mo.h10.v1", "gochar.ju.h7.v1"), ids(r))
    }

    @Test
    fun moonShiftMidDay() {
        val at = start + 12.hours
        val src = FakeTransitSource(ingress = listOf(Ingress(Graha.MOON, Rashi.SIMHA, at, retrograde = false)))
        val r = RashifalRules(src, one).h1(Rashi.MESHA, Period.DAY, day)
        assertEquals(listOf("gochar.mo.h4.v1", "rf.moon_shift", "gochar.mo.h5.v1", "gochar.sa.h12.v1"), ids(r))
        assertEquals(
            mapOf("time" to TemplateArg.Time(at), "rashi" to TemplateArg.Term(TermKind.RASHI, 5)),
            r.lines[1].args,
        )
    }

    @Test
    fun ingressAtWindowEndIsExcludedAndAtMostTwoAreShown() {
        val atEnd = Ingress(Graha.MOON, Rashi.SIMHA, day.end, retrograde = false)
        val edge = RashifalRules(FakeTransitSource(ingress = listOf(atEnd)), one).h1(Rashi.MESHA, Period.DAY, day)
        assertEquals(2, ids(edge).size)
        val three = listOf(1, 2, 3).map { Ingress(Graha.MOON, Rashi.entries[3 + it], start + (it * 5).hours, false) }
        val r = RashifalRules(FakeTransitSource(ingress = three), one).h1(Rashi.MESHA, Period.DAY, day)
        val expected = listOf(
            "gochar.mo.h4.v1", "rf.moon_shift", "gochar.mo.h5.v1",
            "rf.moon_shift", "gochar.mo.h6.v1", "gochar.sa.h12.v1",
        )
        assertEquals(expected, ids(r))
    }

    @Test
    fun dayIgnoresSecondaryIngressAndWeekIgnoresMoon() {
        val sat = Ingress(Graha.SATURN, Rashi.MESHA, start + 3.hours, retrograde = false)
        val moon = Ingress(Graha.MOON, Rashi.SIMHA, start + 3.hours, retrograde = false)
        val src = FakeTransitSource(ingress = listOf(sat, moon))
        assertEquals(1, ids(RashifalRules(src, one).h1(Rashi.MESHA, Period.DAY, day)).count { it == "rf.moon_shift" })
        assertTrue("rf.ingress" !in ids(RashifalRules(src, one).h1(Rashi.MESHA, Period.DAY, day)))
        // Week index 2967 (Monday 2026-11-16 = epochDay 20773, floorDiv(20773, 7) = 2967); 2967 mod 3 = 0:
        // SUN, MARS, JUPITER. Saturn is not listed, so neither ingress shows.
        val week = PeriodWindow(start, start + (7 * 24).hours, 2967)
        val w = RashifalRules(src, one).h1(Rashi.MESHA, Period.WEEK, week)
        assertEquals(listOf("gochar.su.h1.v1", "gochar.ma.h1.v1", "gochar.ju.h1.v1"), ids(w))
    }

    @Test
    fun weekIngressAddsIngressLineAfterItsPlanet() {
        val ju = Ingress(Graha.JUPITER, Rashi.VRISHABHA, start + 30.hours, retrograde = false)
        val week = PeriodWindow(start, start + (7 * 24).hours, 2967)
        val r = RashifalRules(FakeTransitSource(ingress = listOf(ju), retro = setOf(Graha.JUPITER, Graha.RAHU)), one)
            .h1(Rashi.MESHA, Period.WEEK, week)
        assertEquals(
            listOf(
                "gochar.su.h1.v1", "gochar.ma.h1.v1", "gochar.ju.h1.v1", "rf.retro", "rf.ingress", "gochar.ju.h2.v1",
            ),
            ids(r),
        )
        assertEquals(setOf("graha", "rashi", "time"), r.lines[4].args.keys)
    }

    @Test
    fun monthAndYearPlanetSets() {
        val month = PeriodWindow(start, start + (30 * 24).hours, 2026L * 12 + 10)
        val year = PeriodWindow(start, start + (365 * 24).hours, 2026)
        val rules = RashifalRules(FakeTransitSource(retro = setOf(Graha.RAHU, Graha.KETU)), one)
        assertEquals(
            listOf("gochar.su.h1.v1", "gochar.ma.h1.v1", "gochar.ve.h1.v1", "gochar.ju.h1.v1"),
            ids(rules.h1(Rashi.MESHA, Period.MONTH, month)),
        )
        // Rahu and Ketu are always retrograde: no rf.retro for them.
        assertEquals(
            listOf("gochar.ju.h1.v1", "gochar.sa.h12.v1", "gochar.ra.h1.v1", "gochar.ke.h1.v1"),
            ids(rules.h1(Rashi.MESHA, Period.YEAR, year)),
        )
    }

    private val natal =
        NatalInputs(lagna = Rashi.SIMHA, moon = Rashi.MESHA, moonNakshatra = 0, dasha = listOf(Graha.MOON, Graha.MARS))

    @Test
    fun h2TaraFirstThenMoonThenDashaLord() {
        // tara(10, 0) = (10 mod 27) mod 9 + 1 = 2.
        val r = RashifalRules(FakeTransitSource(), one).h2(natal, day)
        // MD MOON, AD MARS: Mars (fake MESHA) is 9th from lagna SIMHA. Saturn in MEENA is 12th from MESHA: rising.
        assertEquals(
            listOf("rf.tara.t2", "gochar.mo.h4.v1", "rf.dasha_lord", "gochar.ma.h9.v1", "rf.sadesati.rising"),
            ids(r),
        )
        assertEquals(mapOf("graha" to TemplateArg.Term(TermKind.GRAHA, 3)), r.lines[2].args)
    }

    @Test
    fun h2ChandrashtamaAndNoDashaWhenBothMoon() {
        // Moon in VRISHCHIKA = 8th from MESHA; Saturn in MEENA.
        val src = FakeTransitSource(signs = mapOf(Graha.MOON to Rashi.VRISHCHIKA, Graha.SATURN to Rashi.MITHUNA))
        val r = RashifalRules(src, one).h2(natal.copy(dasha = listOf(Graha.MOON, Graha.MOON)), day)
        assertEquals(listOf("rf.tara.t2", "gochar.mo.h8.v1", "rf.chandrashtama"), ids(r))
        val md = RashifalRules(src, one).h2(natal.copy(dasha = listOf(Graha.VENUS, Graha.MOON)), day)
        assertEquals("gochar.ve.h9.v1", ids(md)[3])
    }

    @Test
    fun h2WeekIsH1WeekForLagnaPlusDasha() {
        val week = PeriodWindow(start, start + (7 * 24).hours, 2967)
        val rules = RashifalRules(FakeTransitSource(), one)
        val r = rules.h2Week(natal, week)
        val expected = ids(rules.h1(Rashi.SIMHA, Period.WEEK, week)) + listOf("rf.dasha_lord", "gochar.ma.h9.v1")
        assertEquals(expected, ids(r))
    }

    @Test
    fun sameInputsTwiceAreEqual() {
        val a = RashifalRules(FakeTransitSource(), three)
        val b = RashifalRules(FakeTransitSource(), three)
        for (s in Rashi.entries) for (p in Period.entries) assertEquals(a.h1(s, p, day), b.h1(s, p, day))
        assertEquals(a.h2(natal, day), b.h2(natal, day))
    }

    @Test
    fun sadeSatiMirrorEqualsGochar() {
        val gochar = Gochar(AstronomyEngineEphemeris())
        for (m in Rashi.entries) for (s in Rashi.entries) {
            val src = FakeTransitSource(signs = mapOf(Graha.MOON to Rashi.KARKA, Graha.SATURN to s))
            val r = RashifalRules(src, one).h2(natal.copy(moon = m, dasha = emptyList()), day)
            val phase = gochar.sadeSati(m, s)
            val expected = if (phase.ordinal == 0) null else "rf.sadesati." + phase.name.lowercase()
            assertEquals(expected, ids(r).lastOrNull { it.startsWith("rf.sadesati.") }, "$m $s")
        }
    }

    @Test
    fun personalTypesAreRedacted() {
        assertEquals("NatalInputs(redacted)", natal.toString())
        assertEquals("Reading(redacted)", Reading(Period.DAY, emptyList()).toString())
        assertEquals("Line(redacted)", Line("rf.retro", emptyMap()).toString())
    }
}
