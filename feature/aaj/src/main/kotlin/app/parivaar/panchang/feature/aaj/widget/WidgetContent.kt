package app.parivaar.panchang.feature.aaj.widget

import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.time.Instant

/**
 * Pure content for widgets and the tile (M08 L1). The model types hold ids, indexes and instants only; the
 * binder resolves text from resources. The only free text is [WidgetModel.city], present only when the caller
 * passes `showCity = true` (AppSettings.showCityOnWidgets). No type here can carry a name, a profile or birth
 * data, and no function takes one.
 */
sealed interface WidgetModel {
    /** Null unless the user opted in to showing the city on widgets. */
    val city: String?

    data class Today(
        val vaar: Vaar,
        val tithi: LimbSpan,
        val nakshatra: LimbSpan,
        val sunrise: Instant,
        val sunset: Instant,
        val next: Window?,
        override val city: String?,
    ) : WidgetModel

    /** The next or active Rahu kaal. [active] means `start <= now`; the chronometer counts to [end] or [start]. */
    data class Rahu(val start: Instant, val end: Instant, val active: Boolean, override val city: String?) : WidgetModel

    data class Festival(val id: String, val date: LocalDate, val inDays: Int, override val city: String?) : WidgetModel

    /** Not enough data (no place, polar day, no festival in range): the binder shows a neutral prompt. */
    data class Unavailable(override val city: String? = null) : WidgetModel
}

object WidgetContent {
    const val FESTIVAL_HORIZON_DAYS = 60

    private val SHOWN_NEXT =
        setOf(
            WindowKind.RAHU_KAAL, WindowKind.YAMAGANDA, WindowKind.GULIKA, WindowKind.ABHIJIT,
            WindowKind.BRAHMA_MUHURTA, WindowKind.DURMUHURTA, WindowKind.VARJYAM, WindowKind.AMRIT_KAAL,
            WindowKind.BHADRA,
        )

    private fun cityOf(showCity: Boolean, city: String?): String? =
        if (showCity) city?.takeIf { it.isNotBlank() } else null

    /** The day whose `sunrise <= now < nextSunrise`. */
    private fun current(now: Instant, days: List<PanchangDay>): PanchangDay? =
        days.firstOrNull { it.sunrise <= now && now < it.nextSunrise }

    private fun nextWindow(now: Instant, days: List<PanchangDay>, kinds: Set<WindowKind>): Window? =
        days.asSequence().flatMap { it.windows }.filter { it.kind in kinds && it.end > now }.minWithOrNull(
            compareBy<Window> { it.start }.thenBy { it.end },
        )

    fun today(now: Instant, days: List<PanchangDay>, showCity: Boolean, city: String?): WidgetModel {
        val c = cityOf(showCity, city)
        val a = current(now, days)
        val tithi = a?.limbs?.get(LimbKind.TITHI)?.firstOrNull { it.start <= now && now < it.end }
        val nak = a?.limbs?.get(LimbKind.NAKSHATRA)?.firstOrNull { it.start <= now && now < it.end }
        return if (a == null || tithi == null || nak == null) {
            WidgetModel.Unavailable(c)
        } else {
            WidgetModel.Today(
                vaar = a.vaar, tithi = tithi, nakshatra = nak, sunrise = a.sunrise, sunset = a.sunset,
                next = nextWindow(now, days, SHOWN_NEXT), city = c,
            )
        }
    }

    fun rahu(now: Instant, days: List<PanchangDay>, showCity: Boolean, city: String?): WidgetModel {
        val w = nextWindow(now, days, setOf(WindowKind.RAHU_KAAL))
            ?: return WidgetModel.Unavailable(cityOf(showCity, city))
        return WidgetModel.Rahu(w.start, w.end, active = w.start <= now, city = cityOf(showCity, city))
    }

    /** [festivals] are occurrences sorted or not; [today] is the civil date at the place. */
    fun festival(today: LocalDate, festivals: List<FestivalOccurrence>, showCity: Boolean, city: String?): WidgetModel {
        val f = festivals.filter { it.date >= today && today.daysUntil(it.date) <= FESTIVAL_HORIZON_DAYS }
            .minWithOrNull(compareBy<FestivalOccurrence> { it.date }.thenBy { it.id })
            ?: return WidgetModel.Unavailable(cityOf(showCity, city))
        return WidgetModel.Festival(f.id, f.date, today.daysUntil(f.date), cityOf(showCity, city))
    }
}
