package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.RuleFormatException
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.engine.panchang.Vaar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/specs/M04.md section 4, `FestivalRulesTest`. */
class FestivalRulesTest {
    private val names = listOf("pan_india", "recurring", "tamil", "malayalam", "bengali", "odia")
    private val sets = names.associateWith { FestivalRules.parse(it, RuleTestSupport.rule("festivals/$it.json")) }

    private fun def(id: String) = sets.values.flatMap { it.defs }.first { it.id == id }

    private fun tithi(id: String) = def(id).rule as FestivalRule.TithiAt

    private fun code(json: String): String =
        assertFailsWith<RuleFormatException> { FestivalRules.parse("t", json) }.code

    private fun path(json: String): String =
        assertFailsWith<RuleFormatException> { FestivalRules.parse("t", json) }.path

    private fun file(vararg defs: String, extra: String = "") =
        """{"version":1,"status":"VERIFY","sourceId":"s"$extra,"festivals":[${defs.joinToString(",")}]}"""

    private fun item(rule: String, id: String = "x", extra: String = "") =
        """{"id":"$id","name":"term:fest_$id","status":"VERIFY","sourceId":"s","regions":["all"],""" +
            """"rule":$rule$extra}"""

    private val tithiRule = """{"type":"tithi_at","tithi":1,"masa":"any","kala":"sunrise"}"""

