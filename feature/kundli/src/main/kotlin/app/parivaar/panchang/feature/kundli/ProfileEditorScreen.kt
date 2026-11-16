package app.parivaar.panchang.feature.kundli

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.geo.OffsetPickerModel
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.profile.Relation

private fun relationText(r: Relation): Int = when (r) {
    Relation.SELF -> R.string.rel_self
    Relation.SPOUSE -> R.string.rel_spouse
    Relation.PARENT -> R.string.rel_parent
    Relation.CHILD -> R.string.rel_child
    Relation.SIBLING -> R.string.rel_sibling
    Relation.GRANDPARENT -> R.string.rel_grandparent
    Relation.GRANDCHILD -> R.string.rel_grandchild
    Relation.RELATIVE -> R.string.rel_relative
    Relation.FRIEND -> R.string.rel_friend
    Relation.OTHER -> R.string.rel_other
}

private fun sourceText(s: OffsetSource): Int = when (s) {
    OffsetSource.IANA -> R.string.editor_src_iana
    OffsetSource.OVERLAY -> R.string.editor_src_overlay
    OffsetSource.LMT -> R.string.editor_src_lmt
    OffsetSource.MANUAL -> R.string.editor_src_manual
}

/** Add/edit profile (B4): place search, offset and its source, DST fold picker, unknown time, passing date, delete. */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun ProfileEditorScreen(
    vm: ProfileEditorViewModel, s: EditorState, onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    val isSelf = s.relation == Relation.SELF && s.editingId != null
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PpSpace.screen),
        verticalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        Text(
            stringResource(if (s.editingId == null) R.string.editor_title_add else R.string.editor_title_edit),
            style = PpType.displayM, color = r.text, modifier = Modifier.semantics { heading() },
        )
        Lacquer(mergeSemantics = false) {
            OutlinedTextField(
                s.name, vm::onName, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true, isError = EditorError.NAME in s.errors,
            )
            if (!isSelf) {
                Text(
                    stringResource(R.string.editor_relation), style = PpType.meta, color = r.text2,
                    modifier = Modifier.padding(top = PpSpace.s),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                    Relation.entries.filter { it != Relation.SELF }.forEach { rel ->
                        FilterChip(
                            s.relation == rel, { vm.onRelation(rel) }, { Text(stringResource(relationText(rel))) },
                        )
                    }
                }
            }
        }
        Lacquer(mergeSemantics = false) {
            OutlinedTextField(
                s.dateText,
                vm::onDate,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_birth_date)) },
                placeholder = { Text(stringResource(R.string.editor_date_hint)) },
                singleLine = true,
                isError = EditorError.DATE in s.errors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { vm.onTimeUnknown(!s.timeUnknown) },
            ) {
                Checkbox(s.timeUnknown, vm::onTimeUnknown)
                Text(stringResource(R.string.editor_time_unknown), style = PpType.body, color = r.text)
            }
            if (!s.timeUnknown) {
                OutlinedTextField(
                    s.timeText,
                    vm::onTime,
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.editor_birth_time)) },
                    placeholder = { Text(stringResource(R.string.editor_time_hint)) },
                    singleLine = true,
                    isError = EditorError.TIME in s.errors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            } else {
                Text(stringResource(R.string.editor_noon_note), style = PpType.meta, color = r.text2)
            }
        }
        Lacquer(mergeSemantics = false) {
            OutlinedTextField(
                s.query, vm::onQuery, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_birth_place)) }, singleLine = true,
                isError = EditorError.PLACE in s.errors,
            )
            for (h in s.hits) {
                val line = listOfNotNull(h.place.name, h.place.admin1, h.place.countryCode).joinToString(", ")
                Text(
                    line, style = PpType.body, color = r.text,
                    modifier = Modifier.fillMaxWidth().clickable { vm.onPick(h.place) }.padding(vertical = PpSpace.s10),
                )
            }
            s.resolved?.let { v ->
                Text(
                    stringResource(
                        R.string.editor_offset_line, OffsetPickerModel.format(v.offset.offsetSeconds),
                        stringResource(sourceText(v.offset.source)),
                    ),
                    style = PpType.body, color = r.text, modifier = Modifier.padding(top = PpSpace.s),
                )
                if (OffsetFlag.HISTORICAL_VERIFY in v.offset.flags) Text(
                    stringResource(R.string.kundli_note_historical), style = PpType.meta, color = r.text2,
                )
                if (OffsetFlag.PRE_STANDARD_LMT in v.offset.flags) Text(
                    stringResource(R.string.kundli_note_lmt), style = PpType.meta, color = r.text2,
                )
            }
            if (s.gap) Text(stringResource(R.string.editor_gap_time), style = PpType.body, color = r.kumkum)
            if (s.ambiguous) {
                val e = s.resolved?.earlierSeconds
                val l = s.resolved?.laterSeconds
                Text(stringResource(R.string.editor_ambiguous), style = PpType.meta, color = r.text2)
                Row(horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                    FilterChip(
                        s.ambiguity == AmbiguityChoice.EARLIER, { vm.onAmbiguity(AmbiguityChoice.EARLIER) },
                        { Text(stringResource(R.string.editor_earlier, e?.let(OffsetPickerModel::format) ?: "")) },
                    )
                    FilterChip(
                        s.ambiguity == AmbiguityChoice.LATER, { vm.onAmbiguity(AmbiguityChoice.LATER) },
                        { Text(stringResource(R.string.editor_later, l?.let(OffsetPickerModel::format) ?: "")) },
                    )
                }
            }
            OutlinedTextField(
                s.manualText,
                vm::onManual,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_manual_offset)) },
                singleLine = true, isError = EditorError.OFFSET in s.errors,
            )
        }
        if (s.relation != Relation.SELF) {
            Lacquer(mergeSemantics = false) {
                OutlinedTextField(
                    s.passingText,
                    vm::onPassing,
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.editor_passing)) },
                    placeholder = { Text(stringResource(R.string.editor_date_hint)) },
                    singleLine = true,
                    isError = EditorError.PASSING in s.errors,
                )
                Text(stringResource(R.string.editor_passing_note), style = PpType.meta, color = r.text2)
            }
        }
        Lacquer(mergeSemantics = false) {
            Text(stringResource(R.string.editor_family_dates), style = PpType.eyebrow, color = r.text3)
            PrefRow(stringResource(R.string.family_kind_tithi), s.prefs.tithi) { vm.onPrefs(s.prefs.copy(tithi = it)) }
            PrefRow(
                stringResource(R.string.family_kind_nakshatra), s.prefs.nakshatra,
            ) { vm.onPrefs(s.prefs.copy(nakshatra = it)) }
            if (s.relation != Relation.SELF) PrefRow(
                stringResource(R.string.family_kind_shraddha), s.prefs.shraddha,
            ) { vm.onPrefs(s.prefs.copy(shraddha = it)) }
        }
        if (EditorError.SAVE_FAILED in s.errors) Text(
            stringResource(R.string.editor_save_failed), color = r.kumkum, style = PpType.body,
        )
        PpButton(
            stringResource(R.string.editor_save), vm::save, enabled = s.canSave, modifier = Modifier.fillMaxWidth(),
        )
        PpButton(
            stringResource(R.string.editor_cancel), onClose, PpButtonStyle.Outline, modifier = Modifier.fillMaxWidth(),
        )
        if (s.editingId != null && !isSelf) {
            PpButton(
                stringResource(R.string.editor_delete), { vm.askDelete(true) }, PpButtonStyle.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.parivaar_privacy), style = PpType.meta, color = r.text3)
        Spacer(Modifier.height(BOTTOM_INSET))
    }
    if (s.confirmDelete) {
        AlertDialog(
            onDismissRequest = { vm.askDelete(false) },
            confirmButton = { TextButton(vm::delete) { Text(stringResource(R.string.editor_delete)) } },
            dismissButton = { TextButton({ vm.askDelete(false) }) { Text(stringResource(R.string.editor_cancel)) } },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            text = { Text(stringResource(R.string.editor_delete_body)) },
        )
    }
}

@Composable
private fun PrefRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = PpSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PpType.body, color = PpTheme.ratna.text, modifier = Modifier.weight(1f))
        Switch(on, onChange)
    }
}
