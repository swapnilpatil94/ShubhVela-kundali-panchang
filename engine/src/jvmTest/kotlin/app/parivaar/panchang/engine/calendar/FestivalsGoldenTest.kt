package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.o
import app.parivaar.panchang.engine.calendar.M04Golden.s
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Tag
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G-M04 festivals: both backends x 10 cities x 2024-2043 x the 45 shipped definitions against
 * `festivals_oracle.json` (docs/specs/M04.md section 4): dates, labels, tithis, kalas and flags equal unless the
 * row is fragile for the backend (then every engine date must be one of the row's alternative dates), window
 * endpoints within the sun-derived, limb or moonrise limits. The fixture's rule hashes must equal the current
 * rule files and the CHANGELOG must carry the fixture's dates hash.
 */
@Tag("golden")
class FestivalsGoldenTest {
    private val root = M04Golden.fixture("festivals_oracle.json")
    private val meta = M04Golden.meta(root)

    @Test
    fun fixtureShapeAndDefinitions() {
        assertEquals(M04Festivals.ROWS, meta.getValue("rows").i(), "meta.rows")
        val cities = M04Festivals.cities(root)
        assertEquals(M04Festivals.ROWS, cities.sumOf { it.rows.size }, "festival rows")
        assertEquals(M04Festivals.DEFS, M04Festivals.defs.size, "shipped definitions")
        assertEquals(
            M04Festivals.defs.map { it.id }.toSet(), meta.getValue("defs").a().map { it.s() }.toSet(),
            "shipped definitions vs fixture meta",
        )
        val ids = M04Festivals.defs.map { it.id }.toSet()
        assertTrue(cities.all { c -> c.rows.all { it[0].s() in ids } }, "fixture rows with unknown ids")
        assertEquals("2024-01-01..2043-12-31", meta.getValue("range").s())
    }

    @Test
    fun rulesHashesMatchTheCurrentRuleFiles() {
        val hashes = meta.getValue("rulesSha256").o()
        assertEquals(M04Festivals.FILES.map { "$it.json" }.toSet(), hashes.keys, "hashed rule files")
        for (name in M04Festivals.FILES) {
            val text = RuleTestSupport.rule("festivals/$name.json")
            val sha = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
            assertEquals(hashes.getValue("$name.json").s(), sha, "rules/festivals/$name.json changed after the fixture")
        }
    }

    @Test
    fun changelogCarriesTheDatesHash() {
        val repo = File(checkNotNull(System.getProperty("pp.rulesDir")) { "pp.rulesDir is not set" }).parentFile
        val changelog = File(repo, "engine-golden/CHANGELOG.md").readText()
        val prefix = meta.getValue("datesSha256").s().take(DATES_HASH_HEX)
        assertTrue("festivals_oracle.json datesSha256:$prefix" in changelog, "CHANGELOG lacks the dates hash $prefix")
    }

    @Test
    fun precision() = run(M04Golden.PRECISION)

    @Test
    fun base() = run(M04Golden.BASE)

    private fun run(lim: M04Golden.Limits) {
        val cities = M04Festivals.cities(root)
        val defs = M04Festivals.defs
        val types = defs.associate { it.id to M04Festivals.typeOf(it) }
        val first = LocalDate(2024, 1, 1)
        val last = LocalDate(2043, 12, 31)
        val tally = M04Festivals.Tally()
        M04Golden.parallel(cities) { c ->
            val engine = FestivalEngine(lim.eph())
            val occ = engine.occurrences(defs, first, last, c.place) { c.offsetMin * SECONDS_PER_MINUTE }
            M04Festivals.compare(lim, c.name, c.rows, occ, { types.getValue(it) }, tally)
        }
        val lines = tally.types.toSortedMap().map { (t, v) ->
            "$t: rows ${v.rows}, fragile ${v.fragile} (engine absent ${v.absentFragile}), mismatches ${v.failures}"
        } + listOf(
            "sun-derived endpoints max ${tally.sun.fmt()} s (limit ${lim.sunS})",
            "limb endpoints max ${tally.limb.fmt()} s (limit ${M04Festivals.limbLimit(lim)})",
            "sankranti instants max ${tally.sankranti.fmt()} s (limit ${M04Festivals.sankrantiLimit(lim)})",
            "flags seen on compared rows: " +
                tally.flagRows.toSortedMap().entries.joinToString { "${it.key} ${it.value}" },
        )
        M04Golden.report("festivals ${lim.name}", lines)
        M04Golden.assertNoFailures("festivals ${lim.name}", tally.failures.list())
        assertEquals(M04Festivals.ROWS, tally.types.values.sumOf { it.rows }, "festival rows compared")
    }

    private companion object {
        const val DATES_HASH_HEX = 16
        const val SECONDS_PER_MINUTE = 60
    }
}
