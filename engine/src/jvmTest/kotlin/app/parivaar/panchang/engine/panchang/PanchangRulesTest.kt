package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.jyotish.RuleFormatException
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/specs/M03.md section 4, `PanchangRulesTest`. */
class PanchangRulesTest {
    private val dur = RuleTestSupport.rule("durmuhurta.json")
    private val chog = RuleTestSupport.rule("choghadiya.json")
    private val tables = PanchangRules.parse(dur, chog)

    private fun code(block: () -> Unit): String = assertFailsWith<RuleFormatException> { block() }.code

    private val goodRow = """["UDVEG","CHAR","LABH","AMRIT","KAAL","SHUBH","ROG","UDVEG"]"""

    private fun weekdayObject(row: String): String =
        Vaar.entries.joinToString(",", "{", "}") { "\"${it.name}\":$row" }

    private fun gowri(): String =
        """{"version":1,"status":"VERIFY","sourceId":"s","day":${weekdayObject(goodRow)},""" +
            """"night":${weekdayObject(goodRow)}}"""

    private fun varjyam(order: List<String> = NAKSHATRAS): String =
        order.joinToString(",", """{"version":1,"status":"VERIFY","sourceId":"s","rows":[""", "]}") {
            """{"nakshatra":"$it","varjyam":[10.5],"amrit":[20,30]}"""
        }

    @Test
    fun shippedFilesParseAndCarryStatusVerify() {
        assertTrue(dur.contains("\"status\":\"VERIFY\""))
        assertTrue(chog.contains("\"status\":\"VERIFY\""))
        assertEquals(7, tables.durmuhurta.size)
        assertTrue(tables.choghadiya.day.isEmpty())
        assertNull(tables.gowri)
        assertNull(tables.varjyam)
    }

    @Test
    fun shippedFilesHoldIdsOnly() {
        val ids = Regex("\"([^\"]*)\"").findAll(dur + chog).map { it.groupValues[1] }
        assertTrue(ids.all { Regex("[A-Za-z_.]+").matches(it) })
    }

    @Test
    fun nightChoghadiyaFollowsTheSpecRuleForEveryWeekday() {
        val cycle = Choghadiya.entries
        for (v in Vaar.entries) {
            val lord = Vaar.entries[(v.ordinal + 4) % 7].lord
            val n0 = cycle.indexOfFirst { it.lord == lord }
            val expected = List(8) { cycle[(n0 - 2 * it).mod(7)].name }
            assertEquals(expected, tables.choghadiya.night.getValue(v))
        }
    }

    @Test
    fun nightChoghadiyaAnchorsMatch() {
        assertEquals(
            listOf("SHUBH", "AMRIT", "CHAR", "ROG", "KAAL", "LABH", "UDVEG", "SHUBH"),
            tables.choghadiya.night.getValue(Vaar.SUNDAY),
        )
        assertEquals(
            listOf("KAAL", "LABH", "UDVEG", "SHUBH", "AMRIT", "CHAR", "ROG", "KAAL"),
            tables.choghadiya.night.getValue(Vaar.TUESDAY),
        )
    }

    @Test
    fun durmuhurtaIsTheSpecList() {
        val d = { m: Int -> DurmuhurtaSlot(false, m) }
        val expected = mapOf(
            Vaar.SUNDAY to listOf(d(14)),
            Vaar.MONDAY to listOf(d(9), d(12)),
            Vaar.TUESDAY to listOf(d(4), DurmuhurtaSlot(true, 7)),
            Vaar.WEDNESDAY to listOf(d(8)),
            Vaar.THURSDAY to listOf(d(6), d(12)),
            Vaar.FRIDAY to listOf(d(4), d(9)),
            Vaar.SATURDAY to listOf(d(1), d(2)),
        )
        assertEquals(expected, tables.durmuhurta)
    }

    @Test
    fun rejectsUnknownKeys() {
        val extra = dur.replace("\"weekdays\"", "\"extra\":1,\"weekdays\"")
        assertEquals("UNKNOWN_KEY", code { PanchangRules.parse(extra, chog) })
        val withDay = chog.replace("\"night\":{", "\"day\":{},\"night\":{")
        assertEquals("UNKNOWN_KEY", code { PanchangRules.parse(dur, withDay) })
    }

