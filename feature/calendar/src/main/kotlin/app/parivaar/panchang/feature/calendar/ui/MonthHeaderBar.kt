@file:Suppress("MaxLineLength", "LongParameterList", "LongMethod", "MagicNumber")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.feature.calendar.MonthHeader
import app.parivaar.panchang.feature.calendar.R
import java.util.Locale

/**
 * Previous and next buttons around the month and year (tap to pick), the month names with the samvat, the
 * "Today" button, and the adhika notice. Arrow glyphs swap in RTL layouts.
 */
@Composable
fun MonthHeaderBar(
    header: MonthHeader,
    view: CalendarView,
    names: ResCalendarNames,
    locale: Locale,
    showToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val prevDesc = stringResource(R.string.cal_prev_month)
    val nextDesc = stringResource(R.string.cal_next_month)
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val masaNames = header.masas.joinToString(" / ") { names.masa(it.masa) }
    val samvat = if (view == CalendarView.GUJARATI) {
        stringResource(R.string.cal_samvat_vikram, RegionalText.number(header.samvat.vikramKartikadi, view, locale))
    } else {
        stringResource(R.string.cal_samvat_shaka, RegionalText.number(header.samvat.shaka, view, locale))
    }
    val r = PpTheme.ratna
    Column(modifier.fillMaxWidth().padding(start = PpSpace.s, end = PpSpace.s, top = PpSpace.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            Text(
                res.getString(R.string.cal_header_months, masaNames, samvat), Modifier.weight(1f).padding(start = PpSpace.s),
                style = PpType.eyebrow, color = r.text3,
            )
            if (showToday) PpButton(stringResource(R.string.cal_today), onToday, PpButtonStyle.Outline)
        }
        val wide = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.5f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
            ChevronButton(towardStart = !rtl, desc = prevDesc, onClick = onPrev)
            TextButton(onClick = onPick, modifier = Modifier.weight(1f).heightIn(min = PpSpace.minTouchTarget)) {
                // One line (ellipsis as a floor); wrapping is allowed only at large font scales.
                Text(
                    monthTitle(header.ym, locale), style = PpType.displayL, color = r.text, textAlign = TextAlign.Center,
                    maxLines = if (wide) 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
            }
            ChevronButton(towardStart = rtl, desc = nextDesc, onClick = onNext)
        }
        if (header.masaChanges.isNotEmpty()) {
            val fmt = java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
            val dates = header.masaChanges.joinToString { fmt.format(java.time.LocalDate.ofEpochDay(it.toEpochDays())) }
            Text(stringResource(R.string.cal_masa_change, dates), style = PpType.meta, color = r.text3)
        }
        header.adhika?.let {
            Text(stringResource(R.string.cal_adhika_notice, names.masa(it.masa)), style = PpType.meta, color = r.text2)
        }
    }
}


/** 48 dp touch target with a 24 dp chevron pointing to the start edge ([towardStart]) or the end edge. */
@Composable
private fun ChevronButton(towardStart: Boolean, desc: String, onClick: () -> Unit) {
    val color = PpTheme.ratna.text2
    Box(
        Modifier.size(PpSpace.minTouchTarget).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val x0 = if (towardStart) w * 0.62f else w * 0.38f
            val x1 = if (towardStart) w * 0.38f else w * 0.62f
            val path = Path().apply { moveTo(x0, h * 0.25f); lineTo(x1, h * 0.5f); lineTo(x0, h * 0.75f) }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
