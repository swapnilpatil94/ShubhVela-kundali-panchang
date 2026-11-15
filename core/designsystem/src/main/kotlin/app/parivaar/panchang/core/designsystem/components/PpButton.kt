@file:Suppress("MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

enum class PpButtonStyle { Primary, Outline, Tonal }

@Composable
fun PpButton(
    text: String,
    onClick: () -> Unit,
    style: PpButtonStyle = PpButtonStyle.Primary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val m = modifier.heightIn(min = PpSpace.minTouchTarget)
    val label: @Composable () -> Unit = { Text(text, style = MaterialTheme.typography.labelLarge) }
    when (style) {
        PpButtonStyle.Primary -> Button(
            onClick = onClick, modifier = m, enabled = enabled, shape = PpShapes.pill,
            colors = ButtonDefaults.buttonColors(), content = { label() },
        )
        PpButtonStyle.Outline -> OutlinedButton(
            onClick = onClick, modifier = m, enabled = enabled, shape = PpShapes.pill,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PpTheme.colors.primaryText),
            content = { label() },
        )
        PpButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick, modifier = m, enabled = enabled, shape = PpShapes.pill,
            colors = ButtonDefaults.filledTonalButtonColors(), content = { label() },
        )
    }
}
