@file:Suppress("MagicNumber")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import kotlin.math.abs
import kotlin.math.cos

/**
 * Terminator of the Moon for a Sun-Moon elongation: [waxing] lights the right limb (north-up sky), the terminator is
 * an ellipse of half-width `radius * [terminatorFraction]`, drawn lit when [gibbous] and dark when a crescent.
 */
data class MoonShape(val waxing: Boolean, val terminatorFraction: Float, val gibbous: Boolean)

object MoonPhase {
    /** Elongation at the middle of tithi 1..30 (12 degrees each). D13: used per cell, no engine call. */
    fun tithiElongationDeg(tithi: Int): Double = (tithi.coerceIn(1, 30) - 1) * 12.0 + 6.0

    fun shape(elongationDeg: Double): MoonShape {
        val e = ((elongationDeg % 360.0) + 360.0) % 360.0
        val c = cos(Math.toRadians(e))
        return MoonShape(waxing = e < 180.0, terminatorFraction = abs(c).toFloat(), gibbous = c < 0.0)
    }

    /** Illuminated fraction k = (1 - cos e) / 2. */
    fun illumination(elongationDeg: Double): Double = (1.0 - cos(Math.toRadians(elongationDeg))) / 2.0
}

/** A light Canvas moon for the month grid: pure CPU path drawing, decorative (the cell carries the description). */
@Composable
fun MoonPhaseIcon(elongationDeg: Double, size: Dp = 13.dp, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    val lit = if (r.isDark) r.text else r.lacquerTop
    val dark = if (r.isDark) r.ink3 else r.text.copy(alpha = 0.55f)
    val outline = r.text3.copy(alpha = 0.6f)
    val shape = MoonPhase.shape(elongationDeg)
    Canvas(modifier.size(size)) {
        val rad = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val disc = Rect(c.x - rad, c.y - rad, c.x + rad, c.y + rad)
        drawCircle(dark, rad, c)
        val half = Path().apply { arcTo(disc, if (shape.waxing) -90f else 90f, 180f, true); close() }
        drawPath(half, lit)
        val w = rad * shape.terminatorFraction
        if (w > 0.2f) {
            val ellipse = Path().apply { addOval(Rect(c.x - w, c.y - rad, c.x + w, c.y + rad)) }
            drawPath(ellipse, if (shape.gibbous) lit else dark)
        }
        drawCircle(outline, rad, c, style = Stroke(0.75.dp.toPx()))
    }
}
