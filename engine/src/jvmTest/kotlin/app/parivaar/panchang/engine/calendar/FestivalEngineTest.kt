package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.calendar.internal.DayLookup
import app.parivaar.panchang.engine.calendar.internal.EvalContext
import app.parivaar.panchang.engine.calendar.internal.TithiRuleEval
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.internal.SunDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** docs/specs/M04.md section 4, `FestivalEngineTest`. */
class FestivalEngineTest {
    private val day1 = LocalDate(2026, 8, 1)
    private val day2 = LocalDate(2026, 8, 2)

    /** Local time [h]:[m] of [d] at UTC+0 (synthetic days: sunrise 06:00, sunset 18:00). */
    private fun t(d: LocalDate, h: Int, m: Int = 0): Instant =
        Instant.fromEpochSeconds(d.toEpochDays() * 86_400L) + h.hours + m.minutes

    private fun ctx(v: List<TimeRange> = emptyList(), nak: List<TimeRange> = emptyList(), moon: Boolean = false) =
        EvalContext(
            DayLookup({ 0 }) { _, m -> SunDay(m, m + 6.hours, m + 18.hours, m + 30.hours, emptySet()) },
            { sd -> if (moon) sd.sunrise + 14.hours else null },
            { _, _ -> v },
            { _, _, _ -> nak },
        )

    private fun rule(kala: Kala, tie: Tie = Tie.FIRST, none: NoneRule = NoneRule.CONTAINING_START,
        bhadra: List<BhadraStep> = emptyList(), prefer: Int? = null) =
        FestivalRule.TithiAt(listOf(15), null, MonthSystem.AMANTA, kala, tie, prefer, none, bhadra)

    private val raksha = listOf(
        BhadraStep.FreeIn(Kala.APARAHNA), BhadraStep.FreeIn(Kala.PRADOSH), BhadraStep.NextDay(6),
        BhadraStep.AfterBhadraBeforeMidnight,
    )
    private val holika =
        listOf(BhadraStep.FreeIn(Kala.PRADOSH), BhadraStep.AfterBhadraBeforeMidnight, BhadraStep.NextDay(6))
    private val rakshaRule = rule(Kala.APARAHNA, Tie.MAX_COVERAGE, bhadra = raksha)
    private val holikaRule = rule(Kala.PRADOSH, Tie.MAX_COVERAGE, bhadra = holika)

    @Test
    fun bhadraSteps() {
        val x = TimeRange(t(day1, 14), t(day2, 13))
        val v = listOf(TimeRange(t(day1, 14), t(day2, 1, 30)))
        val r = assertNotNull(TithiRuleEval.choose(x, rakshaRule, ctx(v)))
        assertEquals(day2, r.date)
        assertEquals(TimeRange(t(day2, 6), t(day2, 13)), r.muhurat)
        assertTrue(OccFlag.BHADRA_SHIFT in r.flags)
        val h = assertNotNull(TithiRuleEval.choose(x, holikaRule, ctx(v)))
        assertEquals(day2, h.date)
        assertEquals(TimeRange(t(day2, 18), t(day2, 20, 24)), h.muhurat)
        val early = listOf(TimeRange(t(day1, 14), t(day1, 22)))
        val h2 = assertNotNull(TithiRuleEval.choose(x, holikaRule, ctx(early)))
        assertEquals(day1, h2.date)
        assertEquals(TimeRange(t(day1, 22), t(day2, 0)), h2.muhurat)
        assertTrue(OccFlag.BHADRA_SHIFT in h2.flags)
        val short = TimeRange(t(day1, 14), t(day2, 8))
        val r2 = assertNotNull(TithiRuleEval.choose(short, rakshaRule, ctx(early)))
        assertEquals(day1, r2.date)
        assertEquals(TimeRange(t(day1, 22), t(day2, 0)), r2.muhurat)
        val r3 = assertNotNull(TithiRuleEval.choose(short, rakshaRule, ctx(v)))
        assertEquals(day1, r3.date)
        assertNull(r3.muhurat)
        assertTrue(OccFlag.BHADRA_UNAVOIDABLE in r3.flags)
        val free = assertNotNull(TithiRuleEval.choose(x, rakshaRule, ctx()))
        assertEquals(TimeRange(t(day1, 14), t(day1, 15, 36)), free.muhurat)
        assertTrue(OccFlag.BHADRA_SHIFT !in free.flags && OccFlag.BHADRA_UNAVOIDABLE !in free.flags)
    }

