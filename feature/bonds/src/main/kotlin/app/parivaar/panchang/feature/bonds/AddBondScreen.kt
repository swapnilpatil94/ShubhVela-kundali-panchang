package app.parivaar.panchang.feature.bonds

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.SectionEyebrow
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.model.bond.BondTraits

/** Add a bond from a family profile (traits computed on this phone) or a pasted Bond Code or link. */
@Composable
fun AddBondScreen(state: BondsState, onEvent: (BondsEvent) -> Unit, onBack: () -> Unit) {
    val r = PpTheme.ratna
    BondsPage(stringResource(R.string.bonds_add_title), onBack) {
        KindPicker(state.kind) { onEvent(BondsEvent.Kind(it)) }
        state.addError?.let { Text(stringResource(errorText(it)), style = PpType.body, color = r.kumkum) }
        SectionEyebrow(stringResource(R.string.bonds_add_by_code))
        OutlinedTextField(
            value = state.code, onValueChange = { onEvent(BondsEvent.Code(it)) },
            label = { Text(stringResource(R.string.bonds_code_hint)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        PpButton(
            stringResource(R.string.bonds_code_check), { onEvent(BondsEvent.PreviewCode) }, PpButtonStyle.Primary,
            enabled = !state.busy && state.code.isNotBlank(), modifier = Modifier.fillMaxWidth(),
        )
        SectionEyebrow(stringResource(R.string.bonds_add_from_family))
        if (state.profiles.isEmpty()) {
            Text(stringResource(R.string.bonds_add_family_empty), style = PpType.meta, color = r.text2)
        }
        state.profiles.forEach { p ->
            Lacquer(Modifier.fillMaxWidth(), onClick = { if (!state.busy) onEvent(BondsEvent.FromProfile(p.id)) }) {
                Text(p.name, style = PpType.body, color = r.text)
            }
        }
    }
}

internal fun errorText(e: AddError): Int = when (e) {
    AddError.INVALID_CODE -> R.string.bonds_err_invalid
    AddError.NOT_COMPUTABLE -> R.string.bonds_err_not_computable
    AddError.SAVE_FAILED -> R.string.bonds_err_save
    AddError.UNAVAILABLE -> R.string.bonds_err_unavailable
}

/** "Add bond?": every field is shown; nothing is saved until the user taps Add bond. */
@Composable
fun PreviewScreen(p: PreviewUi, busy: Boolean, error: AddError?, onEvent: (BondsEvent) -> Unit, onBack: () -> Unit) {
    BondsPage(stringResource(R.string.bonds_preview_title), onBack) {
        Text(stringResource(R.string.bonds_preview_body), style = PpType.body, color = PpTheme.ratna.text2)
        error?.let { Text(stringResource(errorText(it)), style = PpType.body, color = PpTheme.ratna.kumkum) }
        Lacquer(Modifier.fillMaxWidth(), mergeSemantics = false) { TraitFields(p.traits) }
        KindPicker(p.kind) { onEvent(BondsEvent.Kind(it)) }
        PpButton(
            stringResource(R.string.bonds_preview_add), { onEvent(BondsEvent.ConfirmAdd) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        PpButton(stringResource(R.string.bonds_preview_cancel), onBack, PpButtonStyle.Outline,
            modifier = Modifier.fillMaxWidth())
    }
}

/** The derived traits only (never a date, time or place). */
@Composable
internal fun TraitFields(t: BondTraits) {
    val naks = stringArrayResource(R.array.bonds_nakshatra_names)
    val rashi = stringArrayResource(R.array.bonds_rashi_names)
    val vashya = stringArrayResource(R.array.bonds_vashya_names)
    val unknown = stringResource(R.string.bonds_field_not_shared)
    FieldRow(stringResource(R.string.bonds_field_name), t.name)
    FieldRow(stringResource(R.string.bonds_field_moon), stringResource(R.string.bonds_field_moon_value,
        naks[t.moonNakshatra], t.moonPada))
    FieldRow(stringResource(R.string.bonds_field_rashi), rashi[t.moonRashi])
    FieldRow(stringResource(R.string.bonds_field_lagna), t.lagna?.let { rashi[it] } ?: unknown)
    FieldRow(stringResource(R.string.bonds_field_mars), stringResource(BondTexts.mars(t.manglik)))
    FieldRow(stringResource(R.string.bonds_field_venus), rashi[t.venusSign])
    FieldRow(stringResource(R.string.bonds_field_sun), rashi[t.sunSign])
    FieldRow(
        stringResource(R.string.bonds_field_vashya),
        t.vashya?.let { vashya[it] } ?: stringResource(R.string.bonds_field_unknown),
    )
}
