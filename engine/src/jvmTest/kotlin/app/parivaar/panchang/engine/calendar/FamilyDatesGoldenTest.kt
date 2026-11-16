package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.b
import app.parivaar.panchang.engine.calendar.M04Golden.d
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.isNull
import app.parivaar.panchang.engine.calendar.M04Golden.l
import app.parivaar.panchang.engine.calendar.M04Golden.o
import app.parivaar.panchang.engine.calendar.M04Golden.s
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G-M10 family dates (docs/specs/M10.md, tests): both backends against `family_dates.json` from the independent
 * oracle `tools/oracle/gen_family.py`. Janma indices and labels exact on rows not fragile for the backend;
 * occurrences 2024-2030 of the rule-8 definitions (built by [FamilyDateFinder.def] from the fixture's facts) for
 * eight cities compared row by row like G-M04 ([M04Festivals.compare]: dates exact unless fragile, then one of
 * `alt`; aparahna windows within the section 1 sunrise limits, Precision 5 s / Base 10 s).
 */
@Tag("golden")
class FamilyDatesGoldenTest {
    private val root = M04Golden.fixture("family_dates.json")
    private val meta = M04Golden.meta(root)
    private val janma = root.getValue("janma").a().map { it.a() }
    private val defRows = root.getValue("defs").a().map { it.a() }
    private val dayRows = root.getValue("days").a().map { it.a() }

    private class FamCity(val name: String, val place: Place, val offsetMin: Int, val rows: List<JsonArray>)

    private val cities: List<FamCity> by lazy {
        val metaCities = meta.getValue("cities").a()
        val rows = root.getValue("cities").a()
        assertEquals(CITIES, rows.size, "fixture cities")
        assertEquals(CITIES, metaCities.size, "meta cities")
        rows.mapIndexed { k, c ->
            val o = c.o()
            val mo = metaCities[k].o()
            assertEquals(mo.getValue("name").s(), o.getValue("name").s(), "city $k name")
            assertEquals(mo.getValue("offsetMin").i(), o.getValue("offsetMin").i(), "city $k offset")
            val list = o.getValue("rows").a().map { it.a() }
            assertEquals(mo.getValue("rows").i(), list.size, "city $k rows vs meta")
            FamCity(o.getValue("name").s(), Place(mo.getValue("lat").d(), mo.getValue("lon").d(), 0.0),
                o.getValue("offsetMin").i(), list)
        }
    }

    private fun label(e: JsonElement): LunarLabel {
        val x = e.a()
        return LunarLabel(Masa.entries[x[0].i()], x[1].b(), if (x[2].isNull()) null else Masa.entries[x[2].i()])
    }

    private fun facts(r: JsonArray) =
        JanmaFacts(r[3].i(), label(r[4]), label(r[5]), r[6].i(), Rashi.entries[r[7].i()])

    /** The definitions of the fixture, built by the engine's def() from the oracle's janma facts. */
    private fun defs(finder: FamilyDateFinder): List<FestivalDef> = defRows.map { r ->
        val prefs = FamilyDatePrefs(
            if (r[4].isNull()) MonthSystem.AMANTA else MonthSystem.valueOf(r[4].s()),
            if (r[3].isNull()) NakshatraMonthBasis.SOLAR else NakshatraMonthBasis.valueOf(r[3].s()),
            if (r[5].isNull()) SolarRegion.TAMIL else SolarRegion.valueOf(r[5].s()),
        )
        finder.def(FamilyDateKind.valueOf(r[2].s()), facts(janma[r[1].i()]), prefs, r[0].s())
    }

    private val typeOf: Map<String, String> by lazy {
        defRows.associate { r -> r[0].s() to r[2].s() + (if (r[3].isNull()) "" else "_" + r[3].s()) }
    }

    @Test
    fun fixtureShape() {
        assertEquals(JANMA_ROWS, janma.size, "janma rows")
        assertEquals(JANMA_ROWS, meta.getValue("janmaRows").i(), "meta.janmaRows")
        assertEquals(DEFS, defRows.size, "defs")
        assertEquals(DAY_ROWS, dayRows.size, "unknown-time day rows")
        assertEquals(DAY_EXACT, meta.getValue("dayExact").i(), "meta.dayExact")
        assertEquals(DEFS, meta.getValue("defs").i(), "meta.defs")
        assertEquals(ROWS, meta.getValue("rows").i(), "meta.rows")
        assertEquals(ROWS, cities.sumOf { it.rows.size }, "occurrence rows")
        assertEquals("2024-01-01..2030-12-31", meta.getValue("range").s())
        val tags = janma.map { it[1].s() }
        assertEquals(RANDOM, tags.count { it == "random" }, "random births")
        for (t in MUST_HAVE) assertTrue(tags.any { it.startsWith(t) }, "must-have row $t")
        val ids = defRows.map { it[0].s() }.toSet()
        assertTrue(ids.all { Regex("fam_[0-9]+").matches(it) }, "def ids are fam_<n>")
        assertTrue(cities.all { c -> c.rows.all { it[0].s() in ids } }, "rows with unknown ids")
    }

    @Test
    fun changelogCarriesTheDatesHash() {
        val repo = File(checkNotNull(System.getProperty("pp.rulesDir")) { "pp.rulesDir is not set" }).parentFile
        val changelog = File(repo, "engine-golden/CHANGELOG.md").readText()
        val prefix = meta.getValue("datesSha256").s().take(DATES_HASH_HEX)
        assertTrue("family_dates.json datesSha256:$prefix" in changelog, "CHANGELOG lacks the dates hash $prefix")
    }

    @Test
    fun janmaPrecision() = janma(M04Golden.PRECISION)

    @Test
    fun janmaBase() = janma(M04Golden.BASE)

    @Test
    fun unknownTimeDaysPrecision() = days(M04Golden.PRECISION)

    @Test
    fun unknownTimeDaysBase() = days(M04Golden.BASE)

    /**
     * Review regression (M10): a reminder query starting late June 2026, after the adhika Jyeshtha split of the
     * purnimanta nija month, against the oracle rows of that window (lunar-basis defs, Delhi).
     */
    @Test
    fun lunarDefsInANarrowWindowPrecision() {
        val lim = M04Golden.PRECISION
        val c = cities.first { it.name == "delhi" }
        val lunarIds = defRows.filter { !it[3].isNull() && it[3].s() == "LUNAR" }.map { it[0].s() }.toSet()
        val tally = M04Festivals.Tally()
        for (first in listOf(LocalDate(2026, 6, 26), LocalDate(2026, 6, 28), LocalDate(2026, 7, 2))) {
            val finder = FamilyDateFinder(lim.eph())
            val defs = defs(finder).filter { it.id in lunarIds }
            val rows = c.rows.filter { it[0].s() in lunarIds && LocalDate.parse(it[1].s()) in first..LAST }
            val occ = finder.occurrences(defs, first, LAST, c.place) { c.offsetMin * SECONDS_PER_MINUTE }
            M04Festivals.compare(lim, "delhi from $first", rows, occ, { typeOf.getValue(it) }, tally)
        }
        M04Golden.assertNoFailures("family narrow window", tally.failures.list())
    }

    private fun days(lim: M04Golden.Limits) {
        val finder = FamilyDateFinder(lim.eph())
        val failures = ArrayList<String>()
        var compared = 0
        var exact = 0
        for (r in dayRows) {
            if (r[lim.frag(DAY_FRAG_P)].b()) continue
            val c = cities.first { it.name == r[1].s() }
            val got = finder.janmaOfDay(LocalDate.parse(r[0].s()), c.offsetMin * SECONDS_PER_MINUTE, c.place)
            val tithis = r[2].a().map { it.i() }
            val naks = r[3].a().map { it.i() }
            val want = JanmaFacts(r[4].i(), label(r[5]), label(r[6]), r[7].i(), Rashi.entries[r[8].i()])
            compared++
            val ok = when (got) {
                is JanmaResult.Exact -> (tithis.size == 1 && naks.size == 1).also { if (it) exact++ }
                is JanmaResult.Ambiguous -> got.tithis == tithis && got.nakshatras == naks
            }
            if (!ok || got.factsAtSunrise() != want) failures += "${lim.name} day ${r[0].s()} ${r[1].s()}"
        }
        M04Golden.report("family days ${lim.name}", listOf("rows compared $compared (exact $exact)"))
        M04Golden.assertNoFailures("family days ${lim.name}", failures)
        assertTrue(exact >= DAY_EXACT - DAY_FRAGILE_MAX && compared >= DAY_ROWS - DAY_FRAGILE_MAX, "too few day rows")
    }

    @Test
    fun precision() = occurrences(M04Golden.PRECISION)

    @Test
    fun base() = occurrences(M04Golden.BASE)

    private fun janma(lim: M04Golden.Limits) {
        val finder = FamilyDateFinder(lim.eph())
        val failures = ArrayList<String>()
        var compared = 0
        for (r in janma) {
            if (r[lim.frag(JANMA_FRAG_P)].b()) continue
            compared++
            val got = finder.janma(M04Golden.at(r[2].l()))
            val want = facts(r)
            if (got != want) {
                failures += "${lim.name} janma row ${r[0]}: tithi ${got.tithi}/${want.tithi} nakshatra " +
                    "${got.nakshatra}/${want.nakshatra} sun ${got.sunRashi}/${want.sunRashi} labels " +
                    "${got.amanta == want.amanta}/${got.purnimanta == want.purnimanta}"
            }
        }
        M04Golden.report("family janma ${lim.name}", listOf("rows compared $compared, mismatches ${failures.size}"))
        M04Golden.assertNoFailures("family janma ${lim.name}", failures)
        assertTrue(compared >= JANMA_ROWS - JANMA_FRAGILE_MAX, "too few janma rows compared: $compared")
    }

    private fun occurrences(lim: M04Golden.Limits) {
        val tally = M04Festivals.Tally()
        M04Golden.parallel(cities) { c ->
            val finder = FamilyDateFinder(lim.eph())
            val occ = finder.occurrences(defs(finder), FIRST, LAST, c.place) { c.offsetMin * SECONDS_PER_MINUTE }
            M04Festivals.compare(lim, c.name, c.rows, occ, { typeOf.getValue(it) }, tally)
        }
        val lines = tally.types.toSortedMap().map { (t, v) ->
            "$t: rows ${v.rows}, fragile ${v.fragile} (engine absent ${v.absentFragile}), mismatches ${v.failures}"
        } + listOf(
            "sun-derived endpoints max ${tally.sun.fmt()} s (limit ${lim.sunS})",
            "limb endpoints max ${tally.limb.fmt()} s (limit ${M04Festivals.limbLimit(lim)})",
            "flags seen on compared rows: " +
                tally.flagRows.toSortedMap().entries.joinToString { "${it.key} ${it.value}" },
        )
        M04Golden.report("family dates ${lim.name}", lines)
        M04Golden.assertNoFailures("family dates ${lim.name}", tally.failures.list())
        assertEquals(ROWS, tally.types.values.sumOf { it.rows }, "family rows compared")
    }

    private companion object {
        const val CITIES = 8
        const val RANDOM = 300
        const val JANMA_ROWS = 309
        const val JANMA_FRAG_P = 8
        const val JANMA_FRAGILE_MAX = 6
        const val DEFS = 483
        const val DAY_ROWS = 137
        const val DAY_EXACT = 18
        const val DAY_FRAG_P = 9
        const val DAY_FRAGILE_MAX = 10
        const val ROWS = 26_887
        const val DATES_HASH_HEX = 16
        const val SECONDS_PER_MINUTE = 60
        val FIRST = LocalDate(2024, 1, 1)
        val LAST = LocalDate(2030, 12, 31)
        val MUST_HAVE = listOf(
            "adhika_masa_2026", "kshaya_tithi", "tithi_boundary_plus_40s", "nakshatra_boundary_minus_45s",
            "nakshatra_twice", "nakshatra_absent", "mumbai_pre_1955", "war_time_1943", "auckland_dateline",
        )
    }
}
