@file:Suppress("MagicNumber", "MaxLineLength", "LongMethod")
package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpLoop
import app.parivaar.panchang.core.designsystem.tokens.RatnaColors
import app.parivaar.panchang.core.designsystem.tokens.rememberLoop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Live status of the day: GOOD gold, AVOID kumkum, NEUTRAL gold ring with a lit diya. */
enum class OrbTone { GOOD, AVOID, NEUTRAL }

/**
 * Orb state, plain data. [fractionLeft] is the share of the current choghadiya still to run (null: none, ring is a full okay-grey ring);
 * [untilText] is already formatted; [a11y] is the complete TalkBack label.
 */
@Immutable
data class OrbUi(val tone: OrbTone, val fractionLeft: Float?, val untilText: String?, val a11y: String)

/** Pure ring geometry (tested on the JVM). The ring starts at 12 o'clock and sweeps clockwise. */
object OrbGeometry {
    const val RING_RADIUS_DP = 29f
    const val RING_STROKE_DP = 3f
    const val ORB_DP = 64f
    const val RAISE_DP = 17f
    const val START_DEG = -90f

    fun sweepDegrees(fractionLeft: Float?): Float = 360f * (fractionLeft ?: 1f).coerceIn(0f, 1f)

    /** Position of the end dot relative to the centre, for ring radius [r]. */
    fun endDot(fractionLeft: Float?, r: Float): Pair<Float, Float> {
        val a = (START_DEG + sweepDegrees(fractionLeft)) * PI.toFloat() / 180f
        return r * cos(a) to r * sin(a)
    }

    /** Lit flame for good and neutral, unlit wick for avoid. */
    fun diyaLit(tone: OrbTone): Boolean = tone != OrbTone.AVOID
}

/**
 * The Aaj orb: 64 dp, ring r 29 dp / 3 dp, diya at the centre. The ring is drawn in the draw phase from [ui], so a tick that
 * changes only the fraction redraws without recomposing the dock row. Breathes only while [selected] and motion is allowed.
 */
@Composable
fun AajOrb(ui: OrbUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    val phase = rememberLoop(PpLoop.ORB_MS, restFrame = 0.25f)
    val breathe = selected
    Canvas(
        modifier.size(OrbGeometry.ORB_DP.dp).clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = ui.a11y; this.selected = selected },
    ) {
        val glow = if (breathe) 0.5f + 0.5f * sin(phase.value * 2f * PI.toFloat()) else 0.5f
        drawOrb(ui, r, glow, selected)
    }
}

private fun DrawScope.drawOrb(ui: OrbUi, r: RatnaColors, glow: Float, selected: Boolean) {
    val c = center
    val dp = 1.dp.toPx()
    val ringColor = when {
        ui.fractionLeft == null -> r.okay
        ui.tone == OrbTone.AVOID -> r.kumkum
        else -> if (r.isDark) r.gold else Color(PpPalette.ORB_RING_CHANDAN)
    }
    // Ink gap, thin gold outer ring, inner radial fill.
    drawCircle(r.bg0, 32f * dp, c)
    drawCircle(if (r.isDark) r.gold.copy(alpha = 0.85f) else Color(PpPalette.ORB_RING_CHANDAN), 31.25f * dp, c, style = Stroke(1.5f * dp))
    val fill = if (r.isDark) listOf(r.lacquerTop, r.bg0) else listOf(r.lacquerTop, r.bg1)
    drawCircle(Brush.radialGradient(fill, c, 27.5f * dp), 27.5f * dp, c)
    if (selected && r.isDark) drawCircle(Brush.radialGradient(listOf(r.gold.copy(alpha = 0.10f + 0.12f * glow), Color.Transparent), c, 30f * dp), 30f * dp, c)
    // Progress ring: track, then the remaining part.
    val rr = OrbGeometry.RING_RADIUS_DP * dp
    val stroke = OrbGeometry.RING_STROKE_DP * dp
    drawCircle(r.line, rr, c, style = Stroke(stroke))
    val sweep = OrbGeometry.sweepDegrees(ui.fractionLeft)
    drawArc(
        ringColor, OrbGeometry.START_DEG, sweep, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    if (ui.fractionLeft != null && sweep > 0f) {
        val (dx, dy) = OrbGeometry.endDot(ui.fractionLeft, rr)
        drawCircle(ringColor, 3.5f * dp, Offset(c.x + dx, c.y + dy))
    }
    drawDiya(OrbGeometry.diyaLit(ui.tone), r, glow, c, dp)
}

/** Original line-and-fill diya: a shallow bowl, a wick and, when lit, a teardrop flame. */
private fun DrawScope.drawDiya(lit: Boolean, r: RatnaColors, glow: Float, c: Offset, dp: Float) {
    val bowlColor = if (lit) (if (r.isDark) r.gold else Color(PpPalette.HALDI_DEEP)) else r.okay
    val bowl = Path().apply {
        moveTo(c.x - 12f * dp, c.y + 3f * dp)
        quadraticTo(c.x, c.y + 3f * dp, c.x + 12f * dp, c.y + 3f * dp)
        quadraticTo(c.x + 9f * dp, c.y + 10f * dp, c.x, c.y + 10f * dp)
        quadraticTo(c.x - 9f * dp, c.y + 10f * dp, c.x - 12f * dp, c.y + 3f * dp)
        close()
    }
    drawPath(bowl, bowlColor)
    if (lit) {
        drawCircle(
            Brush.radialGradient(listOf(r.gold.copy(alpha = 0.18f + 0.18f * glow), Color.Transparent), Offset(c.x, c.y - 4f * dp), 14f * dp),
            14f * dp, Offset(c.x, c.y - 4f * dp),
        )
        val flame = Path().apply {
            moveTo(c.x, c.y - 14f * dp)
            cubicTo(c.x + 6f * dp, c.y - 7f * dp, c.x + 4f * dp, c.y + 1f * dp, c.x, c.y + 1f * dp)
            cubicTo(c.x - 4f * dp, c.y + 1f * dp, c.x - 6f * dp, c.y - 7f * dp, c.x, c.y - 14f * dp)
            close()
        }
        drawPath(flame, Brush.verticalGradient(listOf(Color(PpPalette.GOLD_GRAD_TOP), Color(PpPalette.KUMKUM)), c.y - 14f * dp, c.y + 1f * dp))
    } else {
        drawLine(r.okay, Offset(c.x, c.y + 3f * dp), Offset(c.x, c.y - 3f * dp), strokeWidth = 1.5f * dp, cap = StrokeCap.Round)
    }
}
