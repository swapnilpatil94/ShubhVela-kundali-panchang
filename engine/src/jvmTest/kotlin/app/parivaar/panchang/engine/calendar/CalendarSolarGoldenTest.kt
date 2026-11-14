package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.b
import app.parivaar.panchang.engine.calendar.M04Golden.d
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.isNull
import app.parivaar.panchang.engine.calendar.M04Golden.l
import app.parivaar.panchang.engine.calendar.M04Golden.s
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G-M04 solar months: both backends x 10 cities x 4 regions x the sankrantis of 1950-2050 of
 * `calendar_solar.json` (docs/specs/M04.md section 4): the sankranti sequence and instants (M02 ingress gate),
 * the cut-off instant (sun-derived, S limit) and the month start date, which must equal the fixture unless the
 * row is fragile for the backend, and then must be one of the fixture's alternative dates.
 */
@Suppress(
    "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements", "ReturnCount",
    "SwallowedException",
) // a golden comparison is one flat list of gates per fixture row; splitting scatters the limits
@Tag("golden")
class CalendarSolarGoldenTest {
    private class Tally {
        val sankranti = M04Golden.Stat()
        val cutoff = M04Golden.Stat()
        val failures = M04Golden.Failures()
        var rows = 0
        var fragile = 0
        var fragileMatchedBase = 0
        var nearCutoff = 0
        var altCutMatches = 0

        @Synchronized
        fun merge(o: Tally) {
            rows += o.rows
            fragile += o.fragile
            fragileMatchedBase += o.fragileMatchedBase
            nearCutoff += o.nearCutoff
            altCutMatches += o.altCutMatches
        }
    }

    @Test
    fun precision() = run(M04Golden.PRECISION)

    @Test
    fun base() = run(M04Golden.BASE)

    private fun run(limits: M04Golden.Limits) {
        val root = M04Golden.fixture("calendar_solar.json")
        assertEquals(M04Golden.SOLAR_ROWS, M04Golden.meta(root).getValue("solarRows").i(), "meta.solarRows")
        val cities = M04Golden.cities(root, "rows", "solarRows", M04Golden.SOLAR_PER_CITY)
        val rate = sankrantiRates()
        val tally = Tally()
        val tasks = cities.flatMap { c -> SolarRegion.entries.map { c to it } }
        M04Golden.parallel(tasks) { (city, region) -> checkRegion(limits, city, region, rate, tally) }
        assertEquals(M04Golden.SOLAR_ROWS, tally.rows, "solar rows compared")
        assertTrue(tally.nearCutoff >= MIN_NEAR_CUTOFF, "cut-off near-miss rows ${tally.nearCutoff}")
        M04Golden.report(
            "solar months ${limits.name}",
            listOf(
                "rows ${tally.rows} (10 cities x 4 regions x 1950-2050 sankrantis)",
                "sankranti max ${tally.sankranti.fmt()} s",
                "cut-off max ${tally.cutoff.fmt()} s (limit ${limits.sunS})",
                "cut-off matched through the fixture altCut of this backend: ${tally.altCutMatches}",
                "fragile ${tally.fragile}, of which engine date = fixture date ${tally.fragileMatchedBase}",
                "non-fragile-P rows with |sankranti - cut-off| < 600 s: ${tally.nearCutoff} (minimum $MIN_NEAR_CUTOFF)",
            ),
        )
        M04Golden.assertNoFailures("solar months ${limits.name}", tally.failures.list())
    }

    /** Sidereal Sun rate (arcsec/s) of every sankranti, from `calendar_lunations.json` (same oracle world). */
    private fun sankrantiRates(): Map<Long, Double> {
        val rows = M04Golden.fixture("calendar_lunations.json").getValue("lunations").a()
        return buildMap {
            for (r in rows) for (x in r.a()[4].a()) put(x.a()[1].l(), x.a()[2].d())
        }
    }

    private fun checkRegion(
        lim: M04Golden.Limits,
        city: M04Golden.City,
        region: SolarRegion,
        rate: Map<Long, Double>,
        tally: Tally,
    ) {
        val calc = SolarCalendar(LunarCalendar(lim.eph()))
        val rows = city.row.getValue("rows").a().map { it.a() }.filter { it[0].s() == region.name }
        assertEquals(M04Golden.SOLAR_PER_CITY / SolarRegion.entries.size, rows.size, "rows of a region")
        val from = M04Golden.at(FIRST_MS)
        val to = M04Golden.at(END_MS)
        val starts = ArrayList<SolarMonthStart>()
        var a = LocalDate(1949, 12, 1)
        while (a < LAST_DATE) {
            val b = minOf(a.plus(DatePeriod(years = 10)), LAST_DATE)
            starts += calc.monthStarts(region, a, b.plus(DatePeriod(days = -1)), city.place) { city.offsetSeconds }
            a = b
        }
        val got = starts.filter { it.sankranti.at >= from && it.sankranti.at < to }.sortedBy { it.sankranti.at }
        val local = Tally()
        assertEquals(rows.size, got.size, "${lim.name} ${city.name} $region month start count")
        for ((row, st) in rows.zip(got)) {
            val at = "${city.name} $region ${row[4].s()}"
            local.rows++
            val ms = row[2].l()
            if (Rashi.entries[row[1].i()] != st.month) {
                local.failures += "${lim.name} $at month ${st.month} vs ${row[1].i()}"
            }
            val v = rate[ms]
            val dt = M04Golden.dtS(st.sankranti.at, ms)
            val limit = if (v != null && v > 0.0) lim.sankrantiBaseS + lim.sankrantiArcsec / v else Double.NaN
            tally.sankranti.add(dt, at)
            if (!(dt <= limit)) local.failures += "${lim.name} $at sankranti |dt| $dt s > $limit s"
            if (row[3].isNull() != (st.cutoff == null)) {
                local.failures += "${lim.name} $at cut-off presence ${st.cutoff != null}"
            } else if (st.cutoff != null) {
                val main = M04Golden.dtS(st.cutoff, row[3].l())
                val viaAlt = row[lim.frag(8)].a().minOfOrNull { M04Golden.dtS(st.cutoff, it.l()) }
                // the neighbouring day's cut-off counts only where the fixture lists it for this backend
                val dc = if (main <= lim.sunS || viaAlt == null) main else viaAlt
                if (dc != main) local.altCutMatches++
                tally.cutoff.add(dc, at)
                if (!(dc <= lim.sunS)) local.failures += "${lim.name} $at cut-off |dt| $dc s > ${lim.sunS} s"
            }
            val fixtureP = row[5].b()
            if (!row[3].isNull() && !fixtureP && kotlin.math.abs(ms - row[3].l()) < NEAR_MS) local.nearCutoff++
            val fragile = row[lim.frag(5)].b()
            val alt = row[7].a().map { it.s() }
            val date = st.date.toString()
            if (fragile) {
                local.fragile++
                if (date == row[4].s()) local.fragileMatchedBase++
                if (date !in alt) local.failures += "${lim.name} $at fragile date $date not in alt $alt"
            } else if (date != row[4].s()) {
                local.failures += "${lim.name} $at date $date vs ${row[4].s()}"
            }
        }
        tally.merge(local)
        local.failures.list().forEach { tally.failures += it }
    }

    private companion object {
        const val MIN_NEAR_CUTOFF = 20
        const val NEAR_MS = 600_000L

        /** 1950-01-01T00:00Z and 2051-01-01T00:00Z: the sankranti window of the fixture. */
        const val FIRST_MS = -631_152_000_000L
        const val END_MS = 2_556_144_000_000L
        val LAST_DATE = LocalDate(2051, 2, 1)
    }
}
