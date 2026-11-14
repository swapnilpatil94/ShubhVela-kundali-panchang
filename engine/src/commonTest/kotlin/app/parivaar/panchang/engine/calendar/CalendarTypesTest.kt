package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.engine.panchang.Vaar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** docs/specs/M04.md section 4, `CalendarTypesTest`. */
class CalendarTypesTest {
    private val t0 = Instant.parse("2026-05-16T20:01:23.456789012Z")
    private val d0 = LocalDate(2026, 6, 1)

    private fun hasDigit(text: String?): Boolean = text.orEmpty().any { it.isDigit() }

    private fun assertRejects(block: () -> Unit) {
        val e = assertFailsWith<IllegalArgumentException> { block() }
        assertFalse(hasDigit(e.message), "message has a digit")
    }

    private fun sk(into: Rashi, days: Int) = Sankranti(into, t0 + days.days)

    private fun lunation(sankrantis: List<Sankranti>, masa: Masa, adhika: Boolean, kshaya: Masa?) =
        Lunation(t0, t0 + 15.days, t0 + 29.days, sankrantis, masa, adhika, kshaya)

    private val tithiAt = FestivalRule.TithiAt(listOf(23), Masa.BHADRAPADA, MonthSystem.PURNIMANTA, Kala.NISHITA,
        Tie.FIRST, 3, NoneRule.CONTAINING_START)

    private fun def(id: String, regions: Set<String>, rule: FestivalRule = tithiAt) =
        FestivalDef(id, "term:fest_$id", regions, rule, AdhikaPolicy.NIJA, listOf("vrat_x"), RuleStatus.VERIFY,
            "src.traditional.unverified")

    private val label = LunarLabel(Masa.JYESHTHA, adhika = true, kshayaMasa = null)
    private val samvat = Samvat(1948, 2083, 2082, 40, 40, 1433, 1201)
    private val lunarTotal = GlobalEclipse(false, t0, 0.3, EclipseKind.LUNAR_TOTAL, 1.2, 2.2,
        mapOf(Contact.P1 to t0 - 3.hours, Contact.U1 to t0 - 2.hours, Contact.P4 to t0 + 3.hours))
    private val solarGlobal = GlobalEclipse(true, t0, 0.4, null, null, null, emptyMap())

    @Test
    fun masaOrdinalIsRashiEntered() {
        assertEquals(Rashi.entries.size, Masa.entries.size)
        val pairs = listOf(Masa.CHAITRA to Rashi.MESHA, Masa.JYESHTHA to Rashi.MITHUNA,
            Masa.KARTIKA to Rashi.VRISHCHIKA, Masa.PAUSHA to Rashi.MAKARA,
            Masa.MAGHA to Rashi.KUMBHA, Masa.PHALGUNA to Rashi.MEENA)
        for ((m, r) in pairs) assertEquals(r.ordinal, m.ordinal, m.name)
        val vasanta = Masa.entries.filter { Ritu.entries[it.ordinal / 2] == Ritu.VASANTA }
        assertEquals(listOf(Masa.CHAITRA, Masa.VAISHAKHA), vasanta)
        assertEquals(Ritu.SHISHIRA, Ritu.entries[Masa.PHALGUNA.ordinal / 2])
        val starts = SolarRegion.entries.map { it.yearStart }
        assertEquals(listOf(Rashi.MESHA, Rashi.SIMHA, Rashi.MESHA, Rashi.MESHA), starts)
    }

    @Test
    fun enumShapes() {
        assertEquals(8, Kala.entries.size)
        assertEquals(12, OccFlag.entries.size)
        assertEquals(4, SankrantiDay.entries.size)
        assertEquals(listOf("CONTAINING_START", "SUNRISE", "SKIP"), NoneRule.entries.map { it.name })
        assertEquals(3, Pick.entries.size)
        assertEquals(listOf(true, true, true, false, false, false), EclipseKind.entries.map { it.isSolar() })
        assertEquals(10, Contact.entries.size)
    }

    @Test
    fun timeRangeAndPoints() {
        TimeRange(t0, t0)
        TimeRange(t0, t0 + 1.hours)
        assertRejects { TimeRange(t0 + 1.hours, t0) }
    }

