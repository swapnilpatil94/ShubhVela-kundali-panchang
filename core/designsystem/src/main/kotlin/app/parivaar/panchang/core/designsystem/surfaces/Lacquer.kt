@file:Suppress("LongParameterList", "MaxLineLength")
package app.parivaar.panchang.core.designsystem.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

/**
 * The content surface (DESIGN_V2 section 4): vertical gradient lacquerTop to lacquerBottom, hairline border, radius 20,
 * ambient shadow 0 10 24 at 35 % black (lighter on light moods). [hero] adds a gold border (one per screen), [warn] a kumkum one.
 * Content cards always use this; glass is for chrome only.
 */
@Composable
fun Lacquer(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    warn: Boolean = false,
    onClick: (() -> Unit)? = null,
    mergeSemantics: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val r = PpTheme.ratna
    val shape = PpShapes.lacquer
    val spec = remember(r.mood) { PpPalette.of(r.mood) }
    val border = Color(SurfaceMath.lacquerBorder(spec, hero, warn))
    // .lacq: 0 10 24 at 35 %; .hero: 0 16 34 at 45 % and a warmer inset highlight.
    val shadowColor = Color.Black.copy(alpha = (if (hero) 0.45f else 0.35f) * (if (r.isDark) 1f else 0.4f))
    val highlight = if (hero) Color(PpPalette.HERO_HIGHLIGHT) else Color.White.copy(alpha = if (r.isDark) 0.05f else 0.6f)
    var m = modifier
        .shadow(if (hero) 16.dp else 10.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(r.lacquerTop, r.lacquerBottom)))
        .drawWithContent {
            drawContent()
            // Inset top highlight (1 dp), following the straight part of the top edge.
            val inset = 20.dp.toPx()
            drawLine(highlight, Offset(inset, 1.dp.toPx()), Offset(size.width - inset, 1.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        .border(1.dp, border, shape)
    m = if (onClick != null) m.clickable(role = Role.Button, onClick = onClick) else m
    CompositionLocalProvider(LocalContentColor provides r.text) {
        Column(m.semantics(mergeDescendants = mergeSemantics) {}.padding(PpSpace.l), content = content)
    }
}

/** `.hair`: 1 dp gold hairline fading out at both ends. Decorative; gold only beside auspicious content. */
@Composable
fun GoldHairline(modifier: Modifier = Modifier) {
    val gold = PpTheme.ratna.goldFill
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, gold.copy(alpha = 0.45f), Color.Transparent)),
        ),
    )
}
