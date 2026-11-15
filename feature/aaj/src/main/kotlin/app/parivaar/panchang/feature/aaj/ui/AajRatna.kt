@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions", "MagicNumber", "LongParameterList")

package app.parivaar.panchang.feature.aaj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.OrbTone
import app.parivaar.panchang.core.designsystem.planets.PlanetBody
import app.parivaar.panchang.core.designsystem.planets.PlanetLight
import app.parivaar.panchang.core.designsystem.planets.PlanetView
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpLoop
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.RatnaColors
import app.parivaar.panchang.core.designsystem.tokens.rememberLoop
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Ratna Glass pieces of the Aaj screen (M07R B7). Stateless; every string and number is passed in from engine data. */

internal fun OrbTone.color(r: RatnaColors): Color = when (this) {
    OrbTone.GOOD -> r.gold
    OrbTone.AVOID -> r.kumkum
    OrbTone.NEUTRAL -> r.okay
}

/** Weight-1 title with a trailing value; stacks them at large font scales so neither clips. */
@Composable
private fun AdaptiveRow(modifier: Modifier = Modifier, start: @Composable () -> Unit, end: @Composable () -> Unit) {
    if (LocalDensity.current.fontScale > STACK_FONT_SCALE) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PpSpace.xs)) { start(); end() }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.m)) {
            Box(Modifier.weight(1f, fill = false)) { start() }
            end()
        }
    }
}

private const val STACK_FONT_SCALE = 1.3f

internal enum class AbhiState { GOOD, WAIT, NEUTRAL }

/** Original line-and-fill diya (bowl, wick, teardrop flame when lit). The flame breathes on the 1.6 s loop; still under Reduce Motion. */
@Composable
internal fun Diya(lit: Boolean, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    val phase = rememberLoop(PpLoop.DIYA_MS)
    Canvas(modifier.size(72.dp).clearAndSetSemantics { }) {
        val u = size.width / 72f
        val c = Offset(size.width / 2f, size.height / 2f + 6f * u)
        val breathe = 0.5f - 0.5f * cos(2f * PI.toFloat() * phase.value)
        val bowl = Path().apply {
            moveTo(c.x - 28f * u, c.y + 6f * u)
            quadraticTo(c.x, c.y + 6f * u, c.x + 28f * u, c.y + 6f * u)
            quadraticTo(c.x + 20f * u, c.y + 24f * u, c.x, c.y + 24f * u)
            quadraticTo(c.x - 20f * u, c.y + 24f * u, c.x - 28f * u, c.y + 6f * u)
            close()
        }
        val bowlBrush = if (lit) {
            Brush.verticalGradient(listOf(Color(PpPalette.GOLD_GRAD_TOP), Color(PpPalette.GOLD_GRAD_BOTTOM)), c.y + 6f * u, c.y + 24f * u)
        } else {
            Brush.verticalGradient(listOf(r.okay, r.okay))
        }
        drawPath(bowl, bowlBrush)
        if (lit) {
            val gc = Offset(c.x, c.y - 10f * u)
            drawCircle(Brush.radialGradient(listOf(r.gold.copy(alpha = 0.22f + 0.16f * breathe), Color.Transparent), gc, 30f * u), 30f * u, gc)
            val h = 26f * u * (0.94f + 0.06f * breathe)
            val flame = Path().apply {
                moveTo(c.x, c.y + 2f * u - h)
                cubicTo(c.x + 10f * u, c.y - 10f * u, c.x + 7f * u, c.y + 3f * u, c.x, c.y + 3f * u)
                cubicTo(c.x - 7f * u, c.y + 3f * u, c.x - 10f * u, c.y - 10f * u, c.x, c.y + 2f * u - h)
                close()
            }
            drawPath(flame, Brush.verticalGradient(listOf(Color(PpPalette.GOLD_GRAD_TOP), Color(PpPalette.KUMKUM)), c.y - h, c.y + 3f * u))
        } else {
            drawLine(r.okay, Offset(c.x, c.y + 6f * u), Offset(c.x, c.y - 6f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
        }
    }
}

/**
 * The hero lacquer: diya on the left, a Devanagari line over the English display line over the detail line.
 * Gold border when good, kumkum when waiting, plain when neutral. TalkBack reads the English line and detail only.
 */
@Composable
internal fun AbhiCard(
    state: AbhiState,
    devaLine: String?,
    line: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    val accent = if (state == AbhiState.WAIT) r.kumkum else r.gold
    Lacquer(modifier.fillMaxWidth(), hero = state == AbhiState.GOOD, warn = state == AbhiState.WAIT, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.l)) {
            Diya(lit = state != AbhiState.WAIT)
            Column(Modifier.weight(1f)) {
                if (devaLine != null) Text(devaLine, Modifier.clearAndSetSemantics { }, style = PpType.devaHeadline, color = accent)
                Text(line, Modifier.semantics { heading() }, style = PpType.displayM, color = r.text)
                if (detail != null) Text(detail, style = PpType.body, color = r.text2)
            }
        }
    }
}