    @Test
    fun tiesAndNoneBranches() {
        val vriddhi = TimeRange(t(day1, 5), t(day2, 7))
        val first = assertNotNull(TithiRuleEval.choose(vriddhi, rule(Kala.SUNRISE), ctx()))
        assertEquals(day1, first.date)
        assertEquals(setOf(OccFlag.TIE_BROKEN, OccFlag.VRIDDHI_TITHI), first.flags)
        assertEquals(TimeRange(t(day1, 6), t(day1, 6)), first.kalaWindow)
        assertEquals(day2, TithiRuleEval.choose(vriddhi, rule(Kala.SUNRISE, Tie.SECOND), ctx())?.date)
        val long = TimeRange(t(day1, 11), t(day2, 17))
        assertEquals(day2, TithiRuleEval.choose(long, rule(Kala.MADHYAHNA, Tie.MAX_COVERAGE), ctx())?.date)
        val even = TimeRange(t(day1, 10), t(day2, 17))
        assertEquals(day1, TithiRuleEval.choose(even, rule(Kala.MADHYAHNA, Tie.MAX_COVERAGE), ctx())?.date)
        val nak = listOf(TimeRange(t(day2, 5), t(day2, 9)))
        assertEquals(day2, TithiRuleEval.choose(vriddhi, rule(Kala.SUNRISE, prefer = 3), ctx(nak = nak))?.date)
        val kshaya = TimeRange(t(day1, 7), t(day2, 5))
        val c = assertNotNull(TithiRuleEval.choose(kshaya, rule(Kala.SUNRISE), ctx()))
        assertEquals(day1, c.date)
        assertEquals(setOf(OccFlag.NONE_TOUCHED, OccFlag.KSHAYA_TITHI), c.flags)
        assertNull(TithiRuleEval.choose(kshaya, rule(Kala.SUNRISE, none = NoneRule.SKIP), ctx()))
        // Chaturthi at moonrise with no moonrise anywhere: udaya fallback to the day whose sunrise it holds.
        val noMoon = TimeRange(t(day1, 9), t(day2, 10))
        val udaya = assertNotNull(TithiRuleEval.choose(noMoon, rule(Kala.MOONRISE, none = NoneRule.SUNRISE), ctx()))
        assertEquals(day2, udaya.date)
        assertTrue(OccFlag.MOONRISE_NONE in udaya.flags && OccFlag.NONE_TOUCHED in udaya.flags)
        assertEquals(day1, TithiRuleEval.choose(noMoon, rule(Kala.MOONRISE), ctx())?.date)
        assertEquals(day1, TithiRuleEval.choose(noMoon, rule(Kala.MOONRISE), ctx(moon = true))?.date)
    }

    private val engine = FestivalEngine(GoldenLoader.openDe440())
    private val delhi = Place(28.6139, 77.2090)
    private val ist = { _: LocalDate -> 19_800 }

    private fun def(id: String, rule: FestivalRule, adhika: AdhikaPolicy = AdhikaPolicy.NIJA) =
        FestivalDef(id, "term:fest_$id", setOf("all"), rule, adhika, emptyList(), RuleStatus.VERIFY, "src.test")

    private fun tithi(t: Int, masa: Masa?, kala: Kala, tie: Tie = Tie.FIRST) =
        FestivalRule.TithiAt(listOf(t), masa, MonthSystem.AMANTA, kala, tie, null, NoneRule.CONTAINING_START)

    private fun years(defs: List<FestivalDef>, place: Place = delhi) =
        engine.occurrences(defs, LocalDate(2024, 1, 1), LocalDate(2030, 12, 31), place, utcOffsetSeconds = ist)

    @Test
    fun delhiRuleTypes() {
        val ganesh = def("ganesh_chaturthi", tithi(4, Masa.BHADRAPADA, Kala.MADHYAHNA, Tie.MAX_COVERAGE))
        val makar = def("makar_sankranti", FestivalRule.SankrantiRule(Rashi.MAKARA, SankrantiDay.BEFORE_SUNSET))
        val leap = def("leap", FestivalRule.FixedGregorian(2, 29))
        val all = years(listOf(ganesh, makar, leap))
        assertEquals(all.sortedWith(compareBy<FestivalOccurrence> { it.date }.thenBy { it.id }), all)
        assertTrue(LocalDate(2024, 9, 7) in all.filter { it.id == "ganesh_chaturthi" }.map { it.date })
        assertTrue(LocalDate(2024, 1, 15) in all.filter { it.id == "makar_sankranti" }.map { it.date })
        val leaps = all.filter { it.id == "leap" }.map { it.date }
        assertEquals(listOf(LocalDate(2024, 2, 29), LocalDate(2028, 2, 29)), leaps)
        assertEquals(7, all.count { it.id == "ganesh_chaturthi" })
        val monday = FestivalRule.WeekdayTithi(Vaar.MONDAY, tithi(30, null, Kala.SUNRISE))
        val somvati = def("somvati", monday, AdhikaPolicy.BOTH)
        val s = years(listOf(somvati))
        assertTrue(s.isNotEmpty() && s.all { Vaar.of(it.date) == Vaar.MONDAY && it.tithi == 30 })
    }

