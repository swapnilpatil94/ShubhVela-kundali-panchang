@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.Masa
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.feature.aaj.civilDate
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinDayOfWeek

class MonthBuilderTest {
    private val proof = ProofInfo("", "LAHIRI", "ST", null)
    private val sunday = WeekFields.of(Locale.US).firstDayOfWeek.toKotlinDayOfWeek()
    private val monday = WeekFields.of(Locale.UK).firstDayOfWeek.toKotlinDayOfWeek()

    private fun build(ym: YearMonth, i: CalInputs, b: MonthBundle, dow: DayOfWeek = sunday) =
        MonthBuilder.build(ym, Fx.today, dow, i, b, Fx.key(ym, i), proof)

    private fun real(ym: YearMonth, i: CalInputs, dow: DayOfWeek = sunday): Pair<MonthBundle, MonthUi.Content> {
        val first = MonthBuilder.gridFirst(ym, dow)
        val b = Real.engine.month(first, MonthBuilder.rows(ym, dow) * 7, i)
        return b to build(ym, i, b, dow)
    }

    @Test fun `grid sizes`() {
        assertEquals(DayOfWeek.SUNDAY, YearMonth(2026, 2).firstDay.dayOfWeek)
        assertEquals(4, MonthBuilder.rows(YearMonth(2026, 2), sunday))
        assertEquals(5, MonthBuilder.rows(YearMonth(2026, 2), monday))
        assertEquals(6, MonthBuilder.rows(YearMonth(2026, 8), sunday))
        val i = Fx.inputs()
        val c = build(YearMonth(2026, 2), i, Fx.synthetic(LocalDate(2026, 2, 1), 28))
        assertEquals(4, c.weeks.size)
        assertEquals(LocalDate(2026, 2, 1), c.weeks[0][0].date)
        assertTrue(c.weeks.flatten().all { it.inMonth })
        val m = build(YearMonth(2026, 2), i, Fx.synthetic(LocalDate(2026, 1, 26), 35), monday)
        assertEquals(LocalDate(2026, 1, 26), m.weeks[0][0].date)
        assertFalse(m.weeks[0][0].inMonth)
    }

    @Test fun `vriddhi and kshaya marks come from neighbouring sunrise tithis`() {
        val i = Fx.inputs()
        val ym = YearMonth(2026, 2)
        val first = LocalDate(2026, 2, 1)
        // cal index = cell + 1: cells see [4,5,5,6,...]: cell0 sees prev=4? build [5,5,6]: cell1 (t=5, prev 5) = VRIDDHI.
        val seq = listOf(5, 5, 6)
        val v = build(ym, i, Fx.synthetic(first, 28) { k -> seq.getOrElse(k) { (10 + k) % 30 + 1 } })
        val cells = v.weeks.flatten()
        assertEquals(TithiMark.VRIDDHI, cells[0].tithiMark) // cal[1]=5 == cal[0]=5
        assertEquals(TithiMark.NONE, cells[1].tithiMark)
        // [5,7]: tithi 6 begins and ends inside day 0.
        val k = build(ym, i, Fx.synthetic(first, 28) { j -> if (j == 1) 5 else if (j == 2) 7 else (10 + j) % 30 + 1 })
        assertEquals(TithiMark.KSHAYA_NEXT, k.weeks.flatten()[0].tithiMark)
        // wrap-around 29 -> 1 is a skip of tithi 30.
        val w = build(ym, i, Fx.synthetic(first, 28) { j -> if (j == 1) 29 else if (j == 2) 1 else (10 + j) % 30 + 1 })
        assertEquals(TithiMark.KSHAYA_NEXT, w.weeks.flatten()[0].tithiMark)
    }

    @Test fun `tithis and festival dates equal the engine for Pune and New York all of 2026`() {
        for ((place, zone) in listOf(Fx.pune to "Asia/Kolkata", Fx.newYork to "America/New_York")) {
            val i = Fx.inputs(place, zone)
            for (m in 1..12) {
                val ym = YearMonth(2026, m)
                val (b, c) = real(ym, i)
                val cells = c.weeks.flatten()
                val first = cells.first().date
                val direct = Real.cal.days(first, cells.size, place, i.calc, i.offsetFor)
                assertEquals(direct.map { it.tithiAtSunrise }, cells.map { it.tithi }, "tithi $zone $m")
                assertEquals(direct, b.cal.subList(1, b.cal.size - 1), "calendar days $zone $m")
                val defs = Real.defs.filter { d ->
                    val r = d.rule
                    r !is app.parivaar.panchang.engine.calendar.FestivalRule.Ekadashi || r.tradition == i.ekadashi
                }
                val occ = Real.fest.occurrences(defs, first, cells.last().date, place, i.calc, i.offsetFor)
                assertEquals(occ, b.fests, "festivals $zone $m")
                val byDate = cells.associateBy { it.date }
                for (o in occ.filter { it.id !in MARKERS }) assertTrue(o.id in byDate.getValue(o.date).festivalIds, "${o.id} $zone $m")
            }
        }
    }

