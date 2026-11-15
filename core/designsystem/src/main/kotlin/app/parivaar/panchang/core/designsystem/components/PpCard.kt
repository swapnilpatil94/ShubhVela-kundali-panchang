package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer

/**
 * [mergeSemantics] = false for cards holding several independent controls (settings rows).
 * Content card: delegates to [Lacquer]. [hero] (one per screen) and [warn] add the gold / kumkum border. */
@Composable
fun PpCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    hero: Boolean = false,
    warn: Boolean = false,
    mergeSemantics: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) = Lacquer(modifier, hero, warn, onClick, mergeSemantics, content)
