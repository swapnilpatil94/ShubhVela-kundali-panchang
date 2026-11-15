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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.adjustForContrast
import app.parivaar.panchang.core.designsystem.tokens.lighten

/** Colours of the gem chip (a dark glass pill, same in every mood because it sits on the sky). */
object GemChipColors {
    /** Pill fill: ink1 at 85 %; the worst case under it is a white sky, modelled in tests. */
    const val FILL_RGB = PpPalette.INK1
    const val FILL_ALPHA = 0.85f
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
            .border(1.dp, Color(PpPalette.LINE), shape)
            .padding(start = PpSpace.s10, end = PpSpace.m, top = PpSpace.xs + 2.dp, bottom = PpSpace.xs + 2.dp)
            .clearAndSetSemantics { contentDescription = cd },
        horizontalArrangement = Arrangement.spacedBy(PpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val facet = Color(GemChipColors.facet(gem))
            val deep = Color(GemChipColors.dark(gem))
            val path = Path().apply {
                moveTo(c.x, 0f); lineTo(size.width, c.y); lineTo(c.x, size.height); lineTo(0f, c.y); close()
            }
            val conic = Brush.sweepGradient(listOf(facet, deep, Color(GemChipColors.light(gem)), deep, facet), c)
            drawPath(path, conic)
            drawPath(path, Color.White.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
        Text(label, style = PpType.label, color = Color(GemChipColors.TEXT))
    }
}
