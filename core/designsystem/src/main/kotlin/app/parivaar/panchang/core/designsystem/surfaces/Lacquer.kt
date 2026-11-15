@file:Suppress("LongParameterList", "MaxLineLength")
package app.parivaar.panchang.core.designsystem.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val shadowColor = Color.Black.copy(alpha = if (r.isDark) 0.35f else 0.14f)
    var m = modifier
        .shadow(10.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(r.lacquerTop, r.lacquerBottom)))
        .border(1.dp, border, shape)
    m = if (onClick != null) m.clickable(role = Role.Button, onClick = onClick) else m
    CompositionLocalProvider(LocalContentColor provides r.text) {
        Column(m.semantics(mergeDescendants = mergeSemantics) {}.padding(PpSpace.l), content = content)
    }
}
