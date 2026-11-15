@file:Suppress("MaxLineLength", "LongParameterList", "LongMethod")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.feature.calendar.R
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Searchable list of this year's festivals; picking one shows its month with the date selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FestivalJumpSheet(
    year: Int,
    names: ResCalendarNames,
    locale: Locale,
    load: suspend (Int) -> List<FestivalOccurrence>,
    onPick: (FestivalOccurrence) -> Unit,
    onDismiss: () -> Unit,
) {
    var items by remember { mutableStateOf<List<FestivalOccurrence>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(year) { items = load(year).filter { it.id !in MARKER_IDS } }
    val fmt = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = PpShapes.sheetTop, containerColor = PpTheme.ratna.ink3, tonalElevation = 0.dp) {
        Column(Modifier.padding(horizontal = PpSpace.l)) {
            Text(stringResource(R.string.cal_jump_festival), style = PpType.displayS, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text(stringResource(R.string.cal_jump_search)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = PpSpace.s),
            )
            val all = items
            if (all == null) {
                Text(stringResource(R.string.cal_jump_loading), style = MaterialTheme.typography.bodyLarge)
            } else {
                val shown = all.filter { query.isBlank() || names.festivalName(it.id).contains(query.trim(), ignoreCase = true) }
                if (shown.isEmpty()) Text(stringResource(R.string.cal_jump_none), style = MaterialTheme.typography.bodyLarge)
                LazyColumn {
                    items(shown, key = { it.id + it.date }) { o ->
                        Column(Modifier.fillMaxWidth().heightIn(min = PpSpace.minTouchTarget).clickable { onPick(o) }.padding(vertical = PpSpace.s)) {
                            Text(names.festivalName(o.id), style = MaterialTheme.typography.titleSmall)
                            Text(fmt.format(java.time.LocalDate.ofEpochDay(o.date.toEpochDays())), style = PpType.meta, color = PpTheme.ratna.text3)
                        }
                    }
                }
            }
        }
    }
}

private val MARKER_IDS = setOf("ekadashi_smarta", "ekadashi_vaishnava", "purnima", "amavasya", "sankranti")