    @Test
    fun rejectsAMissingWeekday() {
        assertEquals("UNKNOWN_KEY", code { PanchangRules.parse(dur.replace("\"FRIDAY\"", "\"FRIDAY_\""), chog) })
        val noSaturday = chog.replace(Regex(",\n\"SATURDAY\":\\[[^\\]]*\\]"), "")
        assertEquals("MISSING_FIELD", code { PanchangRules.parse(dur, noSaturday) })
    }

    @Test
    fun rejectsWrongLengths() {
        val shortRow = chog.replaceFirst(",\"SHUBH\"]", "]")
        assertEquals("BAD_LENGTH", code { PanchangRules.parse(dur, shortRow) })
        val noSlots = dur.replace("""[{"night":false,"muhurta":14}]""", "[]")
        assertEquals("BAD_LENGTH", code { PanchangRules.parse(noSlots, chog) })
    }

    @Test
    fun rejectsMuhurtaOutsideOneToFifteen() {
        for (bad in listOf("16", "0")) {
            val text = dur.replace("\"muhurta\":14", "\"muhurta\":$bad")
            assertEquals("OUT_OF_RANGE", code { PanchangRules.parse(text, chog) })
        }
    }

    @Test
    fun rejectsAnUnknownChoghadiyaId() {
        val unknown = chog.replaceFirst("\"AMRIT\"", "\"NECTAR\"")
        assertEquals("BAD_VALUE", code { PanchangRules.parse(dur, unknown) })
    }

    @Test
    fun rejectsVarjyamRowsOutOfOrder() {
        val swapped = NAKSHATRAS.toMutableList().apply { this[0] = NAKSHATRAS[1]; this[1] = NAKSHATRAS[0] }
        assertEquals("OUT_OF_ORDER", code { PanchangRules.parse(dur, chog, varjyam = varjyam(swapped)) })
    }

    @Test
    fun rejectsABlankSourceId() {
        assertEquals("BLANK", code { PanchangRules.parse(dur.replace("src.traditional.unverified", " "), chog) })
    }

    @Test
    fun rejectsInputOverTheSizeLimit() {
        assertEquals("TOO_LARGE", code { PanchangRules.parse(dur + " ".repeat(262_144), chog) })
    }

    @Test
    fun rejectsABadStatusAndBadJson() {
        assertEquals("BAD_VALUE", code { PanchangRules.parse(dur.replace("VERIFY", "MAYBE"), chog) })
        assertEquals("BAD_JSON", code { PanchangRules.parse("{", chog) })
    }

    @Test
    fun syntheticGowriAndVarjyamFilesParse() {
        val t = PanchangRules.parse(dur, chog, gowri = gowri(), varjyam = varjyam())
        val g = assertNotNull(t.gowri)
        assertEquals(8, g.day.getValue(Vaar.SUNDAY).size)
        val v = assertNotNull(t.varjyam)
        assertEquals(27, v.varjyamGhati.size)
        assertEquals(listOf(20.0, 30.0), v.amritGhati[5])
    }

    @Test
    fun rejectsGhatiOutsideZeroToFiftySix() {
        val high = varjyam().replace("10.5", "57")
        assertEquals("OUT_OF_RANGE", code { PanchangRules.parse(dur, chog, varjyam = high) })
    }

    private companion object {
        val NAKSHATRAS = listOf(
            "ASHWINI", "BHARANI", "KRITTIKA", "ROHINI", "MRIGASHIRA", "ARDRA", "PUNARVASU", "PUSHYA", "ASHLESHA",
            "MAGHA", "PURVA_PHALGUNI", "UTTARA_PHALGUNI", "HASTA", "CHITRA", "SWATI", "VISHAKHA", "ANURADHA",
            "JYESHTHA", "MULA", "PURVA_ASHADHA", "UTTARA_ASHADHA", "SHRAVANA", "DHANISHTA", "SHATABHISHA",
            "PURVA_BHADRAPADA", "UTTARA_BHADRAPADA", "REVATI",
        )
    }
}
