package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.jyotish.NakshatraTable
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every ordered pair of the 110 inputs (koota_pairs.csv, 12,100 rows) and every unordered pair
 * (koota_symmetric.csv, 6,105 rows) from the independent oracle tools/oracle/gen_koota.py. Exact integer
 * equality of the eight Q values, the total and the note ids; zero tolerance.
 */
@Tag("golden")
class KootaGoldenTest {
    private val calc = AshtakootaCalculator(
        KootaTables.parse(RuleTestSupport.rule("koota.json")),
        NakshatraTable.parse(RuleTestSupport.rule("nakshatra.json")),
    )

    private fun rows(name: String): List<List<String>> {
        val lines = GoldenLoader.resourceBytes("/golden/$name").decodeToString().split('\n').filter { it.isNotEmpty() }
        assertTrue(lines.first().startsWith("gn,") || lines.first().startsWith("an,"))
        return lines.drop(1).map { it.split(',') }
    }

    private fun point(c: List<String>, at: Int) =
        MoonPoint(c[at].toInt(), c[at + 1].toInt(), VashyaClass.entries[c[at + 2].toInt()])

    private fun check(name: String, expectRows: Int, f: (MoonPoint, MoonPoint) -> AshtakootaResult) {
        val rows = rows(name)
        assertEquals(expectRows, rows.size, name)
        val failures = mutableListOf<String>()
        for ((i, c) in rows.withIndex()) {
            assertEquals(16, c.size, "$name row $i columns")
            val r = f(point(c, 0), point(c, 3))
            val got = r.scores.map { it.q.toString() } + r.totalQ.toString() +
                r.scores.flatMap { it.noteIds }.joinToString(";")
            if (got != c.subList(6, 16)) failures += "$name row $i want=${c.subList(6, 16)} got=$got"
        }
        assertTrue(failures.isEmpty(), "${failures.size} mismatches, first: ${failures.take(5)}")
    }

    @Test
    fun `match equals the oracle on all 12100 ordered pairs`() = check("koota_pairs.csv", 12_100, calc::match)

    @Test
    fun `matchSymmetric equals the oracle on all 6105 unordered pairs`() =
        check("koota_symmetric.csv", 6_105, calc::matchSymmetric)
}
