@file:Suppress("MaxLineLength", "LongParameterList", "LongMethod", "MagicNumber")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.feature.calendar.MonthUi
import app.parivaar.panchang.feature.calendar.R
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.LocalDate

private const val SWIPE_PX = 80f
private const val STALE_ALPHA = 0.45f

/** Week-day names starting at the locale's first day. Mirrored by Compose in RTL; nothing is hard-coded left or right. */
@Composable
internal fun WeekdayRow(first: kotlinx.datetime.DayOfWeek, locale: Locale) {
    Row(Modifier.fillMaxWidth()) {
        for (k in 0 until 7) {
            val d = java.time.DayOfWeek.of((first.ordinal + k) % 7 + 1)
            Text(
                d.getDisplayName(TextStyle.SHORT, locale), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The month grid. A horizontal swipe changes the month (swipe direction follows the layout direction); while a month
 * loads, the previous grid stays visible but dimmed.
 */
@Composable
fun MonthGrid(
    content: MonthUi.Content,
    dimmed: Boolean,
    selected: LocalDate?,
    names: ResCalendarNames,
    view: CalendarView,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val title = remember(content.key.ym, locale) { monthTitle(content.key.ym, locale) }
    val grid = stringResource(R.string.cal_grid_desc, title)
    val updating = stringResource(R.string.cal_updating)
    Column(
        modifier
            .pointerInput(rtl) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        // Dragging towards the start edge shows the next month, in either direction of layout.
                        if (dx <= -SWIPE_PX) { if (rtl) onPrev() else onNext() }
                        if (dx >= SWIPE_PX) { if (rtl) onNext() else onPrev() }
                    },
                ) { _, amount -> dx += amount }
            }
            .semantics { contentDescription = grid; if (dimmed) stateDescription = updating }
            .padding(horizontal = PpSpace.xs),
    ) {
        WeekdayRow(content.firstDayOfWeek, locale)
        Column(Modifier.alpha(if (dimmed) STALE_ALPHA else 1f)) {
            content.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCellView(cell, cell.date == selected, names, view, locale, { onSelect(cell.date) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal fun monthTitle(ym: kotlinx.datetime.YearMonth, locale: Locale): String =
    java.time.YearMonth.of(ym.year, ym.month.ordinal + 1).let {
        java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale).format(it)
    }
