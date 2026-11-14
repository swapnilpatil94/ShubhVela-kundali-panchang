package app.parivaar.panchang.engine.jyotish

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** docs/specs/M02.md section 4, `NakshatraTableTest`. */
class NakshatraTableTest {
    private val text = RuleTestSupport.rule("nakshatra.json")
    private val table = NakshatraTable.parse(text)

    private fun moonChart(moon: Double): ChartFacts {
        val lons = DoubleArray(9) { 10.0 * it }
        lons[Graha.MOON.ordinal] = moon
        return RuleTestSupport.chart(5.0, lons)
    }

    private fun rows(): List<String> = text.lines().filter { it.startsWith("{\"id\"") }

    private fun field(row: String, key: String) = Regex("\"$key\":\"([A-Z_a-z.]+)\"").find(row)!!.groupValues[1]

    @Test
    fun `27 nakshatras and 12 rashis, ids in order`() {
        assertEquals(39, rows().size)
        assertEquals("ASHWINI", table.id(0))
        assertEquals("REVATI", table.id(26))
        assertFailsWith<IllegalArgumentException> { table.id(27) }
    }

    @Test
    fun `gana and nadi counts are 9 9 9`() {
        val naks = rows().take(27)
        for ((key, values) in mapOf(
            "gana" to listOf("DEVA", "MANUSHYA", "RAKSHASA"),
            "nadi" to listOf("ADI", "MADHYA", "ANTYA"),
        )) {
            assertEquals(listOf(9, 9, 9), values.map { v -> naks.count { field(it, key) == v } }, key)
        }
    }

    @Test
    fun `14 yonis, each twice except mongoose once`() {
        val counts = rows().take(27).groupingBy { field(it, "yoni") }.eachCount()
        assertEquals(14, counts.size)
        counts.forEach { (yoni, c) -> assertEquals(if (yoni == "MONGOOSE") 1 else 2, c, yoni) }
    }

    @Test
    fun `nadi pattern is A M N N M A repeating`() {
        val pattern = listOf("ADI", "MADHYA", "ANTYA", "ANTYA", "MADHYA", "ADI")
        rows().take(27).forEachIndexed { i, r -> assertEquals(pattern[i % 6], field(r, "nadi"), "row $i") }
    }

    @Test
    fun `avakhada reads sign, nakshatra, pada and the degree for the two-entry vashya`() {
        val a = table.avakhada(moonChart(254.9)) // Dhanu 14.9
        assertEquals(Rashi.DHANU, a.moonRashi)
        assertEquals("MANAVA", a.vashya)
        assertEquals("CHATUSHPADA", table.avakhada(moonChart(255.0)).vashya)
        assertEquals(Graha.JUPITER, a.rashiLord)
        assertEquals("FIRE", a.tatva)
        assertEquals("KSHATRIYA", a.varna)
        val b = table.avakhada(moonChart(0.0))
        assertEquals(0, b.nakshatra)
        assertEquals(1, b.pada)
        assertEquals("HORSE", b.yoni)
        assertEquals("DEVA", b.gana)
        assertEquals(Graha.KETU, b.nakshatraLord)
        assertEquals("CHATUSHPADA", b.vashya)
        assertEquals("CHATUSHPADA", table.avakhada(moonChart(275.0)).vashya)
        assertEquals("JALACHARA", table.avakhada(moonChart(285.0)).vashya)
    }

    @Test
    fun `malformed files throw RuleFormatException without values in the message`() {
        val bad = listOf(
            "not json",
            "[]",
            text.replace("\"version\":1", "\"version\":2"),
            text.replace("\"gana\":\"DEVA\"", "\"gana\":\"D\""),
            text.replace("\"nadi\":\"ADI\"", "\"nadi\":\"X\""),
            text.replace("\"deity\"", "\"deityy\""),
            text.replaceFirst("\"id\":\"BHARANI\"", "\"id\":\"ASHWINI\""),
            text.replaceFirst("\"id\":\"MESHA\"", "\"id\":\"VRISHABHA\""),
            text.replaceFirst("\"vashya\":[\"CHATUSHPADA\"]", "\"vashya\":[]"),
            text.replaceFirst("\"tatva\":\"FIRE\"", "\"tatva\":\"WOOD\""),
            "x".repeat(262_145),
        )
        bad.forEach { b ->
            val e = assertFailsWith<RuleFormatException> { NakshatraTable.parse(b) }
            assertTrue(e.message == "${e.code} ${e.path}")
        }
    }
}
