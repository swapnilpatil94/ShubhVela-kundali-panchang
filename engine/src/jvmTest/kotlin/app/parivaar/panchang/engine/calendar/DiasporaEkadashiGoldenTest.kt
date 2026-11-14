package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.M04Golden.s
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Diaspora Ekadashi (docs/specs/M04.md section 4): New York and London 2024-2030, both traditions, equal the
 * fixture rows (fragile rows by their alternative dates); the same dates result with a test-only US / UK DST
 * offset function; at least one date differs from the dates computed here for Delhi.
 */
@Tag("golden")
class DiasporaEkadashiGoldenTest {
    private val root = M04Golden.fixture("festivals_oracle.json")
    private val ids = setOf("ekadashi_smarta", "ekadashi_vaishnava")
    private val defs = M04Festivals.defs.filter { it.id in ids }
    private val first = LocalDate(2024, 1, 1)
    private val last = LocalDate(2030, 12, 31)

    private fun nthWeekday(year: Int, month: Int, day: DayOfWeek, n: Int): LocalDate {
        var d = LocalDate(year, month, 1)
        while (d.dayOfWeek != day) d = d.plus(DatePeriod(days = 1))
        return d.plus(DatePeriod(days = WEEK * (n - 1)))
    }

    private fun lastSunday(year: Int, month: Int): LocalDate {
        var d = LocalDate(year, month + 1, 1).minus(DatePeriod(days = 1))
        while (d.dayOfWeek != DayOfWeek.SUNDAY) d = d.minus(DatePeriod(days = 1))
        return d
    }

    /** US: second Sunday of March .. first Sunday of November; UK: last Sunday of March .. last Sunday of October. */
    private fun offset(city: String): (LocalDate) -> Int = { d ->
        when (city) {
            "newyork" -> {
                val start = nthWeekday(d.year, MARCH, DayOfWeek.SUNDAY, 2)
                val dst = d >= start && d < nthWeekday(d.year, NOV, DayOfWeek.SUNDAY, 1)
                (if (dst) US_SUMMER_MIN else US_WINTER_MIN) * SECONDS_PER_MINUTE
            }
            else -> {
                val dst = d >= lastSunday(d.year, MARCH) && d < lastSunday(d.year, OCT)
                (if (dst) UK_SUMMER_MIN else 0) * SECONDS_PER_MINUTE
            }
        }
    }

    @Test
    fun precision() = run(M04Golden.PRECISION)

    @Test
    fun base() = run(M04Golden.BASE)

    private fun run(lim: M04Golden.Limits) {
        val fixture = M04Festivals.cities(root).associateBy { it.name }
        val engine = FestivalEngine(lim.eph())
        val tally = M04Festivals.Tally()
        val dates = HashMap<String, Set<String>>()
        for (name in listOf("newyork", "london")) {
            val c = fixture.getValue(name)
            val rows = c.rows.filter { it[0].s() in ids && it[1].s() >= "2024-01-01" && it[1].s() <= "2030-12-31" }
            for ((label, fn) in listOf<Pair<String, (LocalDate) -> Int>>(
                "standard" to { c.offsetMin * SECONDS_PER_MINUTE }, "dst" to offset(name),
            )) {
                val occ = engine.occurrences(defs, first, last, c.place, utcOffsetSeconds = fn)
                M04Festivals.compare(lim, "$name $label", rows, occ, { "ekadashi" }, tally)
                if (label == "dst") dates[name] = occ.map { "${it.id} ${it.date}" }.toSet()
            }
        }
        val delhi = fixture.getValue("delhi")
        val delhiDates = engine.occurrences(defs, first, last, delhi.place) { delhi.offsetMin * SECONDS_PER_MINUTE }
            .map { "${it.id} ${it.date}" }.toSet()
        val differing = dates.values.sumOf { (it - delhiDates).size }
        M04Golden.report(
            "diaspora ekadashi ${lim.name}",
            listOf(
                "new york and london 2024-2030, both traditions: rows ${tally.types.getValue("ekadashi").rows}, " +
                    "fragile ${tally.types.getValue("ekadashi").fragile}, " +
                    "mismatches ${tally.types.getValue("ekadashi").failures}",
                "dates differing from the locally computed Delhi dates: $differing",
            ),
        )
        M04Golden.assertNoFailures("diaspora ${lim.name}", tally.failures.list())
        assertTrue(differing >= 1, "no diaspora date differs from Delhi")
        assertEquals(setOf("newyork", "london"), dates.keys)
    }

    private companion object {
        const val WEEK = 7
        const val MARCH = 3
        const val OCT = 10
        const val NOV = 11
        const val US_WINTER_MIN = -300
        const val US_SUMMER_MIN = -240
        const val UK_SUMMER_MIN = 60
        const val SECONDS_PER_MINUTE = 60
    }
}
