package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.astro.EphemerisRangeException
import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.o
import app.parivaar.panchang.engine.calendar.M04Golden.s
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.PanchangError
import app.parivaar.panchang.engine.panchang.PanchangUnavailableException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Tag
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G-M04 days: both backends x 15,610 city-days of `calendar_days.json` (docs/specs/M04.md section 4): tithi at
 * sunrise, amanta and purnimanta labels, the seven era years, ritu, ayana and the four solar dates. Every field
 * equals the fixture unless the row is fragile for the backend; then it must be one of the fixture's
 * alternative values. A fixture 'U' day must throw NO_SUN_DAY. Precision throws [EphemerisRangeException] for
 * days outside the DE440 excerpt: asserted for exactly the literal set, and those days are compared on Base.
 */
@Suppress(
    "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements", "ReturnCount",
    "SwallowedException",
) // a golden comparison is one flat list of gates per fixture row; splitting scatters the limits
@Tag("golden")
class CalendarDaysGoldenTest {
    private class Tally {
        val failures = M04Golden.Failures()
        val compared = AtomicInteger()
        val unavailable = AtomicInteger()
        val fragileRows = AtomicInteger()
        val fragileFields = AtomicInteger()
        val fragileMatchedBase = AtomicInteger()
        val flagged = AtomicInteger()
        val runFallbacks = AtomicInteger()
        val thrown = java.util.concurrent.ConcurrentLinkedQueue<String>()
    }

    @Test
    fun precision() = run(M04Golden.PRECISION)

    @Test
    fun base() = run(M04Golden.BASE)

    private fun run(limits: M04Golden.Limits) {
        val root = M04Golden.fixture("calendar_days.json")
        assertEquals(M04Golden.CITY_DAYS, M04Golden.meta(root).getValue("cityDays").i(), "meta.cityDays")
        val cities = M04Golden.cities(root, "days", "cityDays", M04Golden.DAYS_PER_CITY)
        val tally = Tally()
        M04Golden.parallel(cities) { checkCity(limits, it, tally) }
        val expectedThrown = if (limits === M04Golden.PRECISION) OUTSIDE_EXCERPT else emptySet()
        assertEquals(expectedThrown, tally.thrown.toSet(), "${limits.name} days that throw EphemerisRangeException")
        assertEquals(M04Golden.CITY_DAYS, tally.compared.get() + tally.unavailable.get() + tally.thrown.size,
            "city-days accounted for")
        assertEquals(UNAVAILABLE_DAYS, tally.unavailable.get(), "fixture U days")
        M04Golden.report(
            "days ${limits.name}",
            listOf(
                "city-days ${M04Golden.CITY_DAYS}: compared ${tally.compared.get()}, NO_SUN_DAY asserted " +
                    "${tally.unavailable.get()}, outside excerpt ${tally.thrown.size} " +
                    "(${if (expectedThrown.isEmpty()) "compared on Base" else "EphemerisRangeException asserted"})",
                "fragile rows ${tally.fragileRows.get()}, fragile fields ${tally.fragileFields.get()}, engine " +
                    "value = fixture value ${tally.fragileMatchedBase.get()}",
                "runs redone day by day after NO_SUN_DAY ${tally.runFallbacks.get()}",
                "sun-fallback flagged days ${tally.flagged.get()}",
            ),
        )
        M04Golden.assertNoFailures("days ${limits.name}", tally.failures.list())
    }

    private fun checkCity(lim: M04Golden.Limits, city: M04Golden.City, t: Tally) {
        val calc = CalendarCalculator(lim.eph())
        val rows = city.row.getValue("days").a().map { it.a() }
        val offset: (LocalDate) -> Int = { city.offsetSeconds }
        val byDate = HashMap<LocalDate, CalendarDay>()
        val unavailable = rows.filter { 'U' in it[1].s() }.map { LocalDate.parse(it[0].s()) }.toSet()
        // runs of consecutive days for the 2024-2043 block, cut at unavailable days
        for (year in 2024..2043) {
            var from: LocalDate? = null
            var d = LocalDate(year, 1, 1)
            val end = LocalDate(year + 1, 1, 1)
            while (d <= end) {
                val cut = d == end || d in unavailable
                if (cut) {
                    val f = from
                    if (f != null) {
                        val n = f.until(d)
                        // a run reaching the unavailable day throws as a whole: its dates are then done one by one
                        try {
                            calc.days(f, n, city.place, utcOffsetSeconds = offset).forEach { byDate[it.date] = it }
                        } catch (e: PanchangUnavailableException) {
                            t.runFallbacks.incrementAndGet()
                        }
                    }
                    from = null
                } else if (from == null) {
                    from = d
                }
                d = d.plus(DatePeriod(days = 1))
            }
        }
        for (row in rows) {
            val date = LocalDate.parse(row[0].s())
            val at = "${city.name} ${row[0].s()}"
            if ('U' in row[1].s()) {
                val code = runCatching { calc.day(date, city.offsetSeconds, city.place) }
                    .exceptionOrNull().let { (it as? PanchangUnavailableException)?.code }
                if (code != PanchangError.NO_SUN_DAY) t.failures += "${lim.name} $at expected NO_SUN_DAY, got $code"
                t.unavailable.incrementAndGet()
                continue
            }
            val day = byDate[date] ?: try {
                calc.day(date, city.offsetSeconds, city.place)
            } catch (e: EphemerisRangeException) {
                t.thrown += "${city.name} ${row[0].s()}"
                continue
            } catch (e: PanchangUnavailableException) {
                t.failures += "${lim.name} $at unexpected ${e.code}"
                continue
            }
            t.compared.incrementAndGet()
            compare(lim, row, day, at, t)
        }
    }

