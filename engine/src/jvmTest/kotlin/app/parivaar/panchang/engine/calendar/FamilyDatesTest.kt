package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.RuleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** docs/specs/M10.md, tests: `FamilyDatesTest`. */
class FamilyDatesTest {
    private val finder = FamilyDateFinder(GoldenLoader.openDe440())
    private val delhi = Place(28.6139, 77.2090)
    private val ist = 19_800
    private val shravana = LunarLabel(Masa.SHRAVANA, false, null)
    private val bhadrapada = LunarLabel(Masa.BHADRAPADA, false, null)
    private val facts = JanmaFacts(22, shravana, bhadrapada, 12, Rashi.SIMHA)
    private val amanta = FamilyDatePrefs(MonthSystem.AMANTA, NakshatraMonthBasis.SOLAR, SolarRegion.TAMIL)
    private val purnimanta = FamilyDatePrefs(MonthSystem.PURNIMANTA, NakshatraMonthBasis.LUNAR, SolarRegion.BENGALI)

    private fun midnight(d: LocalDate): Instant = Instant.fromEpochSeconds(d.toEpochDays() * 86_400L - ist)

    @Test
    fun defMapsEveryTableRow() {
        val d = FamilyDateDefaults
        val t = finder.def(FamilyDateKind.TITHI_BIRTHDAY, facts, amanta, "fam_1")
        assertEquals(
            FestivalRule.TithiAt(listOf(22), Masa.SHRAVANA, MonthSystem.AMANTA, Kala.SUNRISE, Tie.FIRST, null,
                NoneRule.CONTAINING_START),
            t.rule,
        )
        assertEquals(AdhikaPolicy.NIJA, t.adhika)
        assertEquals(RuleStatus.VERIFY, t.status)
        assertEquals(setOf("all"), t.regions)
        assertEquals("term:fest_fam_1", t.nameKey)
        assertEquals(d.SOURCE_ID, t.sourceId)
        assertTrue(t.vratRules.isEmpty())
        val tp = finder.def(FamilyDateKind.TITHI_BIRTHDAY, facts, purnimanta, "fam_2").rule as FestivalRule.TithiAt
        assertEquals(Masa.BHADRAPADA to MonthSystem.PURNIMANTA, tp.masa to tp.system)
        assertEquals(
            FestivalRule.NakshatraInSolarMonth(12, Rashi.SIMHA, SolarRegion.TAMIL, Kala.SUNRISE, Pick.FIRST),
            finder.def(FamilyDateKind.NAKSHATRA_BIRTHDAY, facts, amanta, "fam_3").rule,
        )
        assertEquals(
            FestivalRule.NakshatraInLunarMonth(12, Masa.BHADRAPADA, MonthSystem.PURNIMANTA, Kala.SUNRISE, Pick.FIRST),
            finder.def(FamilyDateKind.NAKSHATRA_BIRTHDAY, facts, purnimanta, "fam_4").rule,
        )
        val sh = finder.def(FamilyDateKind.SHRADDHA, facts, amanta, "fam_5")
        assertEquals(
            FestivalRule.TithiAt(listOf(22), Masa.SHRAVANA, MonthSystem.AMANTA, Kala.APARAHNA, Tie.FIRST, null,
                NoneRule.CONTAINING_START),
            sh.rule,
        )
        assertEquals(AdhikaPolicy.NIJA, sh.adhika)
    }

    @Test
    fun adhikaBirthIsKeptInTheNijaMonthOfTheSameName() {
        val adhika = JanmaFacts(10, LunarLabel(Masa.JYESHTHA, true, null), LunarLabel(Masa.JYESHTHA, true, null), 3,
            Rashi.VRISHABHA)
        val def = finder.def(FamilyDateKind.TITHI_BIRTHDAY, adhika, amanta, "fam_6")
        assertEquals(Masa.JYESHTHA, (def.rule as FestivalRule.TithiAt).masa)
        assertEquals(AdhikaPolicy.NIJA, def.adhika)
        val occ = finder.occurrences(listOf(def), LocalDate(2026, 1, 1), LocalDate(2026, 12, 31), delhi) { ist }
        assertEquals(1, occ.size, "one nija Jyeshtha occurrence in 2026")
        assertFalse(assertNotNull(occ[0].label).adhika)
        assertTrue(occ[0].date > LocalDate(2026, 6, 15), "after adhika Jyeshtha (ends 2026-06-15)")
    }

