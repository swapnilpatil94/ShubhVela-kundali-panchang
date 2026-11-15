package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

/** Flat card: 0 dp elevation, 1 dp outlineVariant border. */
@Composable
fun PpCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val border = BorderStroke(1.dp, colors.outlineVariant)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(PpSpace.l), content = content) }
    val merged = modifier.semantics(mergeDescendants = true) {}
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = merged, shape = PpShapes.medium,
            color = colors.surfaceContainer, contentColor = colors.onSurface, border = border, content = inner,
        )
    } else {
        Surface(
            modifier = merged, shape = PpShapes.medium,
            color = colors.surfaceContainer, contentColor = colors.onSurface, border = border, content = inner,
        )
    }
}
