package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.d
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.o
import app.parivaar.panchang.engine.calendar.M04Golden.s
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner reference files `festivals_{north,mr,gu,ta,ml,bn,or}.json` (docs/specs/M04.md sections 3.10 and 4):
 * TODO_OWNER means exactly zero entries; SIGNED_OFF means every definition of the region (and "all") is present for
 * the seven MASTER cities and 2024-2030 and every entry's date is exactly the engine's, on both backends.
 * Any other status fails.
 */
@Tag("golden")
class FestivalReferenceGoldenTest {
    private val regions = listOf("north", "mr", "gu", "ta", "ml", "bn", "or")
    private val masterCities = listOf("delhi", "mumbai", "chennai", "kolkata", "newyork", "london", "sydney")

    @Test
    fun sevenFilesAreTodoWithNoEntriesOrSignedOffAndExact() {
        val statuses = regions.associateWith { r ->
            val root = M04Golden.fixture("festivals_$r.json")
            assertEquals(r, root.getValue("region").s(), "region of festivals_$r.json")
            val status = root.getValue("status").s()
            val entries = root.getValue("entries").a()
            when (status) {
                "TODO_OWNER" -> assertEquals(0, entries.size, "festivals_$r.json is TODO_OWNER: no entries allowed")
                "SIGNED_OFF" -> signedOff(r, entries.map { it.a() })
                else -> throw AssertionError("festivals_$r.json has unknown status $status")
            }
            status
        }
        M04Golden.report(
            "festival reference files",
            regions.map { r ->
                val st = statuses.getValue(r)
                "festivals_$r: $st" + if (st == "TODO_OWNER") ", 0 entries" else ""
            },
        )
    }

    private fun signedOff(region: String, entries: List<kotlinx.serialization.json.JsonArray>) {
        val defs = M04Festivals.defs.filter { region in it.regions || "all" in it.regions }
        val cities = M04Festivals.cities(M04Golden.fixture("festivals_oracle.json")).filter { it.name in masterCities }
        assertEquals(masterCities.size, cities.size, "MASTER cities in the fixture")
        for (lim in listOf(M04Golden.PRECISION, M04Golden.BASE)) {
            val engine = FestivalEngine(lim.eph())
            val want = HashSet<String>()
            for (c in cities) {
                val occ = engine.occurrences(
                    M04Festivals.defs, LocalDate(FIRST_YEAR, 1, 1), LocalDate(LAST_YEAR, DEC, LAST_DAY), c.place,
                ) { c.offsetMin * SECONDS_PER_MINUTE }.filter { o -> defs.any { it.id == o.id } }
                occ.forEach { want += "${it.id}|${c.name}|${it.date}" }
            }
            val got = entries.map { e ->
                val place = Place(e[1].d(), e[2].d(), 0.0)
                val city = cities.firstOrNull { it.place == place && it.offsetMin == e[3].i() }
                assertTrue(city != null, "entry for a place outside the MASTER cities")
                assertTrue(e[5].s().isNotBlank(), "entry without a sourceId")
                "${e[0].s()}|${city.name}|${e[4].s()}"
            }.toSet()
            assertEquals(want, got, "${lim.name} reference entries vs engine dates for $region")
        }
    }

    private companion object {
        const val FIRST_YEAR = 2024
        const val LAST_YEAR = 2030
        const val DEC = 12
        const val LAST_DAY = 31
        const val SECONDS_PER_MINUTE = 60
    }
}