    private fun LocalDate.until(other: LocalDate): Int = (other.toEpochDays() - toEpochDays()).toInt()

    private fun compare(lim: M04Golden.Limits, row: JsonArray, day: CalendarDay, at: String, t: Tally) {
        val flags = row[1].s()
        val fallback = DayFlag.SUN_FALLBACK in day.flags
        val extra = DayFlag.EXTRA_SUNRISE in day.flags
        if (fallback != ('F' in flags) || extra != ('X' in flags)) {
            t.failures += "${lim.name} $at flags ${day.flags} vs fixture '$flags'"
        }
        if ('F' in flags) t.flagged.incrementAndGet()
        val frag: JsonObject = row[lim.frag(12)].o()
        for (k in frag.keys) if (k !in FIELDS) t.failures += "${lim.name} $at unknown fragile field $k"
        if (frag.isNotEmpty()) t.fragileRows.incrementAndGet()
        val sv = row[8].a()
        val solar = row[11].a()
        fun label(l: LunarLabel) = "[${l.masa.ordinal},${l.adhika},${l.kshayaMasa?.ordinal ?: "null"}]"
        fun check(field: String, got: String, want: String) {
            val alts = frag[field]
            if (alts != null) {
                t.fragileFields.incrementAndGet()
                if (got == want) t.fragileMatchedBase.incrementAndGet()
                if (got !in alts.a().map { it.toString() }) {
                    t.failures += "${lim.name} $at $field $got not in fragile alt"
                }
            } else if (got != want) {
                t.failures += "${lim.name} $at $field $got vs $want"
            }
        }
        check("tithi", day.tithiAtSunrise.toString(), row[5].toString())
        check("amanta", label(day.amanta), row[6].toString())
        check("purnimanta", label(day.purnimanta), row[7].toString())
        val sm = day.samvat
        check("shaka", sm.shaka.toString(), sv[0].toString())
        check("vikramChaitradi", sm.vikramChaitradi.toString(), sv[1].toString())
        check("vikramKartikadi", sm.vikramKartikadi.toString(), sv[2].toString())
        check("samvatsara", sm.samvatsara.toString(), sv[3].toString())
        check("samvatsaraTamil", sm.samvatsaraTamil.toString(), sv[4].toString())
        check("bangabda", sm.bangabda.toString(), sv[5].toString())
        check("kollam", sm.kollam.toString(), sv[6].toString())
        check("ritu", day.ritu.ordinal.toString(), row[9].toString())
        check("ayana", day.ayana.ordinal.toString(), row[10].toString())
        for ((k, r) in SolarRegion.entries.withIndex()) {
            val sd = day.solar.getValue(r)
            check("solar${r.name}", "[${sd.month.ordinal},${sd.day},\"${sd.monthStart}\"]", solar[k].toString())
        }
    }

    private companion object {
        val FIELDS = setOf(
            "tithi", "amanta", "purnimanta", "shaka", "vikramChaitradi", "vikramKartikadi", "samvatsara",
            "samvatsaraTamil", "bangabda", "kollam", "ritu", "ayana",
        ) + SolarRegion.entries.map { "solar${it.name}" }

        /** Fixture rows with flag U (Tromso, no usable sunrise/sunset pair): literal. */
        const val UNAVAILABLE_DAYS = 1

        /** "city date" of the four early-1900 days outside the DE440 excerpt (Precision throws); literal. */
        val OUTSIDE_EXCERPT: Set<String> = setOf(
            "newyork 1900-11-01", "kolkata 1900-03-02", "chennai 1900-05-29", "auckland 1900-08-11",
        )
    }
}