    @Test
    fun idsNeverCarryInputText() {
        for (bad in listOf("Ravi", "fam_ravi", "fam_", "fam_1a", "ravi_1", "fam_1 ", "FAM_1")) {
            assertFailsWith<IllegalArgumentException>(bad) { finder.def(FamilyDateKind.SHRADDHA, facts, amanta, bad) }
        }
        val def = finder.def(FamilyDateKind.TITHI_BIRTHDAY, facts, amanta, "fam_42")
        val occ = finder.occurrences(listOf(def), LocalDate(2025, 1, 1), LocalDate(2026, 12, 31), delhi) { ist }
        assertEquals(2, occ.size)
        assertTrue(occ.all { it.id == "fam_42" })
    }

    @Test
    fun toStringIsRedacted() {
        val values = listOf(
            facts, amanta, JanmaResult.Exact(facts), JanmaResult.Ambiguous(listOf(1, 2), listOf(3), facts),
            FestivalRule.NakshatraInLunarMonth(12, Masa.MAGHA, MonthSystem.AMANTA, Kala.SUNRISE, Pick.LAST),
        )
        for (v in values) {
            val s = v.toString()
            assertTrue(s.endsWith("(redacted)"), s)
            assertFalse(s.any { it.isDigit() } || "SIMHA" in s || "SHRAVANA" in s || "TAMIL" in s, s)
        }
        assertFailsWith<IllegalArgumentException> { JanmaResult.Ambiguous(listOf(1), listOf(3), facts) }
        assertFailsWith<IllegalArgumentException> { JanmaFacts(0, shravana, shravana, 1, Rashi.MESHA) }
        assertFailsWith<IllegalArgumentException> { JanmaFacts(1, shravana, shravana, 27, Rashi.MESHA) }
    }

    @Test
    fun janmaOfDayIsExactOnlyWithOneTithiAndOneNakshatra() {
        var exact = 0
        var ambiguous = 0
        var d = LocalDate(2026, 1, 1)
        var n = 0
        while (n++ < DAYS && (exact == 0 || ambiguous < MIN_AMBIGUOUS)) {
            val m0 = midnight(d)
            val last = m0 + 1.days - 1.seconds
            when (val r = finder.janmaOfDay(d, ist, delhi)) {
                is JanmaResult.Exact -> {
                    exact++
                    assertEquals(finder.janma(m0).tithi, r.facts.tithi)
                    assertEquals(finder.janma(last).tithi, r.facts.tithi)
                    assertEquals(finder.janma(m0).nakshatra, r.facts.nakshatra)
                    assertEquals(finder.janma(last).nakshatra, r.facts.nakshatra)
                }
                is JanmaResult.Ambiguous -> {
                    ambiguous++
                    assertEquals(finder.janma(m0).tithi, r.tithis.first())
                    assertEquals(finder.janma(last).tithi, r.tithis.last())
                    assertEquals(finder.janma(m0).nakshatra, r.nakshatras.first())
                    assertEquals(finder.janma(last).nakshatra, r.nakshatras.last())
                    assertTrue(r.atSunrise.tithi in r.tithis && r.atSunrise.nakshatra in r.nakshatras)
                    assertEquals(r.atSunrise, r.factsAtSunrise())
                }
            }
            d = d.plus(DatePeriod(days = 1))
        }
        assertTrue(exact > 0 && ambiguous > 0, "both kinds of day in $DAYS days (exact $exact)")
    }

