@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName", "TooManyFunctions")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.runtime.Composable
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.Ayana
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.Masa
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Ritu
import app.parivaar.panchang.engine.calendar.Samvat
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.feature.calendar.CalInputs
import app.parivaar.panchang.feature.calendar.DayCell
import app.parivaar.panchang.feature.calendar.Marker
import app.parivaar.panchang.feature.calendar.MonthBuilder
import app.parivaar.panchang.feature.calendar.MonthBundle
import app.parivaar.panchang.feature.calendar.MonthKey
import app.parivaar.panchang.feature.calendar.MonthUi
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

// Preview-only fixtures (debug source set, never shipped). Fictional tithi sequence, not real panchang data.
private val YM = YearMonth(2026, 11)
private val TODAY = LocalDate(2026, 11, 15)

private fun content(view: CalendarView, dow: DayOfWeek = DayOfWeek.SUNDAY): MonthUi.Content {
    val i = CalInputs(
        Place(18.5204, 73.8567), RecordId("0".repeat(32)), TimeZone.of("Asia/Kolkata"), { 19_800 }, CalcSettings(),
        MonthSystem.AMANTA, Tradition.SMARTA, view,
    )
    val n = MonthBuilder.rows(YM, dow) * 7
    val first = MonthBuilder.gridFirst(YM, dow)
    val sam = Samvat(1948, 2083, 2083, 1, 1, 1433, 1201)
    val label = LunarLabel(Masa.KARTIKA, false, null)
    val cal = (0 until n + 2).map { k ->
        val t = if (k == 9) 8 else if (k == 10) 10 else (k * 7 / 6) % 30 + 1
        CalendarDay(first.plus(DatePeriod(days = k - 1)), t, label, label, sam, Ritu.HEMANTA, Ayana.DAKSHINAYANA, emptyMap(), emptySet())
    }
    val b = MonthBundle(cal, List(n) { null }, emptyList(), emptyMap(), emptyList())
    val key = MonthKey(YM, i.placeId, i.settingsHash, i.monthSystem, i.ekadashi, view)
    val built = MonthBuilder.build(YM, TODAY, dow, i, b, key, ProofInfo("", "LAHIRI", "CENTER_REFRACTED", 560.0))
    // Sprinkle markers and festival dots so every glyph shows.
    val weeks = built.weeks.map { w -> w.map { c -> decorate(c) } }
    return built.copy(weeks = weeks)
}

private fun decorate(c: DayCell): DayCell = when (c.date.day) {
    8 -> c.copy(markers = setOf(Marker.EKADASHI), festivalIds = listOf("dhanteras"))
    12 -> c.copy(markers = setOf(Marker.AMAVASYA), festivalIds = listOf("diwali_lakshmi_puja", "naraka_chaturdashi", "govardhan_puja"))
    16 -> c.copy(markers = setOf(Marker.SANKRANTI))
    24 -> c.copy(markers = setOf(Marker.PURNIMA, Marker.ECLIPSE))
    else -> c
}

@Composable
private fun Screen(ui: MonthUi, view: CalendarView = CalendarView.STANDARD) = PanchangTheme {
    val actions = CalendarActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    CalendarContent(
        ui, TODAY, null, Tradition.SMARTA, view, true, "Pune", "AB", { emptyList() }, actions,
        {}, {}, {}, {}, {}, {},
    )
}

@PpPreviews
@Composable
private fun MonthPreview() = Screen(content(CalendarView.STANDARD))

@PpPreviews
@Composable
private fun MarathiPreview() = Screen(content(CalendarView.MARATHI), CalendarView.MARATHI)

@PpPreviews
@Composable
private fun GujaratiPreview() = Screen(content(CalendarView.GUJARATI), CalendarView.GUJARATI)

@PpPreviews
@Composable
private fun MondayStartPreview() = Screen(content(CalendarView.STANDARD, DayOfWeek.MONDAY))

@PpPreviews
@Composable
private fun LoadingStalePreview() = Screen(MonthUi.Loading(content(CalendarView.STANDARD)))

@PpPreviews
@Composable
private fun NoPlacePreview() = Screen(MonthUi.NoPlace)

@PpPreviews
@Composable
private fun NonexistentPreview() = Screen(MonthUi.Error(nonexistent = true))