/** One part of the day arc. Fractions are of sunrise..sunset. */
internal data class PathSeg(val startFrac: Float, val endFrac: Float, val tone: OrbTone, val name: String)

/**
 * Sunrise-to-sunset arc of the day's choghadiya parts, coloured good / okay / avoid, with the Abhijit band, the real sun
 * dot at "now" and sunrise/sunset captions. Labels sit outside the arc at a fixed 11 dp (decorative: the same
 * information is in the content description, the next list and the full timings), skipped where they would collide.
 * A tap on a part calls [onSegment]; TalkBack gets one action on the whole arc.
 */
@Suppress("LongMethod")
@Composable
internal fun SuryaPath(
    segments: List<PathSeg>,
    nowFrac: Float?,
    sunriseText: String,
    sunsetText: String,
    nowLabel: String,
    abhijit: ClosedFloatingPointRange<Float>?,
    a11y: String,
    onSegment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    val measurer = rememberTextMeasurer()
    val sunTop = Color(PpPalette.GOLD_GRAD_TOP)
    Lacquer(modifier.fillMaxWidth()) {
        Column(
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = a11y
                onClick { onSegment(segments.indexOfFirst { nowFrac != null && nowFrac >= it.startFrac && nowFrac < it.endFrac }.coerceAtLeast(0)); true }
            },
            verticalArrangement = Arrangement.spacedBy(PpSpace.s),
        ) {
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(ARC_ASPECT).clearAndSetSemantics { }
                    .pointerInput(segments) {
                        detectTapGestures { p ->
                            val g = ArcGeometry(size.width.toFloat(), size.height.toFloat(), density)
                            val hit = g.segmentAt(p, segments)
                            if (hit >= 0) onSegment(hit)
                        }
                    },
            ) {
                val g = ArcGeometry(size.width, size.height, density)
                val stroke = g.stroke
                val topLeft = Offset(g.cx - g.r, g.cy - g.r)
                val box = Size(g.r * 2, g.r * 2)
                drawArc(r.line, 180f, 180f, false, topLeft, box, style = Stroke(stroke))
                segments.forEach { s ->
                    val a = s.startFrac.coerceIn(0f, 1f)
                    val b = s.endFrac.coerceIn(0f, 1f)
                    if (b > a) {
                        val gap = 0.8f
                        drawArc(s.tone.color(r), 180f + 180f * a + gap, (180f * (b - a) - 2 * gap).coerceAtLeast(0.5f), false, topLeft, box, style = Stroke(stroke))
                    }
                }
                abhijit?.let {
                    val inner = g.r - stroke * 1.2f
                    drawArc(
                        r.gold, 180f + 180f * it.start.coerceIn(0f, 1f), 180f * (it.endInclusive.coerceIn(0f, 1f) - it.start.coerceIn(0f, 1f)), false,
                        Offset(g.cx - inner, g.cy - inner), Size(inner * 2, inner * 2), style = Stroke(density * 2f, cap = StrokeCap.Round),
                    )
                }
                drawLine(r.line, Offset(g.cx - g.r - stroke, g.cy), Offset(g.cx + g.r + stroke, g.cy), strokeWidth = density)
                val labelDensity = Density(density, 1f)
                val placed = ArrayList<Rect>()
                fun label(text: String, mid: Float, color: Color, weight: FontWeight) {
                    val layout = measurer.measure(text, PpType.label.copy(fontSize = 11.sp, fontWeight = weight), density = labelDensity)
                    val ang = PI.toFloat() * (1f + mid)
                    val rr = g.r + stroke * 0.5f + density * 6f
                    val px = g.cx + rr * cos(ang)
                    val py = g.cy + rr * sin(ang)
                    val w = layout.size.width.toFloat()
                    val h = layout.size.height.toFloat()
                    val left = (px - w / 2f + (px - g.cx) / g.r * w / 2f).coerceIn(0f, size.width - w)
                    val top = (py - h / 2f + (py - g.cy) / g.r * h / 2f).coerceIn(0f, size.height - h)
                    val rect = Rect(left, top, left + w, top + h)
                    if (placed.none { it.overlaps(rect.inflate(density * 2f)) }) {
                        placed += rect
                        drawText(layout, color, Offset(left, top))
                    }
                }
                nowFrac?.let { label(nowLabel, it, r.text, FontWeight.ExtraBold) }
                segments.forEach { s -> label(s.name, (s.startFrac + s.endFrac) / 2f, s.tone.color(r), FontWeight.ExtraBold) }
                nowFrac?.let {
                    val ang = PI.toFloat() * (1f + it.coerceIn(0f, 1f))
                    val c = Offset(g.cx + g.r * cos(ang), g.cy + g.r * sin(ang))
                    drawCircle(Brush.radialGradient(listOf(sunTop.copy(alpha = 0.45f), Color.Transparent), c, density * 16f), density * 16f, c)
                    drawCircle(sunTop, density * 6f, c)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sunriseText, style = PpType.meta, color = r.text2, modifier = Modifier.weight(1f, fill = false))
                Text(sunsetText, style = PpType.meta, color = r.text2, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

private const val ARC_ASPECT = 1.85f

/** Pure arc geometry in pixels (also used for hit-testing). */
internal class ArcGeometry(val w: Float, val h: Float, val density: Float) {
    val stroke = density * 10f
    private val padX = density * 34f
    private val padTop = density * 28f
    private val padBottom = density * 10f
    val r = minOf(w / 2f - padX, h - padTop - padBottom)
    val cx = w / 2f
    val cy = h - padBottom

    /** Index of the part under [p] (on the arc band, upper half, 48 dp wide tolerance) or -1. */
    @Suppress("ReturnCount")
    fun segmentAt(p: Offset, segments: List<PathSeg>): Int {
        val dx = p.x - cx
        val dy = p.y - cy
        if (dy > density * 12f) return -1
        if (abs(hypot(dx, dy) - r) > density * 24f) return -1
        var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (deg > 0f) deg -= 360f // upper half is -180..0
        val frac = ((deg + 180f) / 180f).coerceIn(0f, 1f)
        val i = segments.indexOfFirst { frac >= it.startFrac && frac < it.endFrac }
        return if (i >= 0) i else segments.indices.minByOrNull { abs((segments[it].startFrac + segments[it].endFrac) / 2f - frac) } ?: -1
    }
}

internal data class NextRowUi(val tone: OrbTone, val title: String, val range: String)

/** Two or three rows (Rahu Kaal, best window) and "See all timings". The whole card toggles the full timings. */
@Composable
internal fun NextList(rows: List<NextRowUi>, seeAll: String, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    Lacquer(modifier.fillMaxWidth(), onClick = onSeeAll) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s10)) {
            rows.forEach { row ->
                AdaptiveRow(
                    Modifier.heightIn(min = 32.dp),
                    start = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.s10)) {
                            Box(Modifier.size(14.dp).background(row.tone.color(r), CircleShape).clearAndSetSemantics { })
                            Text(row.title, style = PpType.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), color = row.tone.color(r))
                        }
                    },
                    end = { Text(row.range, style = PpType.body.copy(fontSize = 16.sp, fontFeatureSettings = "tnum"), color = r.text) },
                )
            }
            Row(Modifier.heightIn(min = PpSpace.minTouchTarget), verticalAlignment = Alignment.CenterVertically) {
                Text(seeAll, Modifier.weight(1f), style = PpType.body.copy(fontSize = 16.sp), color = r.text2)
                Text("›", Modifier.clearAndSetSemantics { }, style = PpType.displayS, color = r.text2)
            }
        }
    }
}