    @Test
    fun lunationConsistency() {
        lunation(listOf(sk(Rashi.MESHA, 5)), Masa.CHAITRA, adhika = false, kshaya = null)
        lunation(emptyList(), Masa.JYESHTHA, adhika = true, kshaya = null)
        lunation(listOf(sk(Rashi.DHANU, 1), sk(Rashi.MAKARA, 28)), Masa.MARGASHIRSHA, false, Masa.PAUSHA)
        lunation(listOf(sk(Rashi.MEENA, 1), sk(Rashi.MESHA, 28)), Masa.PHALGUNA, adhika = false, kshaya = Masa.CHAITRA)
        assertRejects { Lunation(t0, t0, t0 + 29.days, emptyList(), Masa.CHAITRA, true, null) }
        assertRejects { lunation(emptyList(), Masa.CHAITRA, adhika = false, kshaya = null) }
        assertRejects { lunation(listOf(sk(Rashi.MESHA, 5)), Masa.CHAITRA, adhika = true, kshaya = null) }
        assertRejects { lunation(listOf(sk(Rashi.MESHA, 5)), Masa.VAISHAKHA, adhika = false, kshaya = null) }
        assertRejects { lunation(listOf(sk(Rashi.MESHA, 29)), Masa.CHAITRA, adhika = false, kshaya = null) }
        assertRejects { lunation(listOf(sk(Rashi.MESHA, -1)), Masa.CHAITRA, adhika = false, kshaya = null) }
        assertRejects { lunation(listOf(sk(Rashi.MESHA, 5)), Masa.CHAITRA, adhika = false, kshaya = Masa.VAISHAKHA) }
        assertRejects {
            lunation(listOf(sk(Rashi.DHANU, 1), sk(Rashi.KUMBHA, 28)), Masa.MARGASHIRSHA, false, Masa.MAGHA)
        }
        assertRejects {
            lunation(listOf(sk(Rashi.DHANU, 1), sk(Rashi.MAKARA, 28)), Masa.MARGASHIRSHA, false, null)
        }
        val three = listOf(sk(Rashi.DHANU, 1), sk(Rashi.MAKARA, 14), sk(Rashi.KUMBHA, 28))
        assertRejects { lunation(three, Masa.MARGASHIRSHA, adhika = false, kshaya = Masa.PAUSHA) }
    }

    @Test
    fun labelsSolarAndSamvatValidate() {
        LunarLabel(Masa.PHALGUNA, false, Masa.CHAITRA)
        assertRejects { LunarLabel(Masa.PAUSHA, true, Masa.MAGHA) }
        assertRejects { LunarLabel(Masa.PAUSHA, false, Masa.PHALGUNA) }
        SolarDate(SolarRegion.TAMIL, Rashi.MESHA, 32, d0)
        assertRejects { SolarDate(SolarRegion.TAMIL, Rashi.MESHA, 0, d0) }
        assertRejects { SolarDate(SolarRegion.TAMIL, Rashi.MESHA, 33, d0) }
        assertRejects { SolarMonthStart(SolarRegion.ODIA, Rashi.MESHA, d0, sk(Rashi.MEENA, 0), null) }
        assertRejects { samvat.copy(samvatsara = 0) }
        assertRejects { samvat.copy(samvatsaraTamil = 61) }
        samvat.copy(samvatsara = 60, samvatsaraTamil = 1)
    }

    @Test
    fun calendarDayValidates() {
        val solar = mapOf(SolarRegion.BENGALI to SolarDate(SolarRegion.BENGALI, Rashi.VRISHABHA, 18, d0))
        val flags = setOf(DayFlag.SUN_FALLBACK)
        val day = CalendarDay(d0, 30, label, label, samvat, Ritu.GRISHMA, Ayana.UTTARAYANA, solar, flags)
        assertRejects { day.copy(tithiAtSunrise = 0) }
        assertRejects { day.copy(tithiAtSunrise = 31) }
        assertRejects { day.copy(solar = mapOf(SolarRegion.TAMIL to solar.getValue(SolarRegion.BENGALI))) }
    }

