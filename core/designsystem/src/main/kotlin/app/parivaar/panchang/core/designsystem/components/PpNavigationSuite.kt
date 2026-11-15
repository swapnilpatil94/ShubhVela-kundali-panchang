@file:Suppress("MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.R

data class NavItem(val key: String, @StringRes val label: Int, @DrawableRes val icon: Int)

private val RAIL_MIN_WIDTH = 600.dp

/** Bar below 600 dp width, rail at 600 dp or more. */
@Composable
fun PpNavigationSuite(
    items: List<NavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val selectedText = stringResource(R.string.state_selected)
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= RAIL_MIN_WIDTH) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = colors.surfaceContainer) {
                    items.forEach { item ->
                        val isSel = item.key == selected
                        NavigationRailItem(
                            selected = isSel, onClick = { onSelect(item.key) },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { NavLabel(stringResource(item.label)) },
                            modifier = Modifier.semantics { if (isSel) stateDescription = selectedText },
                        )
                    }
                }
                Column(Modifier.weight(1f).fillMaxSize()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) { content() }
                NavigationBar(containerColor = colors.surfaceContainer) {
                    items.forEach { item ->
                        val isSel = item.key == selected
                        NavigationBarItem(
                            selected = isSel, onClick = { onSelect(item.key) },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { NavLabel(stringResource(item.label)) },
                            modifier = Modifier.semantics { if (isSel) stateDescription = selectedText },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single-line label. At large font scales the size shrinks so the effective text stays near 14 sp-equivalent and
 * fits its slot; ellipsis is the last resort. The full text stays in the semantics for TalkBack.
 */
@Composable
private fun NavLabel(text: String) {
    val fontScale = LocalDensity.current.fontScale
    val base = MaterialTheme.typography.labelMedium
    val shrink = (MAX_LABEL_SCALE / fontScale).coerceAtMost(1f)
    Text(
        text,
        style = base.copy(fontSize = base.fontSize * shrink),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val MAX_LABEL_SCALE = 1.3f
