@file:Suppress("MatchingDeclarationName", "MaxLineLength")

package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

/** Primary = gold gradient, Outline = ghost, Tonal = ink pill. */
enum class PpButtonStyle { Primary, Outline, Tonal }

@Composable
fun PpButton(
    text: String,
    onClick: () -> Unit,
    style: PpButtonStyle = PpButtonStyle.Primary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    val m = modifier.heightIn(min = PpSpace.minTouchTarget)
    val label: @Composable () -> Unit = { Text(text, style = MaterialTheme.typography.labelLarge) }
    when (style) {
        PpButtonStyle.Primary -> {
            val fill = if (enabled) Brush.verticalGradient(r.goldGradient) else Brush.verticalGradient(listOf(r.ink3, r.ink3))
            Button(
                onClick = onClick, enabled = enabled, shape = PpShapes.pill,
                modifier = m.background(fill, PpShapes.pill)
                    .border(1.dp, Color.White.copy(alpha = if (enabled) 0.35f else 0f), PpShapes.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent, contentColor = r.onGoldFill,
                    disabledContainerColor = Color.Transparent, disabledContentColor = r.text3,
                ),
                content = { label() },
            )
        }
        PpButtonStyle.Outline -> OutlinedButton(
            onClick = onClick, modifier = m, enabled = enabled, shape = PpShapes.pill,
            border = BorderStroke(1.dp, if (enabled) r.text3 else r.line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = r.text, disabledContentColor = r.text3),
            content = { label() },
        )
        PpButtonStyle.Tonal -> Button(
            onClick = onClick, modifier = m, enabled = enabled, shape = PpShapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = r.ink3, contentColor = r.text, disabledContainerColor = r.ink3, disabledContentColor = r.text3,
            ),
            content = { label() },
        )
    }
}