    @Test
    fun rulesValidate() {
        assertRejects { tithiAt.copy(tithi = emptyList()) }
        assertRejects { tithiAt.copy(tithi = listOf(0)) }
        assertRejects { tithiAt.copy(tithi = listOf(31)) }
        assertRejects { tithiAt.copy(tithi = listOf(13, 13)) }
        assertRejects { tithiAt.copy(preferNakshatra = 27) }
        assertRejects { tithiAt.copy(preferNakshatra = -1) }
        tithiAt.copy(tithi = listOf(13, 28), preferNakshatra = null)
        FestivalRule.FixedGregorian(2, 29)
        FestivalRule.FixedGregorian(12, 31)
        assertRejects { FestivalRule.FixedGregorian(0, 1) }
        assertRejects { FestivalRule.FixedGregorian(13, 1) }
        assertRejects { FestivalRule.FixedGregorian(2, 30) }
        assertRejects { FestivalRule.FixedGregorian(4, 31) }
        assertRejects { FestivalRule.FixedGregorian(1, 0) }
        val nak =
            FestivalRule.NakshatraInSolarMonth(26, Rashi.SIMHA, SolarRegion.MALAYALAM, Kala.SUNRISE, Pick.FIRST, 12)
        assertRejects { nak.copy(nakshatra = 27) }
        assertRejects { nak.copy(nakshatra = -1) }
        assertRejects { nak.copy(precededBy = 27) }
        nak.copy(kala = Kala.AHORATRA, pick = Pick.NEAREST_FULL_MOON, precededBy = null)
        assertRejects { tithiAt.copy(kala = Kala.AHORATRA) }
        assertRejects { BhadraStep.FreeIn(Kala.AHORATRA) }
        assertRejects { BhadraStep.NextDay(0) }
        assertRejects { BhadraStep.NextDay(61) }
        val steps =
            listOf(BhadraStep.FreeIn(Kala.APARAHNA), BhadraStep.NextDay(6), BhadraStep.AfterBhadraBeforeMidnight)
        assertEquals(steps, tithiAt.copy(bhadra = steps).bhadra)
        FestivalRule.Relative("holika_dahan", -3)
        assertRejects { FestivalRule.Relative("holika_dahan", 0) }
        assertRejects { FestivalRule.Relative("holika_dahan", 4) }
        assertRejects { FestivalRule.Relative("Holika", 1) }
    }

    @Test
    fun festivalDefsAndSets() {
        val a = def("rama_navami", setOf("all"))
        val b = def("puthandu", setOf("ta"), FestivalRule.SankrantiRule(Rashi.MESHA, SankrantiDay.BEFORE_SUNSET))
        val c = def("karwa_chauth", setOf("north", "west"))
        assertRejects { def("Rama", setOf("all")) }
        assertRejects { def("a".repeat(41), setOf("all")) }
        assertRejects { def("", setOf("all")) }
        assertRejects { a.copy(nameKey = "term:fest_holi") }
        assertRejects { def("holi", emptySet()) }
        assertRejects { def("holi", setOf("xx")) }
        assertRejects { a.copy(vratRules = listOf("Bad id")) }
        assertRejects { a.copy(sourceId = " ") }
        val set = FestivalSet("pan_india.json", listOf(a, b, c))
        assertEquals(listOf(a, c), set.forRegion("north"))
        assertEquals(listOf(a, b), set.forRegion("ta"))
        assertEquals(listOf(a), set.forRegion("kn"))
        assertRejects { FestivalSet("x.json", listOf(a, a)) }
        val rel = def("holi", setOf("all"), FestivalRule.Relative("rama_navami", 1))
        assertEquals(listOf(a, rel), FestivalSet("x.json", listOf(a, rel)).forRegion("ta"))
        assertRejects { FestivalSet("x.json", listOf(rel)) }
        val chain = def("holi2", setOf("all"), FestivalRule.Relative("holi", 1))
        assertRejects { FestivalSet("x.json", listOf(a, rel, chain)) }
        val input = mutableListOf(a)
        val copy = FestivalSet("x.json", input)
        input.add(b)
        assertEquals(listOf(a), copy.defs)
    }

    @Test
    fun occurrenceValidates() {
        val occ = FestivalOccurrence("ekadashi_vaishnava", d0, label, 11, Kala.ARUNODAYA, TimeRange(t0, t0 + 1.hours),
            null, TimeRange(t0 + 1.days, t0 + 1.days + 3.hours), setOf(OccFlag.DASHAMI_VIDDHA))
        assertRejects { occ.copy(tithi = 0) }
        assertRejects { occ.copy(tithi = 31) }
        assertRejects { occ.copy(id = "Bad") }
        occ.copy(tithi = null, label = null)
    }

