@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.calendar.Ayana
import app.parivaar.panchang.engine.calendar.CalendarCalculator
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.EclipseSearch
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.FestivalEngine
import app.parivaar.panchang.engine.calendar.FestivalRules
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.Masa
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Ritu
import app.parivaar.panchang.engine.calendar.Samvat
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.jyotish.ChartCalculator
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.panchang.PanchangCalculator
import app.parivaar.panchang.engine.panchang.PanchangRules
import java.io.File
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus

/** The real engine over the repository's rules (`pp.root.dir`). */
object Real {
    private val root = File(System.getProperty("pp.root.dir"))
    val eph = AstronomyEngineEphemeris()
    val panchang = PanchangCalculator(
        eph, PanchangRules.parse(File(root, "rules/durmuhurta.json").readText(), File(root, "rules/choghadiya.json").readText()),
    )
    val cal = CalendarCalculator(eph)
    val fest = FestivalEngine(eph)
    val ecl = EclipseSearch(eph)
    private val panIndia = FestivalRules.parse("pan_india.json", File(root, "rules/festivals/pan_india.json").readText())
    private val recurring = FestivalRules.parse("recurring.json", File(root, "rules/festivals/recurring.json").readText())
    val defs: List<FestivalDef> = panIndia.defs + recurring.defs
    fun newEngine() = DirectCalendarEngine(
        panchang, cal, fest, ecl, ChartCalculator(eph), Gochar(eph), defs, recurring.defs.map { it.id }.toSet(),
    )
    val engine = newEngine()
}

object Fx {
    val today = LocalDate(2026, 11, 15)
    val pune = Place(18.5204, 73.8567)
    val newYork = Place(40.7128, -74.0060)
    val id = RecordId("0".repeat(32))

    fun inputs(
        place: Place = pune,
        zoneId: String = "Asia/Kolkata",
        id: RecordId = Fx.id,
        ekadashi: Tradition = Tradition.SMARTA,
        view: CalendarView = CalendarView.STANDARD,
        system: MonthSystem = MonthSystem.AMANTA,
    ): CalInputs {
        val zone = TimeZone.of(zoneId)
        return CalInputs(
            place, id, zone, { d -> zone.offsetAt(d.atStartOfDayIn(zone)).totalSeconds }, CalcSettings(), system, ekadashi, view,
        )
    }

    fun key(ym: YearMonth, i: CalInputs) = MonthKey(ym, i.placeId, i.settingsHash, i.monthSystem, i.ekadashi, i.view)

    fun samvat(year: Int = 1948) = Samvat(year, 2083, 2083, 1, 1, 1433, 1201)

    fun day(date: LocalDate, tithi: Int, masa: Masa = Masa.KARTIKA, adhika: Boolean = false, sam: Samvat = samvat()) =
        CalendarDay(date, tithi, LunarLabel(masa, adhika, null), LunarLabel(masa, adhika, null), sam, Ritu.HEMANTA, Ayana.DAKSHINAYANA, emptyMap(), emptySet())

    /** A bundle with the given sunrise tithis (cycling through [tithis]) and no festivals; pan is all null. */
    fun synthetic(gridFirst: LocalDate, n: Int, tithis: (Int) -> Int = { (it % 30) + 1 }) = MonthBundle(
        cal = (0 until n + 2).map { day(gridFirst.plus(DatePeriod(days = it - 1)), tithis(it)) },
        pan = List(n) { null }, fests = emptyList(), defs = emptyMap(), eclipses = emptyList(),
    )
}
