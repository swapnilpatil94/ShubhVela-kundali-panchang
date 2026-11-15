package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Samvat
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.panchang.Paksha
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

enum class Marker { EKADASHI, PURNIMA, AMAVASYA, SANKRANTI, ECLIPSE }

enum class TithiMark { NONE, VRIDDHI, KSHAYA_NEXT }

enum class SamvatKind { SHAKA, VIKRAM_KARTIKADI }

/** Cache key of one month. In memory only. [settingsHash] covers the calculation settings and the time zone id. */
data class MonthKey(
    val ym: YearMonth,
    val placeId: RecordId,
    val settingsHash: Int,
    val monthSystem: MonthSystem,
    val ekadashi: Tradition,
    val view: CalendarView,
)

/** Marathi/Gujarati presentation of a day: paksha, tithi within the paksha (1..15) and the era year. */
data class RegionalLabel(val paksha: Paksha, val tithiInPaksha: Int, val samvatYear: Int, val kind: SamvatKind)

/** [tithi] 1..30; [nakshatra] 0..26 or -1 when the engine had none for the day. */
data class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val tithi: Int,
    val tithiMark: TithiMark,
    val nakshatra: Int,
    val markers: Set<Marker>,
    val festivalIds: List<String>,
    val label: LunarLabel,
    val regional: RegionalLabel?,
)

data class MonthHeader(
    val ym: YearMonth,
    val masas: List<LunarLabel>,
    val samvat: Samvat,
    val masaChanges: List<LocalDate>,
    val adhika: LunarLabel?,
)

sealed interface MonthUi {
    data object NoPlace : MonthUi

    /** The grid of [stale] stays visible, dimmed, while the month loads. */
    data class Loading(val stale: Content?) : MonthUi

    data class Error(val nonexistent: Boolean) : MonthUi

    data class Content(
        val key: MonthKey,
        val header: MonthHeader,
        val weeks: List<List<DayCell>>,
        val firstDayOfWeek: DayOfWeek,
        val today: LocalDate,
        val proof: ProofInfo,
    ) : MonthUi {
        override fun toString(): String = "Content(redacted)"
    }
}

/** Everything the day sheet shows, taken from the month already computed (no further engine call). */
class DayDetail(
    val date: LocalDate,
    val day: app.parivaar.panchang.engine.panchang.PanchangDay?,
    val cal: app.parivaar.panchang.engine.calendar.CalendarDay,
    val festivals: List<app.parivaar.panchang.engine.calendar.FestivalOccurrence>,
    val defs: Map<String, app.parivaar.panchang.engine.calendar.FestivalDef>,
    val eclipses: List<app.parivaar.panchang.engine.calendar.LocalEclipse>,
    val offsetSeconds: Int,
    val proof: ProofInfo,
) {
    override fun toString(): String = "DayDetail(redacted)"
}