    @Test
    fun shippedFilesParseWithExpectedCounts() {
        assertEquals(27, sets.getValue("pan_india").defs.size)
        assertEquals(10, sets.getValue("recurring").defs.size)
        assertEquals(37, sets.getValue("pan_india").defs.size + sets.getValue("recurring").defs.size)
        assertEquals(4, sets.getValue("tamil").defs.size)
        assertEquals(2, sets.getValue("malayalam").defs.size)
        assertEquals(1, sets.getValue("bengali").defs.size)
        assertEquals(1, sets.getValue("odia").defs.size)
        val all = sets.values.flatMap { it.defs }
        assertEquals(45, all.size)
        assertEquals(45, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.status == RuleStatus.VERIFY && it.sourceId == "src.traditional.unverified" })
        assertTrue(all.all { it.nameKey == "term:fest_" + it.id })
    }

    @Test
    fun shippedFilesHoldIdsOnly() {
        val text = names.joinToString("") { RuleTestSupport.rule("festivals/$it.json") }
        val strings = Regex("\"([^\"]*)\"").findAll(text).map { it.groupValues[1] }
        assertTrue(strings.all { Regex("[A-Za-z0-9_.:]+").matches(it) })
    }

    @Test
    fun spotChecksOfShippedRules() {
        val jan = tithi("krishna_janmashtami")
        assertEquals(listOf(23), jan.tithi)
        assertEquals(Masa.BHADRAPADA, jan.masa)
        assertEquals(MonthSystem.PURNIMANTA, jan.system)
        assertEquals(Kala.NISHITA, jan.kala)
        assertEquals(3, jan.preferNakshatra)
        assertEquals(Tie.FIRST, jan.tie)
        assertEquals(setOf("north", "west"), def("krishna_janmashtami").regions)
        assertEquals(Tie.MAX_COVERAGE, tithi("rama_navami").tie)
        assertNull(tithi("rama_navami").preferNakshatra)
        assertEquals(listOf(13, 28), tithi("pradosh_vrat").tithi)
        assertNull(tithi("purnima").masa)
        assertEquals(AdhikaPolicy.BOTH, def("purnima").adhika)
        assertEquals(AdhikaPolicy.NIJA, def("rama_navami").adhika)
        val vaishnava = FestivalRule.Ekadashi(Tradition.VAISHNAVA, null, MonthSystem.AMANTA, null)
        assertEquals(vaishnava, def("ekadashi_vaishnava").rule)
        assertEquals(FestivalRule.SankrantiRule(Rashi.MAKARA, SankrantiDay.BEFORE_SUNSET), def("makar_sankranti").rule)
        assertEquals(FestivalRule.SankrantiRule(null, SankrantiDay.CIVIL), def("sankranti").rule)
        val monday = def("somvati_amavasya").rule as FestivalRule.WeekdayTithi
        assertEquals(Vaar.MONDAY, monday.weekday)
        assertEquals(listOf(30), monday.inner.tithi)
        assertEquals(
            FestivalRule.NakshatraInSolarMonth(
                2, Rashi.VRISHCHIKA, SolarRegion.TAMIL, Kala.AHORATRA, Pick.NEAREST_FULL_MOON, null,
            ),
            def("karthigai_deepam").rule,
        )
        val onam = def("onam").rule as FestivalRule.NakshatraInSolarMonth
        assertEquals(Triple(Pick.FIRST, 12, Kala.SUNRISE), Triple(onam.pick, onam.precededBy, onam.kala))
        assertEquals(Pick.NEAREST_FULL_MOON, (def("thaipusam").rule as FestivalRule.NakshatraInSolarMonth).pick)
        assertEquals(FestivalRule.Relative("holika_dahan", 1), def("holi").rule)
        assertEquals(NoneRule.SUNRISE, tithi("karwa_chauth").none)
        assertEquals(NoneRule.SUNRISE, tithi("sankashti_chaturthi").none)
        val tuesday = def("angaraki_sankashti").rule as FestivalRule.WeekdayTithi
        assertEquals(NoneRule.SUNRISE, tuesday.inner.none)
        assertTrue(tithi("rama_navami").bhadra.isEmpty())
        assertEquals(
            listOf(
                BhadraStep.FreeIn(Kala.APARAHNA), BhadraStep.FreeIn(Kala.PRADOSH), BhadraStep.NextDay(6),
                BhadraStep.AfterBhadraBeforeMidnight,
            ),
            tithi("raksha_bandhan").bhadra,
        )
        assertEquals(
            listOf(BhadraStep.FreeIn(Kala.PRADOSH), BhadraStep.AfterBhadraBeforeMidnight, BhadraStep.NextDay(6)),
            tithi("holika_dahan").bhadra,
        )
        assertEquals(setOf("ta"), def("puthandu").regions)
        assertEquals(SankrantiDay.BEFORE_THREE_FIFTHS, (def("vishu").rule as FestivalRule.SankrantiRule).day)
        assertEquals(SankrantiDay.BEFORE_MIDNIGHT_NEXT, (def("pohela_boishakh").rule as FestivalRule.SankrantiRule).day)
        assertEquals(SankrantiDay.CIVIL, (def("pana_sankranti").rule as FestivalRule.SankrantiRule).day)
    }

    @Test
    fun forRegionSelectsAllAndMatching() {
        val pan = sets.getValue("pan_india")
        assertTrue(pan.forRegion("gu").any { it.id == "gujarati_new_year" })
        assertTrue(pan.forRegion("mr").none { it.id == "gujarati_new_year" || it.id == "karwa_chauth" })
        assertTrue(pan.forRegion("mr").any { it.id == "holi" })
    }

    @Test
    fun parsesAllRuleTypesAndDefaults() {
        val s = FestivalRules.parse(
            "t",
            file(
                item("""{"type":"fixed_gregorian","month":2,"day":29}""", "a"),
                item(tithiRule, "b"),
                item(
                    """{"type":"tithi_at","tithi":[1,2],"masa":"magha","kala":"nishita",""" +
                        """"tie":"second","none":"skip"}""",
                    "c",
                ),
                item("""{"type":"ekadashi","tradition":"smarta","masa":"magha","paksha":"krishna"}""", "d"),
            ),
        )
        assertEquals(FestivalRule.FixedGregorian(2, 29), s.defs[0].rule)
        val b = s.defs[1].rule as FestivalRule.TithiAt
        assertEquals(Triple(MonthSystem.AMANTA, Tie.FIRST, NoneRule.CONTAINING_START), Triple(b.system, b.tie, b.none))
        assertEquals(AdhikaPolicy.BOTH, s.defs[1].adhika)
        assertEquals(AdhikaPolicy.NIJA, s.defs[2].adhika)
        assertEquals(NoneRule.SKIP, (s.defs[2].rule as FestivalRule.TithiAt).none)
        assertEquals(Paksha.KRISHNA, (s.defs[3].rule as FestivalRule.Ekadashi).paksha)
    }

    @Test
    fun rejectsBadFileHeaders() {
        assertEquals("BAD_JSON", code("{"))
        assertEquals("TOO_LARGE", code(" ".repeat(RULE_LIMIT + 1)))
        assertEquals("OUT_OF_RANGE", code(file(item(tithiRule)).replace("\"version\":1", "\"version\":2")))
        assertEquals("UNKNOWN_KEY", code(file(item(tithiRule), extra = ""","extra":1""")))
        assertEquals("MISSING_FIELD", code("""{"version":1,"status":"VERIFY","festivals":[]}"""))
        assertEquals("BLANK", code(file(item(tithiRule)).replace("\"sourceId\":\"s\"", "\"sourceId\":\" \"")))
        assertEquals("BAD_VALUE", code(file(item(tithiRule)).replaceFirst("VERIFY", "DRAFT")))
        assertEquals("BAD_LENGTH", code(file()))
    }

    @Test
    fun rejectsBadDefinitions() {
        assertEquals("DUPLICATE", code(file(item(tithiRule, "a"), item(tithiRule, "a"))))
        assertEquals("$.festivals[1].id", path(file(item(tithiRule, "a"), item(tithiRule, "a"))))
        assertEquals("BAD_VALUE", code(file(item(tithiRule, "Bad-Id"))))
        assertEquals("BAD_VALUE", code(file(item(tithiRule, "x").replace("term:fest_x", "term:fest_y"))))
        assertEquals("BLANK", code(file(item(tithiRule).replace("\"sourceId\":\"s\"", "\"sourceId\":\"\""))))
        assertEquals("BAD_VALUE", code(file(item(tithiRule).replace("\"all\"", "\"mars\""))))
        assertEquals("BAD_LENGTH", code(file(item(tithiRule).replace("[\"all\"]", "[]"))))
        assertEquals("UNKNOWN_KEY", code(file(item(tithiRule, extra = ""","name_en":"x""""))))
        assertEquals("BAD_VALUE", code(file(item(tithiRule, extra = ""","adhika":"maybe""""))))
        assertEquals("BAD_VALUE", code(file(item(tithiRule, extra = ""","vratRules":["Bad Id"]"""))))
        val noRule = """{"id":"x","name":"term:fest_x","status":"VERIFY","sourceId":"s","regions":["all"]}"""
        assertEquals("MISSING_FIELD", code(file(noRule)))
    }

    @Test
    fun rejectsBadTithiRules() {
        fun r(body: String) = code(file(item("""{"type":"tithi_at",$body}""")))
        assertEquals("OUT_OF_RANGE", r(""""tithi":31,"masa":"any","kala":"sunrise""""))
        assertEquals("OUT_OF_RANGE", r(""""tithi":0,"masa":"any","kala":"sunrise""""))
        assertEquals("DUPLICATE", r(""""tithi":[3,3],"masa":"any","kala":"sunrise""""))
        assertEquals("BAD_LENGTH", r(""""tithi":[],"masa":"any","kala":"sunrise""""))
        assertEquals("BAD_VALUE", r(""""tithi":1,"masa":"Chaitra","kala":"sunrise""""))
        assertEquals("BAD_VALUE", r(""""tithi":1,"masa":"any","kala":"dawn""""))
        assertEquals("BAD_VALUE", r(""""tithi":1,"masa":"any","kala":"sunrise","system":"solar""""))
        assertEquals("BAD_VALUE", r(""""tithi":1,"masa":"any","kala":"sunrise","tie":"third""""))
        assertEquals("BAD_VALUE", r(""""tithi":1,"masa":"any","kala":"sunrise","none":"stop""""))
        assertEquals("MISSING_FIELD", r(""""tithi":1,"masa":"any""""))
        assertEquals("MISSING_FIELD", r(""""masa":"any","kala":"sunrise""""))
        assertEquals("UNKNOWN_KEY", r(""""tithi":1,"masa":"any","kala":"sunrise","weekday":"MONDAY""""))
        assertEquals("BAD_TYPE", r(""""tithi":1,"masa":"any","kala":"sunrise","tie":true"""))
    }

    @Test
    fun parsesAndRejectsStructuredTie() {
        fun r(tie: String) = file(item("""{"type":"tithi_at","tithi":1,"masa":"any","kala":"sunrise","tie":$tie}"""))
        val parsed = FestivalRules.parse("t", r("""{"prefer_nakshatra":3,"else":"second"}"""))
        val ok = parsed.defs[0].rule as FestivalRule.TithiAt
        assertEquals(Triple(Tie.SECOND, 3, Kala.SUNRISE), Triple(ok.tie, ok.preferNakshatra, ok.kala))
        assertEquals("OUT_OF_RANGE", code(r("""{"prefer_nakshatra":27,"else":"first"}""")))
        assertEquals("MISSING_FIELD", code(r("""{"prefer_nakshatra":3}""")))
        assertEquals("UNKNOWN_KEY", code(r("""{"prefer_nakshatra":3,"else":"first","x":1}""")))
    }

    @Test
    fun rejectsBadWeekdayAndEkadashiRules() {
        val wd = """"tithi":30,"masa":"any","kala":"sunrise""""
        fun w(day: String) = code(file(item("""{"type":"weekday_tithi_combo",$wd$day}""")))
        assertEquals("MISSING_FIELD", w(""))
        assertEquals("BAD_VALUE", w(""","weekday":"monday""""))
        fun e(body: String) = code(file(item("""{"type":"ekadashi",$body}""")))
        assertEquals("UNKNOWN_KEY", e(""""tradition":"smarta","masa":"any","paksha":"both","tithi":11"""))
        assertEquals("BAD_VALUE", e(""""tradition":"jain","masa":"any","paksha":"both""""))
        assertEquals("BAD_VALUE", e(""""tradition":"smarta","masa":"any","paksha":"third""""))
        assertEquals("MISSING_FIELD", e(""""tradition":"smarta","masa":"any""""))
    }

    @Test
    fun rejectsBadOtherRules() {
        fun r(body: String) = code(file(item(body)))
        assertEquals("BAD_VALUE", r("""{"type":"sankranti","into":"makara","day":"civil"}"""))
        assertEquals("BAD_VALUE", r("""{"type":"sankranti","into":"any","day":"noon"}"""))
        assertEquals("MISSING_FIELD", r("""{"type":"sankranti","into":"any"}"""))
        assertEquals("OUT_OF_RANGE", r("""{"type":"fixed_gregorian","month":13,"day":1}"""))
        assertEquals("OUT_OF_RANGE", r("""{"type":"fixed_gregorian","month":2,"day":30}"""))
        assertEquals("OUT_OF_RANGE", r("""{"type":"fixed_gregorian","month":4,"day":31}"""))
        val n = """"nakshatra":2,"month":"MESHA","region":"tamil","kala":"sunrise","occurrence":"first""""
        val nk = """{"type":"nakshatra_in_solar_month","""
        assertEquals(RuleStatus.VERIFY, FestivalRules.parse("t", file(item("$nk$n}"))).defs[0].status)
        assertEquals("OUT_OF_RANGE", r(nk + n.replace("\"nakshatra\":2", "\"nakshatra\":27") + "}"))
        assertEquals("BAD_VALUE", r(nk + n.replace("tamil", "kannada") + "}"))
        assertEquals("BAD_VALUE", r(nk + n.replace("first", "second") + "}"))
        assertEquals("BAD_VALUE", r("""{"type":"unknown"}"""))
        assertEquals("MISSING_FIELD", r("""{"tithi":1}"""))
        assertEquals("NOT_OBJECT", r("""[]"""))
    }

    @Test
    fun parsesAndRejectsAmendedElements() {
        val t = """"type":"tithi_at","tithi":15,"masa":"any","kala":"pradosh""""
        val ok = FestivalRules.parse(
            "t",
            file(
                item(
                    "{$t,\"none\":\"sunrise\",\"bhadra\":[{\"free_in\":\"pradosh\"}," +
                        "\"after_bhadra_before_midnight\",{\"next_day_min_ghatis\":60}]}",
                    "a",
                ),
                item("""{"type":"relative","to":"a","days":-3}""", "b"),
            ),
        )
        assertEquals(3, (ok.defs[0].rule as FestivalRule.TithiAt).bhadra.size)
        assertEquals(FestivalRule.Relative("a", -3), ok.defs[1].rule)
        fun b(steps: String) = code(file(item("{$t,\"bhadra\":$steps}")))
        assertEquals("BAD_LENGTH", b("[]"))
        assertEquals("NOT_ARRAY", b("{}"))
        assertEquals("BAD_VALUE", b("[\"after_sunset\"]"))
        assertEquals("UNKNOWN_KEY", b("""[{"free_in":"pradosh","x":1}]"""))
        assertEquals("BAD_LENGTH", b("[{}]"))
        assertEquals("BAD_VALUE", b("""[{"free_in":"ahoratra"}]"""))
        assertEquals("OUT_OF_RANGE", b("""[{"next_day_min_ghatis":0}]"""))
        assertEquals("OUT_OF_RANGE", b("""[{"next_day_min_ghatis":61}]"""))
        assertEquals("BAD_VALUE", code(file(item("""{"type":"tithi_at","tithi":1,"masa":"any","kala":"ahoratra"}"""))))
        assertEquals("BAD_VALUE", code(file(item("{$t,\"none\":\"same_day\"}"))))
    }

    @Test
    fun rejectsBadRelativeRules() {
        fun rel(body: String, vararg others: String) = code(file(item(tithiRule, "a"), *others, item(body, "z")))
        assertEquals("OUT_OF_RANGE", rel("""{"type":"relative","to":"a","days":0}"""))
        assertEquals("OUT_OF_RANGE", rel("""{"type":"relative","to":"a","days":4}"""))
        assertEquals("OUT_OF_RANGE", rel("""{"type":"relative","to":"a","days":-4}"""))
        assertEquals("BAD_VALUE", rel("""{"type":"relative","to":"nobody","days":1}"""))
        assertEquals("BAD_VALUE", rel("""{"type":"relative","to":"Bad Id","days":1}"""))
        assertEquals("MISSING_FIELD", rel("""{"type":"relative","to":"a"}"""))
        assertEquals("UNKNOWN_KEY", rel("""{"type":"relative","to":"a","days":1,"kala":"sunrise"}"""))
        val chain = item("""{"type":"relative","to":"a","days":1}""", "c")
        assertEquals("BAD_VALUE", rel("""{"type":"relative","to":"c","days":1}""", chain))
    }

    @Test
    fun rejectsBadNakshatraAmendments() {
        val n = """"type":"nakshatra_in_solar_month","nakshatra":2,"month":"MESHA","region":"tamil",""" +
            """"kala":"ahoratra","""
        fun r(rest: String) = code(file(item("{$n$rest}")))
        assertEquals("OUT_OF_RANGE", r(""""occurrence":"first","preceded_in_month_by":27"""))
        assertEquals("BAD_TYPE", r(""""occurrence":"first","preceded_in_month_by":"x""""))
        assertEquals("BAD_VALUE", r(""""occurrence":"nearest""""))
        val ok = FestivalRules.parse("t", file(item("{$n\"occurrence\":\"last\",\"preceded_in_month_by\":0}")))
        assertEquals(0, (ok.defs[0].rule as FestivalRule.NakshatraInSolarMonth).precededBy)
    }

    private companion object {
        const val RULE_LIMIT = 262_144
    }
}
