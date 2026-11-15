@file:Suppress("MatchingDeclarationName", "MaxLineLength", "MagicNumber")

package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.parivaar.panchang.core.designsystem.surfaces.LocalTilt
import app.parivaar.panchang.core.designsystem.surfaces.SurfaceMath
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
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
            val tilt = LocalTilt.current
            val glow = Color(PpPalette.HALDI_DEEP).copy(alpha = 0.3f)
            Button(
                onClick = onClick, enabled = enabled, shape = PpShapes.pill,
                modifier = m
                    .then(if (enabled) Modifier.shadow(10.dp, PpShapes.pill, clip = false, ambientColor = glow, spotColor = glow) else Modifier)
                    .clip(PpShapes.pill)
                    .drawBehind { drawGoldButton(enabled, r.ink3, tilt?.value ?: 0f) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent, contentColor = Color(PpPalette.BTN_ON_GOLD),
                    disabledContainerColor = Color.Transparent, disabledContentColor = r.text3,
                ),
                content = { label() },
            )
        }
        PpButtonStyle.Outline -> OutlinedButton(
            onClick = onClick, modifier = m, enabled = enabled, shape = PpShapes.pill,
            // .btn.ghost: transparent, haldi text, haldi border at 45 %.
            border = BorderStroke(1.dp, if (enabled) r.gold.copy(alpha = 0.45f) else r.line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = r.gold, disabledContentColor = r.text3),
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

/** `.btn`: 3-stop gold gradient, inset top/bottom lines and a soft-light sheen stripe that follows the tilt. */
private fun DrawScope.drawGoldButton(enabled: Boolean, disabledFill: Color, tiltDeg: Float) {
    if (!enabled) {
        drawRect(disabledFill)
        return
    }
    drawRect(
        Brush.verticalGradient(
            0f to Color(PpPalette.GOLD_GRAD_TOP), 0.55f to Color(PpPalette.GOLD_GRAD_MID), 1f to Color(PpPalette.GOLD_GRAD_BOTTOM),
        ),
    )
    val tx = SurfaceMath.specularX(tiltDeg) // 0.15..0.85 across the button
    val x = size.width * tx
    val half = size.width * 0.18f
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Transparent, 0.5f to Color(PpPalette.SHEEN_WHITE).copy(alpha = 0.55f), 1f to Color.Transparent,
            startX = x - half, endX = x + half,
        ),
        blendMode = BlendMode.Softlight,
    )
    val line = 1.dp.toPx()
    drawLine(Color(PpPalette.BTN_INSET_TOP), Offset(size.height / 2, line / 2), Offset(size.width - size.height / 2, line / 2), line)
    drawLine(Color(PpPalette.BTN_INSET_BOTTOM), Offset(size.height / 2, size.height - line / 2), Offset(size.width - size.height / 2, size.height - line / 2), line)
}
