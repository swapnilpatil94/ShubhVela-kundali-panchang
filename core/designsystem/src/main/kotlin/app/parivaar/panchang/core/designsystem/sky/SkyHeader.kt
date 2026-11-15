@file:Suppress("MagicNumber", "MaxLineLength", "LongParameterList")

package app.parivaar.panchang.core.designsystem.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType

/**
 * Text of the sky header. The graha is expressed by [gem] (the weekday lord's gem tints the sky); the caller resolves
 * `gemFor(weekday)` and the localised eyebrow, title and meta. No engine type here.
 */
@Immutable
data class SkyHeaderUi(val gem: Gem, val eyebrow: String, val title: String, val meta: AnnotatedString)

/** Header height (dp) and hora planet geometry (DESIGN_V2 section 6). */
object SkyHeaderSpec {
    val height = 330.dp
    val planetSize = 112.dp
    val planetOffsetX = 28.dp
    val planetTop = 56.dp
    /** Text column fraction of the width when a planet is shown: the planet's left edge is at 1 - (112 - 28) / 360 on a phone. */
    const val TEXT_FRACTION_WITH_PLANET = 0.72f
    const val TEXT_FRACTION = 0.84f
}

/**
 * The sky header: image (or placeholder), reading shade, fade into ink0, then the text and the hora [planet] slot.
 * Always dark (onSky text), in every mood. Static: nothing animates here, so Reduce Motion needs no special case;
 * the planet slot owns its own motion. [extra] is placed under the meta line (for example the gem chip).
 */
@Composable
fun SkyHeader(
    ui: SkyHeaderUi,
    modifier: Modifier = Modifier,
    source: SkySource = SkySource.Placeholder(ui.gem),
    planet: (@Composable () -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val onSky = PpTheme.ratna.onSky
    val shadow = Shadow(Color(0xE6000000), Offset(0f, 1f), 2f)
    Box(modifier.fillMaxWidth().heightIn(min = SkyHeaderSpec.height)) {
        SkyImage(source, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(*
            SkyShade.shadeStops.map { (f, a) -> f to Color(SkyShade.SHADE_RGB).copy(alpha = a) }.toTypedArray().let { it }
        )))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(*
            SkyShade.fadeStops.map { (f, a) -> f to Color(PpPalette.INK0).copy(alpha = a) }.toTypedArray().let { it }
        )))
        if (planet != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = SkyHeaderSpec.planetTop)
                    .offset(x = SkyHeaderSpec.planetOffsetX).size(SkyHeaderSpec.planetSize),
            ) { planet() }
        }
        Column(
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth(if (planet != null) SkyHeaderSpec.TEXT_FRACTION_WITH_PLANET else SkyHeaderSpec.TEXT_FRACTION)
                .padding(start = PpSpace.screen, end = PpSpace.s, bottom = PpSpace.xxl, top = PpSpace.xxxl),
        ) {
            Text(ui.eyebrow.uppercase(), style = PpType.eyebrow.copy(fontWeight = FontWeight.ExtraBold, shadow = shadow), color = onSky)
            Text(
                ui.title,
                Modifier.padding(top = PpSpace.xs).semantics { heading() },
                style = PpType.displayL.copy(shadow = shadow),
                color = Color.White,
            )
            Text(
                ui.meta,
                Modifier.padding(top = PpSpace.xs),
                style = PpType.meta.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, shadow = shadow),
                color = onSky,
            )
            extra()
        }
    }
}

