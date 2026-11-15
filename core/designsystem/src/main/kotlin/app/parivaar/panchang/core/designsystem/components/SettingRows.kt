package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType

/** Shared row body: title in text, supporting line in text2 (meta), 48 dp minimum, one merged target. */
@Composable
private fun RowBody(
    title: String,
    supporting: String?,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
    leading: @Composable () -> Unit = {},
) {
    val r = PpTheme.ratna
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = PpSpace.minTouchTarget).padding(vertical = PpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = r.text)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = r.text2)
        }
        trailing()
    }
}

/** A tappable settings row (navigates or opens). With [onClick] null it is a static label row. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val m = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    RowBody(title, supporting, m, trailing)
}

/** A labelled switch row, gold track when on (one toggleable target). */
@Composable
fun ToggleRow(
    title: String,
    supporting: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    RowBody(
        title, supporting, modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        trailing = {
            Switch(
                checked = checked, onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = r.goldFill, checkedThumbColor = r.onGoldFill, checkedBorderColor = r.goldFill,
                    uncheckedTrackColor = r.ink3, uncheckedThumbColor = r.text2, uncheckedBorderColor = r.text3,
                ),
            )
        },
    )
}

/** A radio row with a gold selected mark; label and explanation merge for TalkBack. */
@Composable
fun ChoiceRow(
    title: String,
    supporting: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    RowBody(
        title, supporting, modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        trailing = {},
        leading = {
            RadioButton(
                selected = selected, onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = r.gold, unselectedColor = r.text3),
            )
        },
    )
}

/** Eyebrow section label: 11 sp bold, upper-cased where the script has case. */
@Composable
fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.fillMaxWidth(), style = PpType.eyebrow, color = PpTheme.ratna.text2)
}