    @Test
    fun nishitaAfterLocalMidnightKeepsThePanchangDate() {
        val shivratri = def("masik_shivratri", tithi(29, null, Kala.NISHITA, Tie.MAX_COVERAGE), AdhikaPolicy.BOTH)
        val occ = years(listOf(shivratri))
        assertTrue(occ.size in 84..88)
        val midnights = occ.map {
            Instant.fromEpochSeconds(it.date.plus(DatePeriod(days = 1)).toEpochDays() * 86_400L - 19_800)
        }
        assertTrue(occ.zip(midnights).any { (o, m) -> o.kalaWindow!!.start >= m }, "a nishita after civil midnight")
        assertTrue(occ.zip(midnights).all { (o, m) -> o.kalaWindow!!.start > m - 3.hours })
    }

    @Test
    fun twoOccurrencesInOneYearAndRelativeAcrossTheYearEnd() {
        val pausha = def("pausha_purnima", tithi(15, Masa.PAUSHA, Kala.SUNRISE))
        val byYear = years(listOf(pausha)).groupBy { it.date.year }
        assertTrue(byYear.values.any { it.size == 2 } || byYear.values.any { it.isEmpty() } || byYear.size < 7)
        val eve = def("eve", FestivalRule.FixedGregorian(12, 31))
        val next = def("next", FestivalRule.Relative("eve", 1))
        val jan = engine.occurrences(listOf(eve, next), LocalDate(2026, 1, 1), LocalDate(2026, 1, 31), delhi,
            utcOffsetSeconds = ist)
        assertEquals(listOf("next" to LocalDate(2026, 1, 1)), jan.map { it.id to it.date })
        val dahan = holikaRule.copy(masa = Masa.PHALGUNA)
        val holika = def("holika_dahan", dahan)
        val holi = def("holi", FestivalRule.Relative("holika_dahan", 1))
        val h = years(listOf(holika, holi)).groupBy { it.id }
        assertEquals(7, h.getValue("holika_dahan").size)
        val dayAfter = h.getValue("holika_dahan").map { it.date.plus(DatePeriod(days = 1)) }
        assertEquals(dayAfter, h.getValue("holi").map { it.date })
        assertTrue(h.getValue("holi").all { it.kalaWindow == null && it.tithi == null && it.label != null })
        assertFailsWith<IllegalArgumentException> { years(listOf(holi)) }
    }



    @Test
    fun ekadashiTraditions() {
        fun ek(t: Tradition) = FestivalRule.Ekadashi(t, null, MonthSystem.AMANTA, null)
        val smarta = def("ekadashi_smarta", ek(Tradition.SMARTA), AdhikaPolicy.BOTH)
        val vaishnava = def("ekadashi_vaishnava", ek(Tradition.VAISHNAVA), AdhikaPolicy.BOTH)
        val occ = years(listOf(smarta, vaishnava)).groupBy { it.id }
        val s = occ.getValue("ekadashi_smarta")
        val v = occ.getValue("ekadashi_vaishnava")
        assertEquals(s.size, v.size)
        for ((a, b) in s.zip(v)) {
            assertEquals(b.date > a.date, OccFlag.DASHAMI_VIDDHA in b.flags)
            assertTrue(b.date == a.date || b.date == a.date.plus(DatePeriod(days = 1)))
            assertEquals(Paksha.SHUKLA == (if (a.tithi == 11) Paksha.SHUKLA else Paksha.KRISHNA), a.tithi == 11)
        }
        assertTrue(v.any { OccFlag.DASHAMI_VIDDHA in it.flags })
        assertTrue(s.all { it.parana != null || OccFlag.PARANA_UNDEFINED in it.flags })
    }

    @Test
    fun keralaOnamNeedsAthamInChingam() {
        val tvm = Place(8.5241, 76.9366)
        val chingam =
            FestivalRule.NakshatraInSolarMonth(21, Rashi.SIMHA, SolarRegion.MALAYALAM, Kala.SUNRISE, Pick.FIRST)
        val onam = def("onam", chingam.copy(precededBy = 12))
        val plain = def("plain", chingam)
        val occ = engine.occurrences(listOf(onam, plain), LocalDate(2024, 1, 1), LocalDate(2043, 12, 31), tvm,
            utcOffsetSeconds = ist).groupBy { it.id }
        val o = occ.getValue("onam")
        val p = occ.getValue("plain")
        assertTrue(p.size in 19..20)
        assertTrue(o.isNotEmpty() && o.all { a -> p.any { it.date.year == a.date.year && it.date <= a.date } })
        // 2024: the first Thiruvonam of Chingam (20 Aug) has its Atham in Karkidakam, so it drops (published 15 Sep).
        assertEquals(LocalDate(2024, 9, 15), o.single { it.date.year == 2024 }.date)
        assertTrue(p.single { it.date.year == 2024 }.date < LocalDate(2024, 9, 1))
        val thai = def(
            "thaipusam",
            FestivalRule.NakshatraInSolarMonth(
                7, Rashi.MAKARA, SolarRegion.TAMIL, Kala.AHORATRA, Pick.NEAREST_FULL_MOON,
            ),
        )
        val t = years(listOf(thai), Place(13.0827, 80.2707))
        assertEquals(7, t.size)
        assertTrue(t.all { it.date.month.ordinal in 0..1 && it.muhurat != null }, "thaipusam in Thai")
    }
}
