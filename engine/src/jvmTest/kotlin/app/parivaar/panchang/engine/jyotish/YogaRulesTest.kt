package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.astro.GoldenLoader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** docs/specs/M02.md section 4, `YogaRulesTest`. Fixtures are synthetic charts. */
class YogaRulesTest {
    private val text = RuleTestSupport.rule("yogas.json")
    private val rules = YogaRules.parse(text)

    private class Fixture(val ruleId: String, val expect: Boolean, val asc: Double, val lons: DoubleArray) {
        /** asc and the nine longitudes as one list of ten placements. */
        val placements: List<Double> get() = listOf(asc) + lons.toList()
    }

    private val fixtures: List<Fixture> = (GoldenLoader.json("/golden/yoga_fixtures.json")["fixtures"] as JsonArray)
        .map { e ->
            val o = e.jsonObject
            val l = o.getValue("lons").jsonObject
            Fixture(
                o.getValue("ruleId").jsonPrimitive.content,
                o.getValue("expect").jsonPrimitive.boolean,
                o.getValue("ascDeg").jsonPrimitive.double,
                DoubleArray(9) { l.getValue(Graha.entries[it].name).jsonPrimitive.double },
            )
        }

    private fun find(id: String, expect: Boolean, asc: Double, vararg lon: Pair<Graha, Double>): Boolean =
        fixtures.any { f ->
            f.ruleId == id && f.expect == expect && f.asc == asc && lon.all { (g, v) -> f.lons[g.ordinal] == v }
        }

    @Test
    fun `15 rules, all VERIFY with ids, in file order`() {
        assertEquals(15, rules.ids.size)
        assertEquals(rules.ids.toSet().size, rules.ids.size)
        val findings = rules.evaluate(RuleTestSupport.chart(5.0, DoubleArray(9) { 40.0 * it }))
        assertEquals(rules.ids, findings.map { it.ruleId })
        findings.forEach {
            assertEquals(RuleStatus.VERIFY, it.status)
            assertEquals("yoga." + it.ruleId.lowercase(), it.contentId)
            assertTrue(it.sourceId.isNotBlank() && it.variant.isNotBlank())
            assertTrue(it.category == "YOGA" || it.category == "DOSHA")
        }
        assertEquals("BASE_NO_CANCELLATION", findings.first { it.ruleId == "MANGLIK_MOON" }.variant)
    }

    @Test
    fun `every rule has two positive and two negative fixtures and no fixture names an unknown rule`() {
        assertTrue(fixtures.all { it.ruleId in rules.ids })
        for (id in rules.ids) {
            val mine = fixtures.filter { it.ruleId == id }
            assertTrue(mine.count { it.expect } >= 2, "$id positives")
            assertTrue(mine.count { !it.expect } >= 2, "$id negatives")
        }
    }

    @Test
    fun `each negative differs from a positive in one placement`() {
        for (neg in fixtures.filter { !it.expect }) {
            val ok = fixtures.any { pos ->
                pos.ruleId == neg.ruleId && pos.expect &&
                    pos.placements.zip(neg.placements).count { (a, b) -> a != b } == 1
            }
            assertTrue(ok, "${neg.ruleId} negative has no one-placement positive")
        }
    }

    @Test
    fun `all fixtures evaluate as expected`() {
        for ((i, f) in fixtures.withIndex()) {
            val finding = rules.evaluate(RuleTestSupport.chart(f.asc, f.lons)).first { it.ruleId == f.ruleId }
            assertEquals(f.expect, finding.present, "fixture $i ${f.ruleId}")
        }
    }

    @Test
    fun `spec anchors are included`() {
        assertTrue(find("GAJAKESARI", true, 5.0, Graha.MOON to 10.0, Graha.JUPITER to 100.0))
        assertTrue(find("GAJAKESARI", false, 5.0, Graha.MOON to 10.0, Graha.JUPITER to 130.0))
        assertTrue(find("MANGLIK_LAGNA", true, 5.0, Graha.MARS to 215.0))
        assertTrue(find("MANGLIK_LAGNA", false, 5.0, Graha.MARS to 65.0))
        assertTrue(find("KAAL_SARP", true, 5.0, Graha.RAHU to 10.0, Graha.KETU to 190.0))
        assertTrue(find("KAAL_SARP", false, 5.0, Graha.RAHU to 10.0, Graha.KETU to 190.0, Graha.SATURN to 200.0))
    }

    private fun one(body: String, whenJson: String = "{\"conjunct\":[\"SUN\",\"MOON\"]}", extra: String = "") =
        "{\"version\":1,\"rules\":[{\"id\":\"T\",\"category\":\"YOGA\",\"status\":\"VERIFY\",\"variant\":\"B\"," +
            "\"sourceId\":\"s\",\"contentId\":\"c\",$extra\"when\":$whenJson}$body]}"

    private fun rejects(json: String, code: String? = null) {
        val e = assertFailsWith<RuleFormatException> { YogaRules.parse(json) }
        assertEquals("${e.code} ${e.path}", e.message)
        if (code != null) assertEquals(code, e.code)
    }

    private fun whenOf(cond: String) = one("", cond)

    @Test
    fun `parse accepts a minimal rule`() {
        assertEquals(listOf("T"), YogaRules.parse(one("")).ids)
    }

