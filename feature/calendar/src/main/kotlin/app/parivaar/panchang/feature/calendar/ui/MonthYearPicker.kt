@file:Suppress("MaxLineLength", "LongParameterList", "LongMethod", "MagicNumber")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.feature.calendar.R
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

/** Years offered on each side of today (product choice, D7). */
const val PICKER_YEARS = 50

/** Pick a month and a year, today's year +-50. Every row is at least 48 dp tall. */
@Composable
fun MonthYearPicker(
    current: YearMonth,
    todayYear: Int,
    locale: Locale,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(current.year) }
    var month by remember { mutableIntStateOf(current.month.ordinal) }
    val years = remember(todayYear) { (todayYear - PICKER_YEARS..todayYear + PICKER_YEARS).toList() }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { list.scrollToItem((years.indexOf(year) - 1).coerceAtLeast(0)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cal_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                Text(stringResource(R.string.cal_pick_year), style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.heightIn(max = 144.dp), state = list) {
                    items(years) { y ->
                        Text(
                            y.toString(),
                            Modifier.fillMaxWidth().heightIn(min = PpSpace.minTouchTarget).clickable { year = y }
                                .semantics { selected = y == year }.padding(PpSpace.s),
                            style = if (y == year) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            color = if (y == year) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(stringResource(R.string.cal_pick_month), style = MaterialTheme.typography.labelLarge)
                for (row in (0 until 12).chunked(3)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        row.forEach { m ->
                            TextButton(
                                onClick = { month = m },
                                modifier = Modifier.weight(1f).heightIn(min = PpSpace.minTouchTarget).semantics { selected = m == month },
                            ) {
                                val label = java.time.Month.of(m + 1).getDisplayName(TextStyle.SHORT, locale)
                                Text(label, color = if (m == month) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    style = if (m == month) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(YearMonth(year, Month(month + 1))) }, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget)) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget)) { Text(stringResource(android.R.string.cancel)) } },
    )
}
