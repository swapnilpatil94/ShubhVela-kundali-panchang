package app.parivaar.panchang.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import app.parivaar.panchang.R
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.R as DsR

private val ENTRIES =
    listOf(
        Triple(Dest.Root(Tab.AAJ), DsR.string.tab_aaj, R.array.search_alias_aaj),
        Triple(Dest.Root(Tab.CALENDAR), DsR.string.tab_calendar, R.array.search_alias_calendar),
        Triple(Dest.Root(Tab.KUNDLI), DsR.string.tab_kundli, R.array.search_alias_kundli),
        Triple(Dest.Root(Tab.BONDS), DsR.string.tab_bonds, R.array.search_alias_bonds),
        Triple(Dest.Root(Tab.BHAKTI), DsR.string.tab_bhakti, R.array.search_alias_bhakti),
        Triple(Dest.Settings, R.string.search_label_settings, R.array.search_alias_settings),
        Triple(Dest.Conventions, R.string.search_label_conventions, R.array.search_alias_conventions),
        Triple(Dest.Privacy, R.string.search_label_privacy, R.array.search_alias_privacy),
    )

/** Full-screen palette over the five tabs, Settings, Conventions and Privacy. Localized label plus English aliases. */
@Composable
fun SearchPalette(onPick: (Dest) -> Unit, onDismiss: () -> Unit) {
    val entries =
        ENTRIES.map { (d, label, aliases) ->
            SearchEntry(d, listOf(stringResource(label)) + stringArrayResource(aliases))
        }
    val labelFor = entries.associate { it.dest to it.labels.first() }
    val index = remember(entries) { SearchIndex(entries) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, index) { index.query(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(PpSpace.l),
                verticalArrangement = Arrangement.spacedBy(PpSpace.m),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_hint)) },
                )
                if (query.isNotBlank() && results.isEmpty()) {
                    Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.bodyLarge)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(results) { e ->
                        Text(
                            labelFor.getValue(e.dest),
                            style = MaterialTheme.typography.titleMedium,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .defaultMinSize(minHeight = PpSpace.minTouchTarget)
                                    .clickable { onPick(e.dest) }
                                    .padding(vertical = PpSpace.m),
                        )
                    }
                }
                PpButton(stringResource(R.string.search_close), onDismiss, PpButtonStyle.Tonal)
            }
        }
    }
}
