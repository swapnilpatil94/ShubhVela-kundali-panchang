@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.adjustForContrast
import app.parivaar.panchang.core.designsystem.tokens.lighten

/** Colours of the gem chip (a dark glass pill, same in every mood because it sits on the sky). */
object GemChipColors {
    /** Pill fill rgba(10,13,24,.72) as in the design; the worst case under it is a white sky, modelled in tests. */
    const val FILL_RGB = 0xFF0A0D18L
    const val FILL_ALPHA = 0.72f
    const val TEXT = PpPalette.WHITE
    fun light(gem: Gem): Long = lighten(gem.argb, 0.25f)
    fun dark(gem: Gem): Long = gem.argb
    /** Facet colour guaranteed >= 3:1 against the pill (icons). */
    fun facet(gem: Gem): Long = adjustForContrast(light(gem), listOf(FILL_RGB), darken = false, minRatio = 3.0)
}

/** Weekday gem chip, for example "Shanivaar - Neelam": 16 dp faceted gem (conic gradient) and the label. */
@Composable
fun GemChip(gem: Gem, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val cd = stringResource(R.string.cd_gem_chip, label)
    Row(
        modifier
            .clip(shape)
            .background(Color(GemChipColors.FILL_RGB).copy(alpha = GemChipColors.FILL_ALPHA))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(start = PpSpace.s10, end = PpSpace.m, top = PpSpace.xs + 2.dp, bottom = PpSpace.xs + 2.dp)
            .clearAndSetSemantics { contentDescription = cd },
        horizontalArrangement = Arrangement.spacedBy(PpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val glow = Color(gem.argb)
        Canvas(Modifier.size(16.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val gemColor = Color(gem.argb)
            val reach = size.width / 2 + 10.dp.toPx()
            // box-shadow: 0 0 10px gem
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), c, reach), reach, c)
            // conic-gradient(from 30deg, gem, #0008, gem, #fff8, gem): CSS 0deg is up, a Compose sweep starts at 3 o'clock, so rotate by 30 - 90.
            val conic = Brush.sweepGradient(
                0f to gemColor, 0.25f to Color(0x88000000), 0.5f to gemColor, 0.75f to Color(0x88FFFFFF), 1f to gemColor, center = c,
            )
            rotate(-60f, c) { drawCircle(conic, size.width / 2, c) }
            // radial-gradient(circle at 30% 20%, rgba(255,255,255,.9) 0 8%, transparent 30%)
            val spec = Offset(size.width * 0.30f, size.height * 0.20f)
            drawCircle(
                Brush.radialGradient(0f to Color.White.copy(alpha = 0.9f), 0.27f to Color.White.copy(alpha = 0.9f), 1f to Color.Transparent, center = spec, radius = size.width * 0.3f),
                size.width / 2, c,
            )
        }
        Text(label, style = PpType.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = Color(GemChipColors.TEXT))
    }
}