    @Test
    fun `parse rejects unknown key, kind and graha`() {
        rejects(whenOf("{\"flies\":[\"SUN\"]}"), "UNKNOWN_KEY")
        rejects(whenOf("{\"conjunct\":[\"SUN\",\"PLUTO\"]}"), "BAD_VALUE")
        rejects(whenOf("{\"in_house\":{\"graha\":\"SUN\",\"from\":\"LAGNA\",\"houses\":[1],\"x\":1}}"), "UNKNOWN_KEY")
        rejects(whenOf("{\"conjunct\":[\"SUN\",\"MOON\"],\"not\":{\"exalted\":\"SUN\"}}"), "BAD_COUNT")
        rejects(one("", extra = "\"note\":\"free text\","), "UNKNOWN_KEY")
        rejects(one("", extra = "\"description\":\"x\","), "UNKNOWN_KEY")
    }

    @Test
    fun `parse rejects houses outside 1 to 12 and bad lord_of`() {
        rejects(whenOf("{\"in_house\":{\"graha\":\"SUN\",\"from\":\"LAGNA\",\"houses\":[0]}}"), "OUT_OF_RANGE")
        rejects(whenOf("{\"in_house\":{\"graha\":\"SUN\",\"from\":\"LAGNA\",\"houses\":[13]}}"), "OUT_OF_RANGE")
        rejects(whenOf("{\"conjunct\":[{\"lord_of\":13},\"MOON\"]}"), "OUT_OF_RANGE")
    }

    @Test
    fun `parse rejects empty all and any`() {
        rejects(whenOf("{\"all\":[]}"), "EMPTY")
        rejects(whenOf("{\"any\":[]}"), "EMPTY")
    }

    @Test
    fun `parse rejects missing or blank source and content ids`() {
        val base = one("")
        rejects(base.replace("\"sourceId\":\"s\",", ""), "MISSING_FIELD")
        rejects(base.replace("\"contentId\":\"c\",", ""), "MISSING_FIELD")
        rejects(base.replace("\"sourceId\":\"s\"", "\"sourceId\":\"  \""), "BLANK")
        rejects(base.replace("\"contentId\":\"c\"", "\"contentId\":\"\""), "BLANK")
    }

    @Test
    fun `parse rejects duplicate ids, bad category and status, bad json`() {
        val first = one("")
        val rule = first.substringAfter("\"rules\":[").removeSuffix("]}")
        rejects("{\"version\":1,\"rules\":[$rule,$rule]}", "DUPLICATE")
        rejects(first.replace("YOGA", "BLESSING"), "BAD_VALUE")
        rejects(first.replace("VERIFY", "SURE"), "BAD_VALUE")
        rejects("{", "BAD_JSON")
        rejects(first.replace("\"version\":1", "\"version\":2"), "OUT_OF_RANGE")
    }

    private fun nested(levels: Int): String =
        "{\"not\":".repeat(levels) + "{\"exalted\":\"SUN\"}" + "}".repeat(levels)

    @Test
    fun `parse limits nesting to 16 and input to 262144 characters`() {
        YogaRules.parse(whenOf(nested(15))) // depth 16
        rejects(whenOf(nested(16)), "TOO_DEEP") // depth 17
        rejects(" ".repeat(262_145) + one(""), "TOO_LARGE")
    }

    @Test
    fun `dignity, aspect, exchange and arc predicates`() {
        fun eval(cond: String, asc: Double, vararg lon: Pair<Graha, Double>): Boolean {
            val lons = DoubleArray(9) { 10.0 + 35.0 * it }
            lon.forEach { (g, v) -> lons[g.ordinal] = v }
            return YogaRules.parse(whenOf(cond)).evaluate(RuleTestSupport.chart(asc, lons)).single().present
        }
        assertTrue(eval("{\"debilitated\":\"MARS\"}", 5.0, Graha.MARS to 100.0))
        assertTrue(!eval("{\"debilitated\":\"RAHU\"}", 5.0, Graha.RAHU to 100.0))
        assertTrue(!eval("{\"own_sign\":\"KETU\"}", 5.0, Graha.KETU to 10.0))
        val jupiterOnSun = "{\"aspect\":{\"by\":\"JUPITER\",\"on\":\"SUN\"}}"
        assertTrue(eval(jupiterOnSun, 5.0, Graha.JUPITER to 10.0, Graha.SUN to 130.0))
        assertTrue(!eval("{\"aspect\":{\"by\":\"RAHU\",\"on\":\"SUN\"}}", 5.0, Graha.RAHU to 10.0, Graha.SUN to 190.0))
        assertTrue(eval("{\"exchange\":[\"SUN\",\"MOON\"]}", 5.0, Graha.SUN to 100.0, Graha.MOON to 130.0))
        assertTrue(!eval("{\"conjunct\":[\"SUN\",\"SUN\"]}", 5.0))
        assertTrue(!eval("{\"all_in_arc\":{\"grahas\":[\"MARS\"],\"from\":\"RAHU\",\"to\":\"KETU\"}}", 5.0,
            Graha.RAHU to 10.0, Graha.KETU to 190.0, Graha.MARS to 10.0))
        assertTrue(eval("{\"not\":{\"in_trikona\":{\"graha\":\"MARS\",\"from\":\"LAGNA\"}}}", 5.0, Graha.MARS to 100.0))
    }
}
