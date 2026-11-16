package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.LocalEclipse
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Cache key of one computed day. In memory only. */
data class AajKey(val date: LocalDate, val placeId: RecordId, val offsetSeconds: Int, val settingsHash: Int)

/** What Aaj needs to compute. [placeId] is the home place id (or a session id for a travel place). */
data class AajInputs(
    val place: GeoPlace,
    val placeId: RecordId,
    val travel: Boolean,
    val settings: CalcSettings,
    val monthSystem: MonthSystem,
    val region: String,
    val ekadashi: Tradition = Tradition.SMARTA,
) {
    override fun toString(): String = "AajInputs(redacted)"
}

/** Ids of tables that have no data yet (shown as "Not available yet", never guessed). */
object UnavailableIds {
    const val VIJAYA = "VIJAYA"
    const val GODHULI = "GODHULI"
    const val DO_GHATI = "DO_GHATI"
    const val LAGNA = "LAGNA"
    val ALWAYS: Set<String> = setOf(VIJAYA, GODHULI, DO_GHATI, LAGNA)
}

sealed interface AajUi {
    data object Loading : AajUi
    data object NoPlace : AajUi
    data object NonexistentDate : AajUi

    /** The engine could not produce a day (for example a date outside the ephemeris range). */
    data object Failed : AajUi

    /** [days] are D-1, D, D+1. [festivals] and [eclipse]: `festivals == null` means still loading. */
    data class Content(
        val date: LocalDate,
        val days: List<PanchangDay>,
        val calendar: CalendarDay?, // null while the calendar facts are still loading
        val header: Header,
        val next: NextWindow?,
        val festivals: List<FestivalOccurrence>?,
        val eclipse: LocalEclipse?,
        val unavailable: Set<String>,
        val highLatitude: Set<DayFlag>,
        val proof: ProofInfo,
        val travel: Boolean,
        /** The place's IANA zone id: times are formatted per instant in it (DST-aware). Null: fixed day offset. */
        val zoneId: String? = null,
    ) : AajUi {
        /** The panchang day of [date]. [days] holds D-1, D, D+1 minus any neighbour that has no sunrise or sunset. */
        val day: PanchangDay get() = days.first { it.date == date }

        override fun toString(): String = "Content(redacted)"
    }

    /** The Sun does not rise or set: only limbs are shown, in the place's zone ([offsetSeconds] of [date]). */
    data class PolarDay(
        val date: LocalDate,
        val limbs: Map<LimbKind, List<LimbSpan>>,
        val proof: ProofInfo,
        val offsetSeconds: Int = 0,
        val zoneId: String? = null,
    ) : AajUi {
        override fun toString(): String = "PolarDay(redacted)"
    }
}

sealed interface Header {
    data class GoodUntil(val until: Instant) : Header
    data class WaitUntil(val kind: WindowKind, val end: Instant, val nextGood: Instant?) : Header
    data class NextGood(val start: Instant, val end: Instant) : Header
    data class TomorrowFrom(val start: Instant?) : Header
    data object Unavailable : Header
}

data class NextWindow(val window: Window, val startsIn: Duration, val active: Boolean)