    @Test
    fun eclipsesValidate() {
        assertRejects { lunarTotal.copy(gammaDeg = Double.NaN) }
        assertRejects { lunarTotal.copy(gammaDeg = -0.1) }
        assertRejects { lunarTotal.copy(lunarKind = null) }
        assertRejects { lunarTotal.copy(lunarKind = EclipseKind.SOLAR_TOTAL) }
        assertRejects { lunarTotal.copy(umbralMag = null) }
        assertRejects { lunarTotal.copy(penumbralMag = Double.POSITIVE_INFINITY) }
        assertRejects { lunarTotal.copy(contacts = mapOf(Contact.C1 to t0)) }
        assertRejects { solarGlobal.copy(lunarKind = EclipseKind.LUNAR_TOTAL) }
        assertRejects { solarGlobal.copy(umbralMag = 0.5) }
        val vis = TimeRange(t0 - 1.hours, t0 + 1.hours)
        val solarLocal = LocalEclipse(solarGlobal, EclipseKind.SOLAR_TOTAL, 1.01, t0,
            mapOf(Contact.C1 to t0 - 1.hours, Contact.C4 to t0 + 1.hours), vis, TimeRange(t0 - 13.hours, t0 + 1.hours))
        assertRejects { solarLocal.copy(kind = EclipseKind.LUNAR_TOTAL) }
        assertRejects { solarLocal.copy(magnitude = Double.NaN) }
        assertRejects { solarLocal.copy(contacts = mapOf(Contact.U1 to t0)) }
        assertRejects { solarLocal.copy(visible = null) }
        solarLocal.copy(visible = null, sutak = null)
        val lunarLocal = LocalEclipse(lunarTotal, EclipseKind.LUNAR_TOTAL, 1.2, t0, lunarTotal.contacts, vis, vis)
        assertRejects { lunarLocal.copy(kind = EclipseKind.LUNAR_PARTIAL) }
        assertRejects { lunarLocal.copy(contacts = mapOf(Contact.C2 to t0)) }
        val penumbral = lunarTotal.copy(lunarKind = EclipseKind.LUNAR_PENUMBRAL)
        assertRejects { LocalEclipse(penumbral, EclipseKind.LUNAR_PENUMBRAL, 0.5, t0, emptyMap(), vis, vis) }
        LocalEclipse(penumbral, EclipseKind.LUNAR_PENUMBRAL, 0.5, t0, emptyMap(), vis, null)
    }

    @Test
    fun everyDataClassIsRedacted() {
        val tr = TimeRange(t0, t0 + 1.hours)
        val sankranti = sk(Rashi.MESHA, 5)
        val solarDate = SolarDate(SolarRegion.TAMIL, Rashi.MESHA, 12, d0)
        val values: List<Any> = listOf(
            tr,
            sankranti,
            lunation(listOf(sankranti), Masa.CHAITRA, adhika = false, kshaya = null),
            label,
            SolarMonthStart(SolarRegion.MALAYALAM, Rashi.MESHA, d0, sankranti, t0 + 3.hours),
            solarDate,
            samvat,
            CalendarDay(d0, 12, label, label, samvat, Ritu.VASANTA, Ayana.UTTARAYANA,
                mapOf(SolarRegion.TAMIL to solarDate), emptySet()),
            tithiAt,
            FestivalRule.Ekadashi(Tradition.VAISHNAVA, Masa.ASHADHA, MonthSystem.AMANTA, Paksha.SHUKLA),
            FestivalRule.SankrantiRule(Rashi.MAKARA, SankrantiDay.BEFORE_SUNSET),
            FestivalRule.FixedGregorian(2, 29),
            FestivalRule.NakshatraInSolarMonth(21, Rashi.SIMHA, SolarRegion.MALAYALAM, Kala.AHORATRA, Pick.LAST, null),
            FestivalRule.Relative("holika_dahan", 1),
            BhadraStep.FreeIn(Kala.PRADOSH),
            BhadraStep.AfterBhadraBeforeMidnight,
            BhadraStep.NextDay(6),
            FestivalRule.WeekdayTithi(Vaar.MONDAY, tithiAt.copy(tithi = listOf(30))),
            def("ganesh_chaturthi4", setOf("mr", "gu")),
            FestivalOccurrence("holi", d0, label, 16, Kala.SUNRISE, tr, tr, null, setOf(OccFlag.TIE_BROKEN)),
            lunarTotal,
            LocalEclipse(lunarTotal, EclipseKind.LUNAR_TOTAL, 1.2, t0, lunarTotal.contacts, tr, tr),
        )
        for (v in values) {
            val name = v::class.simpleName
            assertEquals("$name(redacted)", v.toString())
            assertFalse(hasDigit(v.toString()), "toString has a digit")
        }
        assertEquals(22, values.map { it::class }.toSet().size)
    }
}
