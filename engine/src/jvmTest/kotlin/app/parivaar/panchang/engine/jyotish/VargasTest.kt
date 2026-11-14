package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import kotlin.math.floor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** docs/specs/M02.md section 4, `VargasTest`. */
class VargasTest {
    private val text = RuleTestSupport.rule("vargas.json")
    private val vargas = Vargas.parse(text)
    private val lons = Random(20261114).let { r -> List(10_000) { r.nextDouble(0.0, 360.0) } }

    @Test
    fun `D9 rashi is floor(lon times 9 over 30) mod 12`() {
        for (lon in lons) {
            val p = vargas.position(Varga.D9, lon)
            assertEquals(floor(lon * 9 / 30.0).toInt() % 12, p.rashi.ordinal, "lon index")
        }
    }

    @Test
    fun `D60 is s plus floor(2 d) mod 12`() {
        for (lon in lons) {
            val s = floor(lon / 30.0).toInt()
            val d = lon - 30.0 * s
            val p = vargas.position(Varga.D60, lon)
            assertEquals((s + floor(2 * d).toInt()) % 12, p.rashi.ordinal)
        }
    }

    @Test
    fun `D3 stays in the trine and D1 is the sign`() {
        for (lon in lons) {
            val s = Zodiac.rashiOf(lon).ordinal
            val p = vargas.position(Varga.D3, lon)
            assertEquals(0, (p.rashi.ordinal - s).mod(4), "trine")
            assertEquals(Zodiac.rashiOf(lon), vargas.position(Varga.D1, lon).rashi)
            val j = vargas.position(Varga.D3, lon, CalcSettings(drekkana = DrekkanaVariant.JAGANNATH))
            assertEquals(0, (j.rashi.ordinal - s).mod(4), "trine, jagannath")
        }
    }

    @Test
    fun `hand-worked anchors`() {
        // Mesha 0 to 3.33: D9 part 0 starts at Mesha; Vrishabha (fixed) starts 9th from it.
        assertEquals(VargaPos(Rashi.MESHA, 0), vargas.position(Varga.D9, 1.0))
        assertEquals(VargaPos(Rashi.MAKARA, 0), vargas.position(Varga.D9, 31.0))
        assertEquals(VargaPos(Rashi.SIMHA, 0), vargas.position(Varga.D2, 10.0)) // odd sign, first half
        assertEquals(VargaPos(Rashi.KARKA, 1), vargas.position(Varga.D2, 20.0))
        assertEquals(VargaPos(Rashi.MESHA, 0), vargas.position(Varga.D30, 3.0))
        assertEquals(VargaPos(Rashi.TULA, 4), vargas.position(Varga.D30, 29.0))
        assertEquals(VargaPos(Rashi.VRISHABHA, 0), vargas.position(Varga.D30, 33.0))
        assertEquals(Varga.entries.size, 16)
    }

    @Test
    fun `D2 KASHINATH throws`() {
        val e = assertFailsWith<VargaUnavailableException> {
            vargas.position(Varga.D2, 10.0, CalcSettings(hora = HoraVariant.KASHINATH))
        }
        assertEquals(Varga.D2, e.varga)
    }

    @Test
    fun `chart maps lagna and nine grahas`() {
        val lons9 = DoubleArray(9) { 33.0 * it }
        val facts = RuleTestSupport.chart(100.0, lons9)
        val c = vargas.chart(Varga.D9, facts)
        assertEquals(9, c.grahas.size)
        assertEquals(vargas.position(Varga.D9, 100.0), c.lagna)
        assertEquals(vargas.position(Varga.D9, 33.0 * 4), c.grahas[4])
        assertTrue(c.toString().none { it.isDigit() })
    }

    @Test
    fun `malformed files throw RuleFormatException`() {
        val bad = listOf(
            "{",
            text.replace("\"version\":1", "\"version\":3"),
            text.replaceFirst("\"kind\":\"CYCLIC\"", "\"kind\":\"SPIRAL\""),
            text.replaceFirst("\"step\":1", "\"step\":1,\"extra\":1"),
            text.replaceFirst("\"id\":\"D1\"", "\"id\":\"D99\""),
            text.replaceFirst("{\"rel\":0},{\"rel\":8},{\"rel\":4}", "{\"rel\":0},{\"rel\":8}"),
            text.replaceFirst("{\"rel\":8}", "{\"rel\":12}"),
            text.replaceFirst("{\"rel\":8}", "{\"rel\":-1}"),
            text.replaceFirst("[15,4],[30,3]", "[15,4],[29,3]"),
            text.replaceFirst("[15,4],[30,3]", "[20,4],[15,3],[30,3]"),
            text.replaceFirst("[15,4],[30,3]", "[15,13],[30,3]"),
            text.replaceFirst("\"id\":\"D4\"", "\"id\":\"D1\""), // duplicate (D1, PARASHARA)
            text.replaceFirst("\"by\":\"NONE\"", "\"by\":\"SEASON\""),
        )
        bad.forEachIndexed { i, b ->
            val e = assertFailsWith<RuleFormatException>("case $i") { Vargas.parse(b) }
            assertEquals("${e.code} ${e.path}", e.message)
        }
    }
}
