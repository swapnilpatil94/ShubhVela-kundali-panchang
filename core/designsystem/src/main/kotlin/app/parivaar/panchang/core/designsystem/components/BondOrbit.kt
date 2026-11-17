package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.rememberLoop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val OUTER_MS = 40_000
private const val INNER_MS = 26_000
private const val DASH = 6f
private const val AXIS_MAX = 10
private const val GOLD_POINT = 0xFFFFE3A3
private const val BLUE_POINT = 0xFFBFD3FF

/**
 * DESIGN_V2 section 8 `BondOrbit` (screen 18): two light points on counter-rotating rings, the score in num-xl gold
 * and a caption. Still under Reduce Motion. [description] is the TalkBack text for the whole ring.
 */
@Composable
fun BondOrbit(score: String, caption: String, description: String, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    val outer = rememberLoop(OUTER_MS, 0f)
    val inner = rememberLoop(INNER_MS, 0.5f)
    val gold = r.gold
    val blue = Color(PpPalette.NEELAM)
    Box(
        modifier.fillMaxWidth().height(200.dp).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(196.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val ro = size.minDimension / 2 - 8.dp.toPx()
            val ri = ro * 0.75f
            drawCircle(gold.copy(alpha = 0.3f), ro, c, style = Stroke(1.dp.toPx()))
            drawCircle(
                blue.copy(alpha = 0.5f), ri, c,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
            )
            val a = (outer.value * 2 * PI - PI / 2).toFloat()
            val b = (-inner.value * 2 * PI + PI / 2).toFloat()
            val pa = Offset(c.x + ro * cos(a), c.y + ro * sin(a))
            val pb = Offset(c.x + ri * cos(b), c.y + ri * sin(b))
            drawCircle(gold.copy(alpha = 0.35f), 12.dp.toPx(), pa)
            drawCircle(Color(GOLD_POINT), 6.dp.toPx(), pa)
            drawCircle(blue.copy(alpha = 0.35f), 11.dp.toPx(), pb)
            drawCircle(Color(BLUE_POINT), 5.dp.toPx(), pb)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score, style = PpType.numXl.copy(fontSize = 56.sp), color = gold, textAlign = TextAlign.Center)
            Text(caption, style = PpType.meta, color = r.text2, textAlign = TextAlign.Center)
        }
    }
}

/** One axis bar of a bond result: gold, or the calm "okay" tone for a growth area. */
@Composable
fun BondAxisBar(label: String, value: Int, growth: Boolean, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    val shape = RoundedCornerShape(5.dp)
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(PpSpace.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = PpType.body, color = r.text)
            Text(value.toString(), style = PpType.meta, color = r.text2)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(shape).background(r.ink3)) {
            Box(
                Modifier.fillMaxWidth(value.coerceIn(0, AXIS_MAX) / AXIS_MAX.toFloat()).height(5.dp).clip(shape)
                    .background(if (growth) r.okay else r.gold),
            )
        }
    }
}