    @Test fun `tradition filter keeps exactly one ekadashi id`() {
        val i = Fx.inputs(ekadashi = Tradition.SMARTA)
        val defs = Real.defs.associateBy { it.id }
        val first = LocalDate(2026, 11, 1)
        fun occ(id: String, d: Int) = FestivalOccurrence(id, first.plus(DatePeriod(days = d)), null, 11, null, null, null, null, emptySet())
        val b = Fx.synthetic(first, 35).let {
            MonthBundle(it.cal, it.pan, listOf(occ("ekadashi_smarta", 3), occ("ekadashi_vaishnava", 4)), defs, emptyList())
        }
        val c = build(YearMonth(2026, 11), i, b)
        val marked = c.weeks.flatten().filter { Marker.EKADASHI in it.markers }
        assertEquals(listOf(first.plus(DatePeriod(days = 3))), marked.map { it.date })
        val v = MonthBuilder.build(YearMonth(2026, 11), Fx.today, sunday, Fx.inputs(ekadashi = Tradition.VAISHNAVA), b, Fx.key(YearMonth(2026, 11), Fx.inputs(ekadashi = Tradition.VAISHNAVA)), proof)
        assertEquals(listOf(first.plus(DatePeriod(days = 4))), v.weeks.flatten().filter { Marker.EKADASHI in it.markers }.map { it.date })
    }

    @Test fun `marker ids are not dots and vrats come first`() {
        val i = Fx.inputs()
        val first = LocalDate(2026, 11, 1)
        val d = first.plus(DatePeriod(days = 2))
        fun occ(id: String) = FestivalOccurrence(id, d, null, null, null, null, null, null, emptySet())
        val defs = Real.defs.associateBy { it.id }
        val ids = listOf("purnima", "diwali", "sankashti_chaturthi", "ekadashi_smarta").filter { it in defs }
        val base = Fx.synthetic(first, 35)
        val b = MonthBundle(base.cal, base.pan, ids.map(::occ), defs, emptyList(), Real.defs.filter { it.id in setOf("purnima", "sankashti_chaturthi") }.map { it.id }.toSet())
        val cell = build(YearMonth(2026, 11), i, b).weeks.flatten().first { it.date == d }
        assertTrue("purnima" !in cell.festivalIds && "ekadashi_smarta" !in cell.festivalIds)
        assertTrue(Marker.PURNIMA in cell.markers && Marker.EKADASHI in cell.markers)
        assertEquals(cell.festivalIds.sortedWith(compareBy({ if (defs[it]?.vratRules.isNullOrEmpty()) 1 else 0 }, { if (it in b.recurringIds) 1 else 0 }, { it })), cell.festivalIds)
    }

    @Test fun `eclipse marker is on the local date of visible start`() {
        val i = Fx.inputs()
        var seen = 0
        for (m in 1..12) {
            val ym = YearMonth(2026, m)
            val (b, c) = real(ym, i)
            val expected = b.eclipses.mapNotNull { e -> e.visible?.let { civilDate(it.start, 19_800) } }.toSet()
            seen += expected.size
            val marked = c.weeks.flatten().filter { Marker.ECLIPSE in it.markers }.map { it.date }.toSet()
            assertEquals(expected, marked, "eclipse month $m")
        }
        assertTrue(seen > 0, "no visible eclipse found in 2026 for Pune")
    }

    @Test fun `gujarati samvat changes on Kartika Shukla 1 and marathi uses shaka`() {
        val ym = YearMonth(2026, 11)
        val g = real(ym, Fx.inputs(view = CalendarView.GUJARATI))
        val b = g.first
        val flip = b.cal.zipWithNext().first { (a, c) -> c.samvat.vikramKartikadi != a.samvat.vikramKartikadi }.second
        assertEquals(Masa.KARTIKA, flip.amanta.masa)
        assertTrue(flip.tithiAtSunrise <= 15)
        val cells = g.second.weeks.flatten().associateBy { it.date }
        assertEquals(flip.samvat.vikramKartikadi, cells.getValue(flip.date).regional!!.samvatYear)
        assertEquals(flip.samvat.vikramKartikadi - 1, cells.getValue(flip.date.plus(DatePeriod(days = -1))).regional!!.samvatYear)
        assertEquals(SamvatKind.VIKRAM_KARTIKADI, cells.getValue(flip.date).regional!!.kind)
        val m = real(ym, Fx.inputs(view = CalendarView.MARATHI)).second.weeks.flatten().first()
        assertEquals(SamvatKind.SHAKA, m.regional!!.kind)
        assertTrue(real(ym, Fx.inputs()).second.weeks.flatten().all { it.regional == null })
    }

    @Test fun `regional paksha and tithi in paksha`() {
        val first = LocalDate(2026, 11, 1)
        val b = Fx.synthetic(first, 35) { listOf(1, 15, 16, 30)[it % 4] }
        val c = build(YearMonth(2026, 11), Fx.inputs(view = CalendarView.MARATHI), b).weeks.flatten()
        val r = c.take(4).map { it.regional!! }
        assertEquals(listOf(Paksha.SHUKLA, Paksha.KRISHNA, Paksha.KRISHNA, Paksha.SHUKLA), r.map { it.paksha })
        assertEquals(listOf(15, 1, 15, 1), r.map { it.tithiInPaksha })
    }

    @Test fun `header lists masas in order with changes and adhika`() {
        val first = LocalDate(2026, 11, 1)
        val b = Fx.synthetic(first, 35).let { s ->
            MonthBundle(
                s.cal.mapIndexed { k, d -> if (k > 10) Fx.day(d.date, d.tithiAtSunrise, Masa.MARGASHIRSHA, adhika = true) else d },
                s.pan, s.fests, s.defs, s.eclipses,
            )
        }
        val h = build(YearMonth(2026, 11), Fx.inputs(), b).header
        assertEquals(listOf(Masa.KARTIKA, Masa.MARGASHIRSHA), h.masas.map { it.masa })
        assertEquals(listOf(LocalDate(2026, 11, 11)), h.masaChanges)
        assertEquals(Masa.MARGASHIRSHA, h.adhika?.masa)
        assertEquals(1948, h.samvat.shaka)
    }

    private companion object {
        val MARKERS = setOf("ekadashi_smarta", "ekadashi_vaishnava", "purnima", "amavasya", "sankranti")
    }
}
