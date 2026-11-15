package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.engine.calendar.CalendarCalculator
import app.parivaar.panchang.engine.calendar.EclipseSearch
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.FestivalEngine
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.jyotish.ChartCalculator
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.panchang.PanchangCalculator
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.PanchangUnavailableException
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Calls the engine and indexes its output; no astronomy here and no lock: the caller serialises (in the app,
 * `EngineAccess`). [defs] is every shipped festival definition; [recurringIds] marks the recurring file's ids.
 */
class DirectCalendarEngine(
    private val calc: PanchangCalculator,
    private val cal: CalendarCalculator,
    private val fest: FestivalEngine,
    private val ecl: EclipseSearch,
    private val chart: ChartCalculator,
    private val gochar: Gochar,
    private val defs: List<FestivalDef>,
    private val recurringIds: Set<String> = emptySet(),
) : CalendarEngine {
    override fun month(gridFirst: LocalDate, n: Int, i: CalInputs): MonthBundle {
        val last = gridFirst.plus(DatePeriod(days = n - 1))
        val block = blockOf(gridFirst, i)
        val days = block.calendar(gridFirst, n)
        val pan = panchangDays(gridFirst, n, i)
        val filtered = defs.forTradition(i.ekadashi)
        val fests = block.festivals(i.ekadashi).filter { it.date in gridFirst..last }
        // Only the grid's range is searched (not a whole year); a day of margin keeps edge contacts.
        val from = midnight(gridFirst.plus(DatePeriod(days = -1)), i)
        val to = midnight(last.plus(DatePeriod(days = 2)), i)
        val eclipses = ecl.global(from, to).mapNotNull { ecl.local(it, i.place, i.calc) }
        return MonthBundle(days, pan, fests, filtered.associateBy { it.id }, eclipses, recurringIds)
    }

    @Suppress("SwallowedException")
    // The calendar days and the festivals have a large fixed cost per call (a year of sankrantis, +-40 days of
    // lunations), so they are computed once for the calendar quarter around the grid (8 days of margin each side)
    // and sliced per month. Results are identical to a direct call over the grid (tests compare them).
    // Not thread-safe, like the calculators: the caller serialises.
    private inner class Block(val first: LocalDate, val count: Int, val i: CalInputs) {
        val days: List<app.parivaar.panchang.engine.calendar.CalendarDay> by lazy {
            cal.days(first.plus(DatePeriod(days = -1)), count + 2, i.place, i.calc, i.offsetFor)
        }
        private val fests = HashMap<app.parivaar.panchang.engine.calendar.Tradition, List<FestivalOccurrence>>()

        fun calendar(gridFirst: LocalDate, n: Int): List<app.parivaar.panchang.engine.calendar.CalendarDay> {
            val from = (gridFirst.toEpochDays() - first.toEpochDays()).toInt()
            return days.subList(from, from + n + 2)
        }

        fun festivals(t: app.parivaar.panchang.engine.calendar.Tradition): List<FestivalOccurrence> =
            fests.getOrPut(t) {
                val last = first.plus(DatePeriod(days = count - 1))
                fest.occurrences(defs.forTradition(t), first, last, i.place, i.calc, i.offsetFor)
            }
    }

    private data class BlockKey(
        val place: app.parivaar.panchang.engine.Place, val calc: app.parivaar.panchang.engine.CalcSettings,
        val zoneId: String, val first: LocalDate, val offsetFirst: Int, val offsetLast: Int,
    )

    private val blocks = object : LinkedHashMap<BlockKey, Block>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BlockKey, Block>?): Boolean = size > BLOCKS
    }

    private fun blockOf(gridFirst: LocalDate, i: CalInputs): Block {
        val mid = gridFirst.plus(DatePeriod(days = MID))
        val qStart = LocalDate(mid.year, (mid.month.ordinal / QUARTER) * QUARTER + 1, 1)
        val first = qStart.plus(DatePeriod(days = -MARGIN))
        val last = qStart.plus(DatePeriod(months = QUARTER)).plus(DatePeriod(days = MARGIN - 1))
        val count = last.toEpochDays() - first.toEpochDays() + 1
        val key = BlockKey(i.place, i.calc, i.zone.id, first, i.offsetFor(first), i.offsetFor(last))
        return blocks.getOrPut(key) { Block(first, count.toInt(), i) }
    }

    // One call for the grid; a day without a sunrise or sunset falls back to per-day calls and becomes null.
    @Suppress("SwallowedException")
    private fun panchangDays(first: LocalDate, n: Int, i: CalInputs): List<PanchangDay?> =
        try {
            calc.days(first, n, i.place, i.calc, i.offsetFor)
        } catch (e: PanchangUnavailableException) {
            (0 until n).map { k ->
                try {
                    calc.days(first.plus(DatePeriod(days = k)), 1, i.place, i.calc, i.offsetFor).single()
                } catch (e2: PanchangUnavailableException) {
                    null
                }
            }
        }

    override fun festivalsYear(year: Int, i: CalInputs): List<FestivalOccurrence> =
        fest.year(defs.forTradition(i.ekadashi), year, i.place, i.calc, i.offsetFor)

    override fun eclipsesYear(year: Int, i: CalInputs): List<GlobalWithLocal> =
        ecl.global(midnight(LocalDate(year, 1, 1), i), midnight(LocalDate(year + 1, 1, 1), i))
            .map { GlobalWithLocal(it, ecl.local(it, i.place, i.calc)) }

    override fun transits(from: LocalDate, to: LocalDate, i: CalInputs): List<TransitEvent> {
        val start = midnight(from, i)
        val end = midnight(to.plus(DatePeriod(days = 1)), i)
        val changes = Graha.entries.filter { it != Graha.MOON }.flatMap { g ->
            gochar.ingresses(g, start, end, i.calc).map { TransitEvent.SignChange(g, it.into, it.at, it.retrograde) }
        }
        val stations = STATION_GRAHAS.flatMap { g -> stations(g, from, to, i) }
        return (changes + stations).sortedBy { e ->
            when (e) {
                is TransitEvent.SignChange -> e.at.epochSeconds
                is TransitEvent.Station -> noon(e.date, i).epochSeconds
            }
        }
    }

    // Day resolution: the retrograde flag of the local-noon chart flips between consecutive dates.
    private fun stations(g: Graha, from: LocalDate, to: LocalDate, i: CalInputs): List<TransitEvent> {
        val out = ArrayList<TransitEvent>()
        var previous: Boolean? = null
        var d = from
        while (d <= to) {
            val now = chart.compute(noon(d, i), i.place, i.calc).of(g).retrograde
            if (previous != null && previous != now) out.add(TransitEvent.Station(g, now, d))
            previous = now
            d = d.plus(DatePeriod(days = 1))
        }
        return out
    }

    override fun day(date: LocalDate, i: CalInputs): List<PanchangDay> =
        calc.days(date, 2, i.place, i.calc, i.offsetFor)

    private fun midnight(d: LocalDate, i: CalInputs): Instant =
        Instant.fromEpochSeconds(d.toEpochDays() * SECONDS_PER_DAY - i.offsetFor(d))

    private fun noon(d: LocalDate, i: CalInputs): Instant =
        Instant.fromEpochSeconds(midnight(d, i).epochSeconds + NOON)

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
        const val NOON = 43_200L
        const val BLOCKS = 4
        const val MID = 14
        const val QUARTER = 3
        const val MARGIN = 8
        val STATION_GRAHAS = listOf(Graha.MERCURY, Graha.VENUS, Graha.MARS, Graha.JUPITER, Graha.SATURN)
    }
}
