package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.jyotish.M02Golden.a
import app.parivaar.panchang.engine.jyotish.M02Golden.d
import app.parivaar.panchang.engine.jyotish.M02Golden.i
import app.parivaar.panchang.engine.jyotish.M02Golden.o
import app.parivaar.panchang.engine.jyotish.M02Golden.s
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

/** 4,160 longitudes x 16 PARASHARA vargas plus D3 JAGANNATH: rashi and part equal (spec section 4). */
@Tag("golden")
class VargasGoldenTest {
    @Test
    fun vargasMatchOracle() {
        val root = M02Golden.fixture("vargas.json")
        val rows = M02Golden.rows(root, "v", 4160, "count")
        val ids = root.getValue("meta").o().getValue("vargas").a().map { it.s() }
        assertEquals(17, ids.size)
        assertEquals(Varga.entries.map { it.name } + "D3J", ids)
        val vargas = Vargas.parse(RuleTestSupport.rule("vargas.json"))
        val jagannath = CalcSettings(drekkana = DrekkanaVariant.JAGANNATH)
        val failures = mutableListOf<String>()
        var compared = 0
        for (row in rows) {
            val r = row.a()
            val lon = r[0].d()
            val cols = r[1].a()
            assertEquals(ids.size, cols.size, "columns per row")
            for ((k, id) in ids.withIndex()) {
                val got = if (id == "D3J") {
                    vargas.position(Varga.D3, lon, jagannath)
                } else {
                    vargas.position(Varga.valueOf(id), lon)
                }
                val want = cols[k].a()
                compared++
                if (got.rashi.ordinal != want[0].i() || got.part != want[1].i()) {
                    failures += "$id lon=$lon want=${want[0].i()}/${want[1].i()} got=${got.rashi.ordinal}/${got.part}"
                }
            }
        }
        assertEquals(4160 * 17, compared)
        M02Golden.report(
            "Vargas (4160 longitudes x 17)",
            listOf("compared=$compared mismatches=${failures.size} (must be 0)"),
        )
        M02Golden.assertNoFailures("vargas", failures)
    }
}
