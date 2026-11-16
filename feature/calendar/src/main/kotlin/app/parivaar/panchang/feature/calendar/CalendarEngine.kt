package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.GlobalEclipse
import app.parivaar.panchang.engine.calendar.LocalEclipse
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.panchang.PanchangDay
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * What the calendar needs to compute. [offsetFor] gives the UTC offset (seconds) of a civil date at the place and
 * throws [NonexistentCivilDayException] for a civil day that does not exist. Equality ignores [offsetFor]: it is
 * derived from [zone] and [placeId].
 */
class CalInputs(
    val place: Place,
    val placeId: RecordId,
    val zone: TimeZone,
    val offsetFor: (LocalDate) -> Int,
    val calc: CalcSettings,
    val monthSystem: MonthSystem,
    val ekadashi: Tradition,
    val view: CalendarView,
) {
    /** Calc, zone and place: an edited place under the same id is a new key. */

    val settingsHash: Int get() = listOf(calc, zone.id, place).hashCode()

    override fun equals(other: Any?): Boolean =
        other is CalInputs && placeId == other.placeId && place == other.place && zone == other.zone &&
            calc == other.calc && monthSystem == other.monthSystem && ekadashi == other.ekadashi && view == other.view

    override fun hashCode(): Int = listOf(placeId, place, zone, calc, monthSystem, ekadashi, view).hashCode()

    override fun toString(): String = "CalInputs(redacted)"
}

/** Thrown by [CalInputs.offsetFor] for a civil day that does not exist (a spring-forward gap). */
class NonexistentCivilDayException : RuntimeException("civil day does not exist")

/**
 * Engine output of one month grid. [cal] has n+2 days from gridFirst-1; [pan] has n entries, null for a day
 * the engine could not give (no sunrise or sunset): that cell keeps its tithi from [cal] and has no nakshatra.
 */
class MonthBundle(
    val cal: List<CalendarDay>,
    val pan: List<PanchangDay?>,
    val fests: List<FestivalOccurrence>,
    val defs: Map<String, FestivalDef>,
    val eclipses: List<LocalEclipse>,
    /** Ids of the recurring file, which sort after the pan-India ones. */
    val recurringIds: Set<String> = emptySet(),
) {
    override fun toString(): String = "MonthBundle(redacted)"
}

data class GlobalWithLocal(val global: GlobalEclipse, val local: LocalEclipse?) {
    override fun toString(): String = "GlobalWithLocal(redacted)"
}

sealed interface TransitEvent {
    val graha: Graha

    data class SignChange(override val graha: Graha, val into: Rashi, val at: Instant, val retrograde: Boolean) :
        TransitEvent {
        override fun toString(): String = "SignChange(redacted)"
    }

    /** Retrograde station at day resolution (shown as "around <date>"). */
    data class Station(override val graha: Graha, val turnsRetrograde: Boolean, val date: LocalDate) : TransitEvent {
        override fun toString(): String = "Station(redacted)"
    }
}

/** Thin adapter over the engine: no math in callers. Calls must be serialised (calculators are not thread-safe). */
interface CalendarEngine {
    fun month(gridFirst: LocalDate, n: Int, i: CalInputs): MonthBundle
    fun festivalsYear(year: Int, i: CalInputs): List<FestivalOccurrence>
    fun eclipsesYear(year: Int, i: CalInputs): List<GlobalWithLocal>
    fun transits(from: LocalDate, to: LocalDate, i: CalInputs): List<TransitEvent>

    /** Panchang days D and D+1. */
    fun day(date: LocalDate, i: CalInputs): List<PanchangDay>
}

/** The Ekadashi tradition filter applied before any festival is computed (calendar, Aaj, reminders, widgets). */
fun List<FestivalDef>.forTradition(t: Tradition): List<FestivalDef> = filter {
    val r = it.rule
    r !is app.parivaar.panchang.engine.calendar.FestivalRule.Ekadashi || r.tradition == t
}
