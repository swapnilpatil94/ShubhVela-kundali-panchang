@file:Suppress("MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod")
package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.parivaar.panchang.core.designsystem.tokens.LocalReducedMotion
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
 * The Aaj orb (`.orb` + `orbSVG` in design/shubhvela-screens.html): 64 dp radial-ink disc, 1.5 dp gold ring, 6 dp ink halo,
 * drop shadow, a gold (kumkum when avoid) glow pulsing 18-34 dp over 3.2 s, ring r 29 dp / 3 dp from 12 o'clock with an end dot,
 * and the diya. The ring and glow are drawn in the draw phase, so a tick that changes only the fraction does not recompose.
 * The pulse runs only while [selected] and motion is allowed; otherwise it rests at 26 dp.
 */
@Composable
fun AajOrb(ui: OrbUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    val animate = selected && !LocalReducedMotion.current
    val pulse = rememberLoop(PpLoop.ORB_MS)
    val flicker = rememberLoop(PpLoop.DIYA_MS)
    Box(modifier.size(OrbGeometry.ORB_DP.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val p = if (animate) 0.5f + 0.5f * sin(pulse.value * 2f * PI.toFloat()) else 0.5f
            val f = if (animate) sin(flicker.value * 2f * PI.toFloat()) else 0f
            drawOrb(ui, r, p, f)
        }
        Box(
            Modifier.fillMaxSize().clip(CircleShape).clickable(role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = ui.a11y; this.selected = selected },
        )
    }
}

private fun DrawScope.drawOrb(ui: OrbUi, r: RatnaColors, pulse: Float, flicker: Float) {
    val c = center
    val dp = 1.dp.toPx()
    val avoid = ui.tone == OrbTone.AVOID
    val accent = when {
        ui.fractionLeft == null -> r.okay
        avoid -> r.kumkum
        r.isDark -> r.gold
        else -> Color(PpPalette.ORB_RING_CHANDAN)
    }
    // Drop shadow 0 12 28 at 60 %, then the pulsing glow (18..34 dp, .30..50 alpha; kumkum when avoid).
    val shadowR = 32f * dp + 24f * dp
    drawCircle(
        Brush.radialGradient(0f to Color.Black.copy(alpha = if (r.isDark) 0.55f else 0.16f), 32f / 56f to Color.Black.copy(alpha = if (r.isDark) 0.45f else 0.12f), 1f to Color.Transparent, center = Offset(c.x, c.y + 12f * dp), radius = shadowR),
        shadowR, Offset(c.x, c.y + 12f * dp),
    )
    if (r.isDark) {
        val g = (18f + 16f * pulse) * dp
        val gr = 32f * dp + g
        val a = if (avoid) 0.45f else 0.30f + 0.20f * pulse
        drawCircle(
            Brush.radialGradient(0f to accent.copy(alpha = a), 32f * dp / gr to accent.copy(alpha = a), 1f to Color.Transparent, center = c, radius = gr),
            gr, c,
        )
    }
    // 6 dp ink halo (ink0 at 85 %), 1.5 dp ring outside the disc.
    val halo = if (r.isDark) r.bg0.copy(alpha = 0.85f) else Color(PpPalette.ORB_HALO_CHANDAN)
    drawCircle(halo, 33.5f * dp + 3f * dp, c, style = Stroke(6f * dp))
    drawCircle(accent.copy(alpha = if (r.isDark) 0.65f + 0.2f * pulse else 1f), 32f * dp + 0.75f * dp, c, style = Stroke(1.5f * dp))
    // Disc: radial-gradient(circle at 50% 35%, ink3, ink0 70%); ivory on Chandan.
    val focal = Offset(c.x, c.y - 0.15f * 64f * dp)
    val reach = 52.5f * dp
    val (inner, outer, stopAt) = if (r.isDark) Triple(r.ink3, r.bg0, 0.7f) else Triple(Color(PpPalette.ORB_CHANDAN_TOP), Color(PpPalette.ORB_CHANDAN_BOTTOM), 0.75f)
    drawCircle(Brush.radialGradient(0f to inner, stopAt to outer, 1f to outer, center = focal, radius = reach), 32f * dp, c)
    // Track and progress.
    val rr = OrbGeometry.RING_RADIUS_DP * dp
    val stroke = OrbGeometry.RING_STROKE_DP * dp
    drawCircle(if (r.isDark) Color(0x1FEDE6D6) else Color(0x2E785A28), rr, c, style = Stroke(stroke))
    val sweep = OrbGeometry.sweepDegrees(ui.fractionLeft)
    drawArc(accent, OrbGeometry.START_DEG, sweep, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(stroke, cap = StrokeCap.Round))
    if (ui.fractionLeft != null && sweep > 0f) {
        val (dx, dy) = OrbGeometry.endDot(ui.fractionLeft, rr)
        drawCircle(Color(if (avoid) PpPalette.END_DOT_AVOID else PpPalette.END_DOT_GOOD), 3.2f * dp, Offset(c.x + dx, c.y + dy))
    }
    drawDiya(OrbGeometry.diyaLit(ui.tone), r, c, dp, flicker)
}

/** orbSVG flame and bowl, in the design's 70-unit box, scaled to the 64 dp orb. Unlit: a wick in chandan3. */
private fun DrawScope.drawDiya(lit: Boolean, r: RatnaColors, c: Offset, dp: Float, flicker: Float) {
    val s = 64f * dp / 70f
    fun px(x: Float) = c.x + (x - 35f) * s
    fun py(y: Float) = c.y + (y - 35f) * s
    val bowl = Path().apply {
        moveTo(px(21f), py(42f)); lineTo(px(46f), py(42f)); lineTo(px(50f), py(39.5f)); lineTo(px(48.5f), py(43.5f))
        cubicTo(px(45.5f), py(50.5f), px(24f), py(50.5f), px(21f), py(42f)); close()
    }
    if (lit) {
        val k = 1f + 0.04f * flicker
        fun fy(y: Float) = py(41f) - (41f - y) * s * k
        val outer = Path().apply {
            moveTo(px(35f), fy(20f)); quadraticTo(px(42f), fy(31f), px(35f), fy(41f)); quadraticTo(px(28f), fy(31f), px(35f), fy(20f)); close()
        }
        val inner = Path().apply {
            moveTo(px(35f), fy(29f)); quadraticTo(px(38f), fy(35f), px(35f), fy(40f)); quadraticTo(px(32f), fy(35f), px(35f), fy(29f)); close()
        }
        drawPath(outer, Color(PpPalette.FLAME_OUTER))
        drawPath(inner, Color(PpPalette.FLAME_INNER))
    } else {
        drawLine(r.text3, Offset(px(35f), py(33f)), Offset(px(35f), py(40f)), strokeWidth = 1.6f * s)
        drawCircle(r.text3, 1.4f * s, Offset(px(35f), py(31f)))
    }
    drawPath(bowl, if (r.isDark) Color(PpPalette.HALDI_DEEP) else Color(PpPalette.ORB_RING_CHANDAN))
}
