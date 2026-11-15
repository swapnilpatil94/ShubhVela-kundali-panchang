package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.FestivalRule
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.pakshaOf
import app.parivaar.panchang.feature.aaj.civilDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

@Suppress("TooManyFunctions")
/** Pure: turns one engine [MonthBundle] into the grid. Every number shown comes from the engine's output. */
object MonthBuilder {
    const val DAYS_PER_WEEK = 7
    private const val TITHIS = 30
    private const val TITHIS_PER_PAKSHA = 15
    private const val KSHAYA_STEP = 2

    private val markerIds = mapOf(
        "ekadashi_smarta" to Marker.EKADASHI,
        "ekadashi_vaishnava" to Marker.EKADASHI,
        "purnima" to Marker.PURNIMA,
        "amavasya" to Marker.AMAVASYA,
        "sankranti" to Marker.SANKRANTI,
    )

    /** Days from the grid's first cell to the first of the month. */
    fun leadingDays(ym: YearMonth, firstDow: DayOfWeek): Int =
        Math.floorMod(ym.firstDay.dayOfWeek.ordinal - firstDow.ordinal, DAYS_PER_WEEK)

    fun gridFirst(ym: YearMonth, firstDow: DayOfWeek): LocalDate =
        ym.firstDay.plus(DatePeriod(days = -leadingDays(ym, firstDow)))

    /** 4..6 rows. */
    fun rows(ym: YearMonth, firstDow: DayOfWeek): Int =
        (leadingDays(ym, firstDow) + ym.numberOfDays + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK

    @Suppress("LongParameterList")
    fun build(
        ym: YearMonth,
        today: LocalDate,
        firstDow: DayOfWeek,
        i: CalInputs,
        b: MonthBundle,
        key: MonthKey,
        proof: ProofInfo,
    ): MonthUi.Content {
        val lead = leadingDays(ym, firstDow)
        val n = rows(ym, firstDow) * DAYS_PER_WEEK
        require(b.cal.size == n + 2 && b.pan.size == n) { "bundle does not match the grid" }
        val first = gridFirst(ym, firstDow)
        val regional = i.view != CalendarView.STANDARD
        val byDate = b.fests.filter { keep(it, b, i) }.groupBy { it.date }
        val eclipseDates = eclipseDates(b, i)
        val cells = (0 until n).map { idx ->
            val date = first.plus(DatePeriod(days = idx))
            val c = b.cal[idx + 1]
            val occs = byDate[date].orEmpty()
            DayCell(
                date = date,
                inMonth = date.year == ym.year && date.month == ym.month,
                isToday = date == today,
                tithi = c.tithiAtSunrise,
                tithiMark = mark(b.cal[idx].tithiAtSunrise, c.tithiAtSunrise, b.cal[idx + 2].tithiAtSunrise),
                nakshatra = nakshatra(b.pan[idx]),
                markers = markers(occs, date in eclipseDates),
                festivalIds = dots(occs, b),
                label = label(c, i, regional),
                regional = if (regional) regionalLabel(c, i.view) else null,
            )
        }
        val header = header(ym, lead, b, i, regional)
        return MonthUi.Content(key, header, cells.chunked(DAYS_PER_WEEK), firstDow, today, proof)
    }

    // Tradition filter: only the chosen Ekadashi tradition's occurrences stay.
    internal fun keep(o: FestivalOccurrence, b: MonthBundle, i: CalInputs): Boolean {
        val r = b.defs[o.id]?.rule
        return r !is FestivalRule.Ekadashi || r.tradition == i.ekadashi
    }

    private fun mark(prev: Int, t: Int, next: Int): TithiMark = when {
        t == prev -> TithiMark.VRIDDHI
        Math.floorMod(next - t, TITHIS) == KSHAYA_STEP -> TithiMark.KSHAYA_NEXT
        else -> TithiMark.NONE
    }

    private fun nakshatra(d: PanchangDay?): Int =
        d?.limbs?.get(LimbKind.NAKSHATRA)?.firstOrNull { d.sunrise in it }?.index ?: -1

    private fun markers(occs: List<FestivalOccurrence>, eclipse: Boolean): Set<Marker> =
        buildSet {
            occs.forEach { o -> markerIds[o.id]?.let(::add) }
            if (eclipse) add(Marker.ECLIPSE)
        }

    // Vrats first, then pan-India before recurring, then id. Marker ids are not dots.
    private fun dots(occs: List<FestivalOccurrence>, b: MonthBundle): List<String> =
        occs.map { it.id }.filter { it !in markerIds }.distinct().sortedWith(
            compareBy<String>(
                { if (b.defs[it]?.vratRules.isNullOrEmpty()) 1 else 0 },
                { if (it in b.recurringIds) 1 else 0 },
                { it },
            ),
        )

    private fun eclipseDates(b: MonthBundle, i: CalInputs): Set<LocalDate> =
        b.eclipses.mapNotNull { eclipseDate(it, i) }.toSet()

    /** The local date of the first visible moment, or null when the eclipse is not visible here. */
    internal fun eclipseDate(e: app.parivaar.panchang.engine.calendar.LocalEclipse, i: CalInputs): LocalDate? {
        val start = e.visible?.start ?: return null
        val guess = civilDate(start, i.offsetFor(civilDate(start, 0)))
        return civilDate(start, i.offsetFor(guess))
    }

    private fun label(c: CalendarDay, i: CalInputs, regional: Boolean): LunarLabel =
        if (regional || i.monthSystem == MonthSystem.AMANTA) c.amanta else c.purnimanta

    private fun regionalLabel(c: CalendarDay, view: CalendarView): RegionalLabel {
        val t = c.tithiAtSunrise
        val inPaksha = (t - 1) % TITHIS_PER_PAKSHA + 1
        return when (view) {
            CalendarView.MARATHI -> RegionalLabel(pakshaOf(t), inPaksha, c.samvat.shaka, SamvatKind.SHAKA)
            else -> RegionalLabel(pakshaOf(t), inPaksha, c.samvat.vikramKartikadi, SamvatKind.VIKRAM_KARTIKADI)
        }
    }

    private fun header(ym: YearMonth, lead: Int, b: MonthBundle, i: CalInputs, regional: Boolean): MonthHeader {
        val days = (0 until ym.numberOfDays).map { b.cal[lead + 1 + it] }
        val labels = days.map { label(it, i, regional) }
        val previous = label(b.cal[lead], i, regional)
        val changes = labels.indices.filter { labels[it] != (if (it == 0) previous else labels[it - 1]) }
            .map { ym.firstDay.plus(DatePeriod(days = it)) }
        val masas = labels.distinct()
        return MonthHeader(ym, masas, days.first().samvat, changes, masas.firstOrNull { it.adhika })
    }
}
