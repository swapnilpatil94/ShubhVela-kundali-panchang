package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.feature.aaj.PpPerf
import app.parivaar.panchang.feature.calendar.CalInputs
import app.parivaar.panchang.feature.calendar.CalendarEngine
import app.parivaar.panchang.feature.calendar.GlobalWithLocal
import app.parivaar.panchang.feature.calendar.MonthBundle
import app.parivaar.panchang.feature.calendar.TransitEvent
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import kotlinx.datetime.LocalDate

/**
 * [DirectCalendarEngine] under the app's single engine lock (the calculators are not thread-safe). [lock] is the
 * same monitor [EngineAccess] uses. Only durations are logged (PpPerf is debug/perf only).
 */
class EngineCalendarEngine(private val delegate: CalendarEngine, private val lock: Any) : CalendarEngine {
    override fun month(gridFirst: LocalDate, n: Int, i: CalInputs): MonthBundle =
        synchronized(lock) { PpPerf.time("calendar.month") { delegate.month(gridFirst, n, i) } }

    override fun festivalsYear(year: Int, i: CalInputs): List<FestivalOccurrence> =
        synchronized(lock) { delegate.festivalsYear(year, i) }

    override fun eclipsesYear(year: Int, i: CalInputs): List<GlobalWithLocal> =
        synchronized(lock) { delegate.eclipsesYear(year, i) }

    override fun transits(from: LocalDate, to: LocalDate, i: CalInputs): List<TransitEvent> =
        synchronized(lock) { delegate.transits(from, to, i) }

    override fun day(date: LocalDate, i: CalInputs): List<PanchangDay> = synchronized(lock) { delegate.day(date, i) }
}
