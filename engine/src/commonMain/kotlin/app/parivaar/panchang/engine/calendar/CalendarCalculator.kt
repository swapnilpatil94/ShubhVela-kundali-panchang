package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.astro.positionOnly
import app.parivaar.panchang.engine.calendar.internal.DayLookup
import app.parivaar.panchang.engine.calendar.internal.MasaNaming
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.requireUtcOffset
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Calendar days (docs/specs/M04.md sections 3.1-3.3): tithi at sunrise, amanta and purnimanta labels, era
 * years, ritu, ayana and the four solar dates of the panchang day of a civil date. Results never hold the
 * place. Holds caches that never change a result: not thread-safe.
 */
class CalendarCalculator(eph: Ephemeris, time: TimeTables = TimeTables.DEFAULT) {
    val lunar: LunarCalendar = LunarCalendar(eph, time)
    val solar: SolarCalendar = SolarCalendar(lunar)

    /**
     * The calendar day of civil [date] at [place]; [utcOffsetSeconds] in [-43,200, 50,400] (else
     * [IllegalArgumentException]). Throws `PanchangUnavailableException` (NO_SUN_DAY) like M03.
     */
    fun day(date: LocalDate, utcOffsetSeconds: Int, place: Place, s: CalcSettings = CalcSettings()): CalendarDay {
        requireUtcOffset(utcOffsetSeconds)
        return days(date, 1, place, s) { utcOffsetSeconds }.single()
    }

    /** [count] consecutive days from [first]; [utcOffsetSeconds] gives each date's offset (range-checked). */
    fun days(
        first: LocalDate,
        count: Int,
        place: Place,
        s: CalcSettings = CalcSettings(),
        utcOffsetSeconds: (LocalDate) -> Int,
    ): List<CalendarDay> {
        require(count >= 0) { "count must not be negative" }
        if (count == 0) return emptyList()
        val lookup = DayLookup.of(lunar.riseSet, place, s, utcOffsetSeconds)
        val last = first.plus(DatePeriod(days = count - 1))
        val yearFirst = first.minus(YEAR_BACK)
        val sankrantis = solar.sankrantisFor(yearFirst, last, lookup, s)
        val monthFirst = first.minus(SolarCalendar.MONTH_BACK)
        val months = SolarRegion.entries.associateWith { solar.starts(it, monthFirst, last, sankrantis, lookup) }
        val years = SolarRegion.entries.associateWith {
            solar.starts(it, yearFirst, last, sankrantis, lookup, setOf(it.yearStart))
        }
        return (0 until count).map { i ->
            val date = first.plus(DatePeriod(days = i))
            fun yearOf(r: SolarRegion): Int =
                checkNotNull(years.getValue(r).lastOrNull { it.date <= date }) { "no solar year start found" }.date.year
            day(date, lookup, s, months.mapValues { (_, v) -> SolarCalendar.dateOf(date, v) }, ::yearOf)
        }
    }

    private fun day(
        date: LocalDate,
        lookup: DayLookup,
        s: CalcSettings,
        solarDates: Map<SolarRegion, SolarDate>,
        yearOf: (SolarRegion) -> Int,
    ): CalendarDay {
        val sun = lookup.sunDay(date)
        val tithi = lunar.limbs.spanAt(LimbKind.TITHI, sun.sunrise, s).index
        val l = lunar.lunationAt(sun.sunrise, s)
        val amanta = lunar.label(l, tithi, MonthSystem.AMANTA, s)
        val era = lunar.eraYears(l, s)
        val samvat = Samvat(
            shaka = era.shaka,
            vikramChaitradi = era.vikramChaitradi,
            vikramKartikadi = era.vikramKartikadi,
            samvatsara = era.samvatsara,
            samvatsaraTamil = MasaNaming.samvatsara(yearOf(SolarRegion.TAMIL) - SHAKA_OFFSET),
            bangabda = yearOf(SolarRegion.BENGALI) - BANGABDA_OFFSET,
            kollam = yearOf(SolarRegion.MALAYALAM) - KOLLAM_OFFSET,
        )
        return CalendarDay(
            date = date,
            tithiAtSunrise = tithi,
            amanta = amanta,
            purnimanta = lunar.label(l, tithi, MonthSystem.PURNIMANTA, s),
            samvat = samvat,
            ritu = Ritu.entries[amanta.masa.ordinal / MASAS_PER_RITU],
            ayana = ayanaAt(sun.sunrise, s),
            solar = solarDates,
            flags = sun.flags,
        )
    }

    /** UTTARAYANA iff the sidereal Sun is in [270, 360) or [0, 90) at [at]. */
    private fun ayanaAt(at: Instant, s: CalcSettings): Ayana {
        val tt = TimeScales.utcToTt(at, lunar.timeTables)
        val lon = Ayanamsa.siderealDeg(lunar.ephemeris.positionOnly(Body.SUN, tt).lonDeg, s.ayanamsa, tt)
        return if (lon >= UTTARAYANA_FROM || lon < UTTARAYANA_TO) Ayana.UTTARAYANA else Ayana.DAKSHINAYANA
    }

    private companion object {
        const val SHAKA_OFFSET = 78
        const val BANGABDA_OFFSET = 593
        const val KOLLAM_OFFSET = 824
        const val MASAS_PER_RITU = 2
        const val UTTARAYANA_FROM = 270.0
        const val UTTARAYANA_TO = 90.0

        /** Before the latest year start (Mesha or Simha month) of any date. */
        val YEAR_BACK = DatePeriod(days = 370)
    }
}
