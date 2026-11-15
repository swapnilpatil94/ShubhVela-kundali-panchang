@file:Suppress("MatchingDeclarationName", "LongParameterList", "MaxLineLength")

package app.parivaar.panchang.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.surfaces.LocalGlassBackdrop
import app.parivaar.panchang.core.designsystem.surfaces.glassSource
import app.parivaar.panchang.core.designsystem.surfaces.rememberGlassBackdrop
import app.parivaar.panchang.core.designsystem.tokens.Gem

/** Bottom space a screen that scrolls under the dock must keep clear (zero when the suite already pads the content). */
val LocalDockInset = compositionLocalOf { 0.dp }

/** [center] marks the Aaj orb entry (else the middle item is the orb). */
data class NavItem(val key: String, @StringRes val label: Int, @DrawableRes val icon: Int, val center: Boolean = false)

/**
 * Root navigation: content with a floating [GlassDock] over it (no rail on tablets; the dock is centred, max 480 dp).
 * The content is the glass source; the dock samples it. Content gets bottom room for the dock via [DockSpec.FOOTPRINT].
 * Same first four parameters as before; [gem], [orb] and [dockVisible] are new with defaults.
 */
@Composable
fun PpNavigationSuite(
    items: List<NavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    gem: Gem = Gem.PUKHRAJ,
    orb: OrbUi? = null,
    dockVisible: Boolean = true,
    underDock: Boolean = false,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val orbUi = orb ?: OrbUi(OrbTone.NEUTRAL, null, null, stringResource(R.string.orb_a11y_neutral))
    Box(Modifier.fillMaxSize()) {
        // underDock: the screen scrolls beneath the dock and pads its own bottom by LocalDockInset (M07R B7).
        val contentBox = if (underDock) {
            Modifier.fillMaxSize().glassSource(backdrop)
        } else {
            Modifier.fillMaxSize().glassSource(backdrop).navigationBarsPadding().padding(bottom = (DockSpec.HEIGHT + DockSpec.MARGIN).dp)
        }
        val inset = if (underDock) {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + DockSpec.FOOTPRINT.dp
        } else {
            0.dp
        }
        Box(contentBox) { CompositionLocalProvider(LocalDockInset provides inset, content = content) }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            GlassDock(items, selected, gem, orbUi, dockVisible, onSelect, Modifier.align(Alignment.BottomCenter))
        }
    }
}
