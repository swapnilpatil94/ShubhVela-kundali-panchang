@file:Suppress("MagicNumber", "MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import kotlin.math.cos
import kotlin.math.sin

/** A stretch of the day as fractions of sunrise..sunset (0..1). */
data class ArcSegment(val from: Float, val to: Float, val quality: TimeQuality)

/**
 * Sunrise-to-sunset arc. Good stretches are drawn solid in green, waiting stretches dashed in amber, so colour
 * is never the only signal; the legend chips name both. [description] is the full spoken summary.
 */
@Composable
fun DayArc(
    segments: List<ArcSegment>,
    nowFraction: Float?,
    startLabel: String,
    endLabel: String,
    goodLabel: String,
    waitLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val pp = PpTheme.colors
    val track = MaterialTheme.colorScheme.outlineVariant
    val marker = pp.primaryText
    val amber = pp.gold
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(PpSpace.s),
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(ARC_ASPECT).clearAndSetSemantics { }) {
            val stroke = 12.dp.toPx()
            val pad = stroke
            val r = minOf(size.width / 2 - pad, size.height - pad * 2)
            val center = Offset(size.width / 2, size.height - pad)
            val topLeft = Offset(center.x - r, center.y - r)
            val box = Size(r * 2, r * 2)
            drawArc(track, 180f, 180f, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Butt))
            fun draw(s: ArcSegment, color: Color, dashed: Boolean) {
                val a = s.from.coerceIn(0f, 1f)
                val b = s.to.coerceIn(0f, 1f)
                if (b <= a) return
                val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(stroke * 0.8f, stroke * 0.6f)) else null
                drawArc(
                    color, 180f + 180f * a, 180f * (b - a), false, topLeft, box,
                    style = Stroke(stroke, cap = StrokeCap.Butt, pathEffect = effect),
                )
            }
            segments.filter { it.quality == TimeQuality.AUSPICIOUS }.forEach { draw(it, pp.auspiciousFg, false) }
            segments.filter { it.quality == TimeQuality.INAUSPICIOUS }.forEach { draw(it, amber, true) }
            nowFraction?.let {
                val theta = Math.toRadians((180f + 180f * it.coerceIn(0f, 1f)).toDouble())
                val p = Offset(center.x + r * cos(theta).toFloat(), center.y + r * sin(theta).toFloat())
                drawCircle(marker, stroke * 0.9f, p)
                drawCircle(track, stroke * 0.4f, p)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelLarge)
            Text(endLabel, style = MaterialTheme.typography.labelLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            PpChip(goodLabel, TimeQuality.AUSPICIOUS)
            PpChip(waitLabel, TimeQuality.INAUSPICIOUS)
        }
    }
}

private const val ARC_ASPECT = 2.2f

@PpPreviews
@Composable
private fun DayArcPreview() = PanchangTheme {
    DayArc(
        segments = listOf(
            ArcSegment(0.1f, 0.35f, TimeQuality.AUSPICIOUS), ArcSegment(0.45f, 0.58f, TimeQuality.AUSPICIOUS),
            ArcSegment(0.62f, 0.75f, TimeQuality.INAUSPICIOUS),
        ),
        nowFraction = 0.4f, startLabel = "6:24 AM", endLabel = "5:48 PM", goodLabel = "Good time",
        waitLabel = "Wait", description = "Day arc", modifier = Modifier.padding(PpSpace.l),
    )
}
