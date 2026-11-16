@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpFonts
import app.parivaar.panchang.core.designsystem.tokens.lighten

/** Gem colour of graha [code] (engine `Graha` ordinal SUN..KETU matches the navaratna order). */
fun grahaColor(code: Int): Color = Color(lighten(Gem.entries[code.coerceIn(0, Gem.entries.size - 1)].argb, 0.2f))

/**
 * The birth chart in gold hairlines (DESIGN_V2 section 8 `KundliChart`, PNG 13). Canvas draws the frame and labels;
 * twelve invisible boxes carry one semantics node per house with [a11y]'s text, and the container carries [summary].
 * Rashi numbers label each cell (NORTH); the lagna shows as the Asc chip (NORTH) or a diagonal stroke (SOUTH/EAST).
 */
@Composable
fun ChartView(
    model: ChartModel,
    style: ChartStyle,
    a11y: (HouseCell) -> String,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val used = ChartGeometry.effective(style, model.lagnaKnown)
    val cells = remember(model, used) { ChartGeometry.layout(model, used) }
    val r = PpTheme.ratna
    val ink = ChartInk(
        gold = r.goldFill, label = r.text3, text = r.text, disc = Color(DISC),
        lagna = stringResource(R.string.chart_lagna_glyph), retro = stringResource(R.string.chart_retro_suffix),
    )
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(1f).semantics { if (summary != null) contentDescription = summary },
    ) {
        val side = maxWidth
        Canvas(Modifier.fillMaxSize()) { drawChart(measurer, cells, used, model.lagnaKnown, ink) }
        // One semantics node per house (acceptance 1).
        for (pc in cells) {
            val w = side * (pc.right - pc.left) * NODE_FRACTION
            val h = side * (pc.bottom - pc.top) * NODE_FRACTION
            Box(
                Modifier
                    .offset(side * pc.anchor.x - w / 2, side * pc.anchor.y - h / 2)
                    .size(w, h)
                    .semantics { contentDescription = a11y(pc.cell) },
            )
        }
    }
}

/** Colours and resource texts the canvas needs. */
private class ChartInk(
    val gold: Color,
    val label: Color,
    val text: Color,
    val disc: Color,
    val lagna: String,
    val retro: String,
)

private const val DISC = 0xFF1B2030
private const val NODE_FRACTION = .6f
private const val LAGNA_STROKE = .3f

private fun DrawScope.drawChart(
    m: TextMeasurer,
    cells: List<PlacedCell>,
    used: ChartStyle,
    lagnaKnown: Boolean,
    ink: ChartInk,
) {
    val s = size.minDimension
    fun p(u: UPoint) = Offset(u.x * s, u.y * s)
    val stroke = 1.2.dp.toPx()
    drawRect(ink.gold, topLeft = Offset.Zero, size = Size(s, s), style = Stroke(stroke))
    ChartGeometry.lines(used).forEach { (a, b) -> drawLine(ink.gold, p(a), p(b), stroke) }
    val north = used == ChartStyle.NORTH
    for (pc in cells) {
        val lagnaHere = pc.cell.lagna && lagnaKnown
        if (!north && lagnaHere) {
            // Lagna stroke across the cell's top-left corner.
            val from = UPoint(pc.left, pc.top + (pc.bottom - pc.top) * LAGNA_STROKE)
            val to = UPoint(pc.left + (pc.right - pc.left) * LAGNA_STROKE, pc.top)
            drawLine(ink.gold, p(from), p(to), stroke)
        }
        if (north) {
            val style = TextStyle(color = ink.label, fontSize = 10.sp)
            drawCentered(m, (pc.cell.rashi + 1).toString(), p(pc.labelAt), style)
        }
        val marks = buildList {
            if (north && lagnaHere) add(ink.lagna to ink.text)
            pc.cell.grahas.forEach { add((if (it.retro) it.glyph + ink.retro else it.glyph) to grahaColor(it.code)) }
        }
        drawMarks(m, marks, pc, s, ink.disc)
    }
}

private fun DrawScope.drawCentered(m: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val l = m.measure(text, style)
    drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f))
}

/** Glyph chips in a small grid around the cell anchor, two or three per row. */
private fun DrawScope.drawMarks(
    m: TextMeasurer,
    marks: List<Pair<String, Color>>,
    pc: PlacedCell,
    s: Float,
    disc: Color,
) {
    if (marks.isEmpty()) return
    val perRow = if (marks.size > 4) 3 else 2
    val chip = (s * 0.075f).coerceAtMost(30.dp.toPx())
    val gap = chip * 1.08f
    val rows = (marks.size + perRow - 1) / perRow
    val cx = pc.anchor.x * s
    val cy = pc.anchor.y * s
    marks.forEachIndexed { i, (text, color) ->
        val row = i / perRow
        val inRow = minOf(perRow, marks.size - row * perRow)
        val x = cx + (i % perRow - (inRow - 1) / 2f) * gap
        val y = cy + (row - (rows - 1) / 2f) * gap
        drawRoundRect(disc, Offset(x - chip / 2, y - chip / 2), Size(chip, chip), CornerRadius(chip / 2))
        val fontSize = (chip * 0.40f).toSp()
        val style = TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = PpFonts.ui)
        drawCentered(m, text, Offset(x, y), style)
    }
}