/** Shown only on a real sky-event day (an eclipse visible from the place). Thumbnail with the body, eyebrow, display line, sub line. */
@Composable
internal fun SkyEventCard(
    eyebrow: String,
    title: String,
    sub: String,
    body: PlanetBody,
    a11y: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    Lacquer(modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.semantics { contentDescription = a11y },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.l),
        ) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.radialGradient(listOf(r.gold.copy(alpha = 0.35f), r.bg0))),
                contentAlignment = Alignment.Center,
            ) { PlanetView(body, PlanetLight.FULL, 64.dp, animated = false, modifier = Modifier.clearAndSetSemantics { }) }
            Column(Modifier.weight(1f).clearAndSetSemantics { }) {
                Text(eyebrow.uppercase(), style = PpType.eyebrow.copy(fontWeight = FontWeight.ExtraBold), color = r.gold)
                Text(title, style = PpType.displayS, color = r.text)
                Text(sub, style = PpType.meta, color = r.text2)
            }
        }
    }
}

internal data class LimbRowUi(val label: String, val value: String, val ends: String, val note: String?)

/** Tithi, nakshatra, yoga and karana with their end times. The card opens the full panchang. */
@Composable
internal fun PanchangList(rows: List<LimbRowUi>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    Lacquer(modifier.fillMaxWidth(), onClick = onOpen) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = r.line)
            Column(Modifier.heightIn(min = PpSpace.minTouchTarget).padding(vertical = PpSpace.s10)) {
                AdaptiveRow(
                    start = { Text(row.label, style = PpType.body, color = r.text2) },
                    end = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(row.value, style = PpType.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), color = r.text, textAlign = TextAlign.End)
                            Text(row.ends, style = PpType.meta, color = r.text3, textAlign = TextAlign.End)
                        }
                    },
                )
                if (row.note != null) Text(row.note, Modifier.padding(top = PpSpace.xs), style = PpType.meta, color = r.text3)
            }
        }
    }
}

/** Small uppercase section label (for example the panchang eyebrow). */
@Composable
internal fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier.padding(horizontal = PpSpace.xs).semantics { heading() },
        style = PpType.eyebrow.copy(fontWeight = FontWeight.ExtraBold), color = PpTheme.ratna.text3,
    )
}

/** Keeps the tap target of a plain text action at 48 dp. */
internal fun Modifier.touchTarget(onClick: () -> Unit): Modifier = heightIn(min = PpSpace.minTouchTarget).clickable(onClick = onClick)
