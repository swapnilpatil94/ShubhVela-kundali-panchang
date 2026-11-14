package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.s
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `festivals_published_soft.json` (docs/specs/M04.md acceptance 8): 80 commonly published Delhi dates, status VERIFY.
 * VERIFY evaluates all entries on both backends, writes the match count and the mismatching (id, year) to the
 * summary and passes; CONFIRMED requires every entry to equal the engine's date exactly on both backends.
 */
@Tag("golden")
class FestivalsPublishedSoftGoldenTest {
    private val delhi = Place(28.6139, 77.2090, 0.0)

    @Test
    fun precision() = run(M04Golden.PRECISION)

    @Test
    fun base() = run(M04Golden.BASE)

    private fun run(lim: M04Golden.Limits) {
        val root = M04Golden.fixture("festivals_published_soft.json")
        val status = root.getValue("status").s()
        assertTrue(status == "VERIFY" || status == "CONFIRMED", "unknown status $status")
        assertEquals(DELHI_OFFSET_MIN, root.getValue("offsetMin").i(), "offset of the soft list")
        val entries = root.getValue("entries").a().map { it.a() }
        assertEquals(ENTRIES, entries.size, "soft list entries")
        val engine = FestivalEngine(lim.eph())
        val occ = engine.occurrences(
            M04Festivals.defs, LocalDate(FIRST_YEAR, 1, 1), LocalDate(LAST_YEAR, DEC, LAST_DAY), delhi,
        ) { DELHI_OFFSET_MIN * SECONDS_PER_MINUTE }
        val dates = occ.groupBy { it.id }.mapValues { (_, v) -> v.map { it.date.toString() }.toSet() }
        val known = M04Festivals.defs.map { it.id }.toSet()
        val misses = entries.filter { e ->
            assertTrue(e[0].s() in known, "soft list names an unknown festival")
            e[1].s() !in dates[e[0].s()].orEmpty()
        }
        val list = misses.joinToString { "${it[0].s()} ${it[1].s().take(YEAR_CHARS)}" }
        M04Golden.report(
            "festivals published soft list ${lim.name}",
            listOf(
                "status $status: matched ${entries.size - misses.size} of ${entries.size}",
                "mismatching (id, year): ${list.ifEmpty { "none" }}",
            ),
        )
        if (status == "CONFIRMED") assertTrue(misses.isEmpty(), "${lim.name} confirmed dates differ: $list")
    }

    private companion object {
        const val ENTRIES = 80
        const val DELHI_OFFSET_MIN = 330
        const val FIRST_YEAR = 2024
        const val LAST_YEAR = 2026
        const val DEC = 12
        const val LAST_DAY = 31
        const val SECONDS_PER_MINUTE = 60
        const val YEAR_CHARS = 4
    }
}
