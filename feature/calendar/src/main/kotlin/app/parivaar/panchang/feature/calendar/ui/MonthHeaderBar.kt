@file:Suppress("MaxLineLength", "LongParameterList", "LongMethod", "MagicNumber")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
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
    val prev = stringResource(if (rtl) R.string.cal_glyph_next else R.string.cal_glyph_prev)
    val next = stringResource(if (rtl) R.string.cal_glyph_prev else R.string.cal_glyph_next)
    val prevDesc = stringResource(R.string.cal_prev_month)
    val nextDesc = stringResource(R.string.cal_next_month)
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val masaNames = header.masas.joinToString(" / ") { names.masa(it.masa) }
    val samvat = if (view == CalendarView.GUJARATI) {
        stringResource(R.string.cal_samvat_vikram, RegionalText.number(header.samvat.vikramKartikadi, view, locale))
    } else {
        stringResource(R.string.cal_samvat_shaka, RegionalText.number(header.samvat.shaka, view, locale))
    }
    Column(modifier.fillMaxWidth().padding(horizontal = PpSpace.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
            TextButton(onClick = onPrev, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget).widthIn(min = PpSpace.minTouchTarget).semantics { contentDescription = prevDesc }) { Text(prev) }
            TextButton(onClick = onPick, modifier = Modifier.weight(1f).heightIn(min = PpSpace.minTouchTarget)) {
                Text(monthTitle(header.ym, locale), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            TextButton(onClick = onNext, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget).widthIn(min = PpSpace.minTouchTarget).semantics { contentDescription = nextDesc }) { Text(next) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            Text(
                res.getString(R.string.cal_header_months, masaNames, samvat), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showToday) TextButton(onClick = onToday, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget)) { Text(stringResource(R.string.cal_today)) }
        }
        if (header.masaChanges.isNotEmpty()) {
            val fmt = java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
            val dates = header.masaChanges.joinToString { fmt.format(java.time.LocalDate.ofEpochDay(it.toEpochDays())) }
            Text(stringResource(R.string.cal_masa_change, dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        header.adhika?.let {
            Text(stringResource(R.string.cal_adhika_notice, names.masa(it.masa)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

