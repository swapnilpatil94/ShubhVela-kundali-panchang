@file:Suppress("MaxLineLength", "MagicNumber")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.RatnaMoodScope
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.calendar.Ayana
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.Masa
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Ritu
import app.parivaar.panchang.engine.calendar.Samvat
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.feature.calendar.DayCell
import app.parivaar.panchang.feature.calendar.DayDetail
import app.parivaar.panchang.feature.calendar.Marker
import app.parivaar.panchang.feature.calendar.MonthHeader
import app.parivaar.panchang.feature.calendar.MonthKey
import app.parivaar.panchang.feature.calendar.MonthUi
import app.parivaar.panchang.feature.calendar.RegionalLabel
import app.parivaar.panchang.feature.calendar.SamvatKind
import app.parivaar.panchang.feature.calendar.TithiMark
import app.parivaar.panchang.core.designsystem.components.ProofInfo
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

/** Sample month for previews only (invented layout, not engine output). */
private val PREVIEW_TODAY = LocalDate(2026, 11, 15)
private val PREVIEW_FESTIVALS = mapOf(8 to "diwali_lakshmi_puja", 10 to "govardhan_puja", 11 to "bhai_dooj")
private val PREVIEW_VRAT = mapOf(5 to "ekadashi_smarta", 21 to "ekadashi_smarta", 24 to "purnima", 28 to "sankashti_chaturthi")

private fun previewContent(view: CalendarView, today: LocalDate, festivalToday: String?): MonthUi.Content {
    val first = LocalDate(2026, 11, 1)
    val samvat = Samvat(1948, 2083, 2083, 1, 1, 1433, 1201)
    val cells = (0 until 35).map { i ->
        val d = first.plus(DatePeriod(days = i))
        val inMonth = d.month == first.month
        val tithi = ((d.day + 6) % 30) + 1
        val ids = buildList {
            PREVIEW_FESTIVALS[d.day]?.takeIf { inMonth }?.let(::add)
            PREVIEW_VRAT[d.day]?.takeIf { inMonth }?.let(::add)
            if (d == today) festivalToday?.let(::add)
        }
        DayCell(
            d, inMonth, d == today, tithi, if (d.day == 9) TithiMark.VRIDDHI else TithiMark.NONE, -1,
            if (d.day == 24 && inMonth) setOf(Marker.PURNIMA) else emptySet(), ids, LunarLabel(Masa.KARTIKA, false, null),
            if (view == CalendarView.STANDARD) null
            else RegionalLabel(if (tithi <= 15) Paksha.SHUKLA else Paksha.KRISHNA, (tithi - 1) % 15 + 1, 1948, SamvatKind.SHAKA),
        )
    }
    return MonthUi.Content(
        MonthKey(YearMonth(2026, kotlinx.datetime.Month.NOVEMBER), RecordId("0".repeat(32)), 0, MonthSystem.AMANTA, Tradition.SMARTA, view),
        MonthHeader(YearMonth(2026, kotlinx.datetime.Month.NOVEMBER), listOf(LunarLabel(Masa.KARTIKA, false, null)), samvat, emptyList(), null),
        cells.chunked(7), DayOfWeek.SUNDAY, today, ProofInfo("preview", "preview", "preview", null),
    )
}

@Composable
private fun PreviewMonth(view: CalendarView, festivalToday: String?, modifier: Modifier = Modifier) {
    val actions = CalendarActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    CalendarContent(
        previewContent(view, PREVIEW_TODAY, festivalToday), null, null, Tradition.SMARTA, view, true, null, null,
        { emptyList() }, actions, {}, {}, {}, {}, {}, {}, modifier,
    )
}

/** Month grid in Night (dark) and Chandan (light), font 1.0 and 2.0 via the shared preview set. Today is a vrat-only day. */
@PpPreviews
@Composable
private fun MonthPreview() = PanchangTheme { PreviewMonth(CalendarView.STANDARD, null) }

@PpPreviews
@Composable
private fun MonthChandanPreview() = PanchangTheme(mood = RatnaMood.CHANDAN) { PreviewMonth(CalendarView.STANDARD, null) }

/** Festival day: the today hero switches to Utsav. */
@PpPreviews
@Composable
private fun MonthFestivalDayPreview() = PanchangTheme { PreviewMonth(CalendarView.STANDARD, "karwa_chauth") }

/** Marathi wall view in the Paper mood. */
@PpPreviews
@Composable
private fun MarathiPaperPreview() = PanchangTheme { PreviewMonth(CalendarView.MARATHI, null) }

@PpPreviews
@Composable
private fun GujaratiPaperPreview() = PanchangTheme { PreviewMonth(CalendarView.GUJARATI, null) }

/** The Paper mood on its own, as the regional views scope it. */
@PpPreviews
@Composable
private fun PaperScopePreview() = PanchangTheme { RatnaMoodScope(RatnaMood.PAPER) { PreviewMonth(CalendarView.MARATHI, null) } }

@PpPreviews
@Composable
private fun DaySheetPreview() = PanchangTheme {
    val s = Samvat(1948, 2083, 2083, 1, 1, 1433, 1201)
    val label = LunarLabel(Masa.KARTIKA, false, null)
    val cal = CalendarDay(PREVIEW_TODAY, 6, label, label, s, Ritu.HEMANTA, Ayana.DAKSHINAYANA, emptyMap(), emptySet())
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val names = ResCalendarNames(ctx.resources, ctx.packageName, java.util.Locale.ENGLISH)
    val detail = DayDetail(PREVIEW_TODAY, null, cal, emptyList(), emptyMap(), emptyList(), 19800, ProofInfo("preview", "preview", "preview", null))
    DaySheet(detail, names, java.util.Locale.ENGLISH, {}, {}, {}, {})
}
