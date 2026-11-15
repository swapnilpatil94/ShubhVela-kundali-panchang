@file:Suppress("MaxLineLength", "LongParameterList", "LongMethod", "MagicNumber", "CyclomaticComplexMethod")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.feature.calendar.CellA11y
import app.parivaar.panchang.feature.calendar.DayCell
import app.parivaar.panchang.feature.calendar.R
import app.parivaar.panchang.feature.calendar.TithiMark
import java.util.Locale
import kotlinx.datetime.DayOfWeek

private const val MAX_DOTS = 2
private const val OUT_OF_MONTH_ALPHA = 0.5f

/**
 * One day: Gregorian date, tithi at sunrise (+ vriddhi/kshaya sign), up to two festival dots then "+N", and glyph
 * markers. Meaning is never colour alone: marks are glyphs and the cell has a full spoken description.
 * Touch target is at least 48 dp tall and grows with the font scale.
 */
@Composable
fun DayCellView(
    cell: DayCell,
    selected: Boolean,
    names: ResCalendarNames,
    view: CalendarView,
    uiLocale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val description = CellA11y.describe(cell, names)
    val openLabel = stringResource(R.string.cal_cell_open)
    val selectedLabel = stringResource(R.string.cal_cell_selected)
    val shape = PpShapes.chip
    val festival = cell.festivalIds.isNotEmpty()
    val border = when {
        cell.isToday -> BorderStroke(1.5.dp, r.gold)
        selected -> BorderStroke(1.5.dp, r.text2)
        festival -> BorderStroke(1.dp, r.gold.copy(alpha = 0.45f))
        else -> BorderStroke(1.dp, r.line)
    }
    var m = modifier
        .heightIn(min = PpSpace.minTouchTarget)
        .padding(1.dp)
        .alpha(if (cell.inMonth) 1f else OUT_OF_MONTH_ALPHA)
        .clip(shape)
        .background(r.lacquerTop.copy(alpha = if (r.isDark) 0.7f else 1f), shape)
    if (cell.isToday) m = m.background(r.gold.copy(alpha = 0.16f), shape)
    if (selected && !cell.isToday) m = m.background(r.text.copy(alpha = 0.08f), shape)
    val sunday = cell.date.dayOfWeek == DayOfWeek.SUNDAY
    Column(
        m.border(border, shape).clickable(onClickLabel = openLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = if (selected) "$description, $selectedLabel" else description
                this.selected = selected
            }
            .padding(vertical = PpSpace.xs, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val day = if (cell.regional != null) RegionalText.number(cell.date.day, view, uiLocale) else cell.date.day.toString()
        Text(
            day, style = PpType.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
            color = if (sunday) r.kumkum else r.text, textAlign = TextAlign.Center,
        )
        // Regional wall views show the paksha and tithi only; the standard grid adds the moon (D13: tithi midpoint, no engine call).
        if (cell.regional == null) MoonPhaseIcon(MoonPhase.tithiElongationDeg(cell.tithi), 13.dp, Modifier.padding(vertical = 1.dp))
        val tithiText = cell.regional?.let { RegionalText.cell(res, view, uiLocale, it) }
            ?: stringResource(if (cell.tithi <= 15) R.string.cal_cell_tithi_s else R.string.cal_cell_tithi_k, ((cell.tithi - 1) % 15) + 1)
        val mark = when (cell.tithiMark) {
            TithiMark.NONE -> ""
            TithiMark.VRIDDHI -> stringResource(R.string.cal_mark_glyph_vriddhi)
            TithiMark.KSHAYA_NEXT -> stringResource(R.string.cal_mark_glyph_kshaya)
        }
        Text(tithiText + mark, style = PpType.label, color = r.text3, textAlign = TextAlign.Center)
        if (cell.markers.isNotEmpty()) {
            Text(
                cell.markers.sortedBy { it.ordinal }.joinToString(" ") { names.markerGlyph(it) },
                style = PpType.label, color = r.gold, textAlign = TextAlign.Center,
            )
        }
        if (festival) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(minOf(MAX_DOTS, cell.festivalIds.size)) { Box(Modifier.size(6.dp).background(r.gold, CircleShape)) }
                val more = cell.festivalIds.size - MAX_DOTS
                if (more > 0) Text(stringResource(R.string.cal_dots_more, more), style = PpType.label, color = r.text2)
            }
        }
    }
}

/** An empty frame used while a grid has fewer cells (keeps row heights aligned). */
@Composable
internal fun CellSpacer(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth())