    @Test
    fun janmaAtSunriseMatchesTheCalendarDay() {
        val calendar = CalendarCalculator(GoldenLoader.openDe440())
        var d = LocalDate(2026, 5, 1)
        repeat(SUNRISE_DAYS) {
            val day = calendar.day(d, ist, delhi)
            val f = finder.janmaOfDay(d, ist, delhi).factsAtSunrise()
            assertEquals(day.tithiAtSunrise, f.tithi, "$d tithi")
            assertEquals(day.amanta, f.amanta, "$d amanta")
            assertEquals(day.purnimanta, f.purnimanta, "$d purnimanta")
            d = d.plus(DatePeriod(days = 1))
        }
    }

    @Test
    fun lunarMonthRuleFollowsTheAdhikaPolicy() {
        val rule = FestivalRule.NakshatraInLunarMonth(5, Masa.JYESHTHA, MonthSystem.AMANTA, Kala.AHORATRA, Pick.FIRST)
        fun occ(p: AdhikaPolicy) = finder.occurrences(
            listOf(FestivalDef("fam_7", "term:fest_fam_7", setOf("all"), rule, p, emptyList(), RuleStatus.VERIFY, "s")),
            LocalDate(2026, 1, 1), LocalDate(2026, 12, 31), delhi,
        ) { ist }
        val both = occ(AdhikaPolicy.BOTH)
        assertEquals(2, both.size, "adhika and nija Jyeshtha 2026")
        assertEquals(listOf(true, false), both.map { OccFlag.ADHIKA_MASA in it.flags })
        assertEquals(listOf(both[1].date), occ(AdhikaPolicy.NIJA).map { it.date })
        assertEquals(listOf(both[0].date), occ(AdhikaPolicy.ADHIKA).map { it.date })
        assertTrue(both.all { it.tithi == null && it.kala == Kala.AHORATRA && it.label?.masa == Masa.JYESHTHA })
        val last = finder.occurrences(
            listOf(FestivalDef("fam_8", "term:fest_fam_8", setOf("all"), rule.copy(pick = Pick.LAST),
                AdhikaPolicy.NIJA, emptyList(), RuleStatus.VERIFY, "s")),
            LocalDate(2026, 1, 1), LocalDate(2026, 12, 31), delhi,
        ) { ist }
        assertTrue(last.single().date >= both[1].date)
    }

    /**
     * Review regression: purnimanta nija Jyeshtha 2026 is split by adhika Jyeshtha (Krishna half about 1-16 May,
     * Shukla half about 15-29 June). A window starting in late June must still see the May half, so FIRST never
     * moves into June when May already has a matching day: a narrow window equals the wide one cut to it.
     */
    @Test
    fun lunarMonthSplitByAdhikaIsCompleteInANarrowWindow() {
        val defs = (0 until 27).flatMap { n ->
            listOf(Kala.AHORATRA, Kala.SUNRISE).mapIndexed { k, kala ->
                val rule =
                    FestivalRule.NakshatraInLunarMonth(n, Masa.JYESHTHA, MonthSystem.PURNIMANTA, kala, Pick.FIRST)
                val id = "fam_${100 * k + n}"
                FestivalDef(
                    id, "term:fest_$id", setOf("all"), rule, AdhikaPolicy.NIJA, emptyList(), RuleStatus.VERIFY, "s",
                )
            }
        }
        val wide = finder.occurrences(defs, LocalDate(2026, 1, 1), LocalDate(2027, 12, 31), delhi) { ist }
        assertTrue(wide.any { it.date.year == 2026 && it.date.monthNumber == 5 }, "May half used by FIRST")
        for (day in 24..30) {
            val first = LocalDate(2026, 6, day)
            val narrow = FamilyDateFinder(GoldenLoader.openDe440())
                .occurrences(defs, first, LocalDate(2027, 6, 30), delhi) { ist }
            assertEquals(
                wide.filter { it.date >= first && it.date <= LocalDate(2027, 6, 30) }.map { it.id to it.date },
                narrow.map { it.id to it.date },
                "window from $first",
            )
        }
    }

    private companion object {
        /** A tithi and a nakshatra both spanning a whole civil day is rare (both must last > 24 h and overlap). */
        const val DAYS = 1_100
        const val MIN_AMBIGUOUS = 30
        const val SUNRISE_DAYS = 45
    }
}
