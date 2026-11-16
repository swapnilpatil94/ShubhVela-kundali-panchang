package app.parivaar.panchang.shell

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parivaar.panchang.feature.kundli.KundliScreen
import app.parivaar.panchang.feature.kundli.ParivaarScreen
import app.parivaar.panchang.feature.kundli.ProfileEditorScreen

private const val MODE_CHART = "chart"
private const val MODE_PARIVAAR = "parivaar"
private const val MODE_EDIT = "edit"

/**
 * Kundli tab: chart screen, Parivaar and the profile editor, switched in place (the tab keeps the dock). System back
 * returns editor -> previous screen -> chart. The editing profile id is a random record id, never personal data.
 */
@Composable
fun KundliTab(w: KundliWiring) {
    var mode by rememberSaveable { mutableStateOf(MODE_CHART) }
    var back by rememberSaveable { mutableStateOf(MODE_CHART) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    val state by w.kundli.state.collectAsStateWithLifecycle()
    val family by w.parivaar.state.collectAsStateWithLifecycle()
    BackHandler(enabled = mode != MODE_CHART) { mode = if (mode == MODE_EDIT) back else MODE_CHART }
    val openEditor = { from: String, id: String? ->
        back = from
        editId = id
        mode = MODE_EDIT
    }
    when (mode) {
        MODE_PARIVAAR -> ParivaarScreen(family, { openEditor(MODE_PARIVAAR, null) }, { openEditor(MODE_PARIVAAR, it) })
        MODE_EDIT -> {
            val vm = remember(editId) { w.newEditor() }
            val es by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(vm) { vm.start(state.profiles.firstOrNull { it.id.hex == editId }) }
            LaunchedEffect(es.done) { if (es.done) mode = back }
            ProfileEditorScreen(vm, es, { mode = back })
        }
        else -> KundliScreen(
            state, w.kundli::onEvent,
            onAddProfile = { openEditor(MODE_CHART, null) },
            onEditProfile = { openEditor(MODE_CHART, it.id.hex) },
            onOpenParivaar = { mode = MODE_PARIVAAR },
        )
    }
}
