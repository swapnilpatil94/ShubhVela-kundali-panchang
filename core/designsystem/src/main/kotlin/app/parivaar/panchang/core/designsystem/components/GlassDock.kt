@file:Suppress("MagicNumber", "MaxLineLength", "LongMethod", "LongParameterList")
package app.parivaar.panchang.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.surfaces.GlassSurface
import app.parivaar.panchang.core.designsystem.surfaces.LocalGlassBackdrop
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.LocalReducedMotion
import app.parivaar.panchang.core.designsystem.tokens.PpHaptic
import app.parivaar.panchang.core.designsystem.tokens.PpMotion
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.lighten
import app.parivaar.panchang.core.designsystem.tokens.rememberPpHaptics

/** Dock geometry in dp (DESIGN_V2 section 5). */
object DockSpec {
    const val HEIGHT = 64
    const val MARGIN = 12
    const val ORB_COLUMN = 80
    const val ORB_RAISE = 17
    const val MAX_WIDTH = 480
    const val ICON = 22
    const val LABEL_SCALE_CAP = 1.3f

    /** Space the dock occupies above the bottom inset: capsule, bottom margin and the raised orb. */
    const val FOOTPRINT = HEIGHT + MARGIN + ORB_RAISE
}

/**
 * Floating glass capsule: tabs left and right of the raised Aaj orb. The [items] entry with `center = true` (else the middle
 * one) is the orb. Chrome only, so glass; solid fallback is handled by [GlassSurface]. [visible] slides it away.
 */
@Composable
fun GlassDock(
    items: List<NavItem>,
    selected: String,
    gem: Gem,
    orb: OrbUi,
    visible: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptic = rememberPpHaptics()
    val navDesc = stringResource(R.string.dock_a11y)
    val centerIdx = items.indexOfFirst { it.center }.takeIf { it >= 0 } ?: (items.size / 2)
    val select: (String) -> Unit = { key ->
        if (key != selected) haptic(PpHaptic.TAB)
        onSelect(key)
    }
    val show by animateFloatAsState(if (visible) 1f else 0f, PpMotion.defaultSpatial(reduced), label = "dock")
    val hideDistance = with(LocalDensity.current) { (DockSpec.FOOTPRINT + DockSpec.MARGIN).dp.toPx() } + 160f
    Box(
        modifier.fillMaxWidth().navigationBarsPadding()
            .graphicsLayer { translationY = (1f - show) * hideDistance; alpha = if (show < 0.01f) 0f else 1f }
            .semantics { contentDescription = navDesc },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier.widthIn(max = DockSpec.MAX_WIDTH.dp).fillMaxWidth()
                .padding(horizontal = DockSpec.MARGIN.dp).padding(bottom = DockSpec.MARGIN.dp)
                .height((DockSpec.HEIGHT + DockSpec.ORB_RAISE).dp),
        ) {
            GlassSurface(
                LocalGlassBackdrop.current, PpShapes.dock,
                Modifier.fillMaxWidth().height(DockSpec.HEIGHT.dp).align(Alignment.BottomCenter),
            ) {
                Row(Modifier.fillMaxWidth().height(DockSpec.HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
                    items.forEachIndexed { i, item ->
                        if (i == centerIdx) {
                            OrbSlot(item, item.key == selected)
                        } else {
                            TabSlot(item, item.key == selected, gem, Modifier.weight(1f)) { select(item.key) }
                        }
                    }
                }
            }
            if (items.isNotEmpty()) {
                val c = items[centerIdx]
                AajOrb(orb, c.key == selected, { select(c.key) }, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** The orb column keeps its 80 dp and shows the label under the raised orb; the orb itself is drawn and clickable above. */
@Composable
private fun OrbSlot(item: NavItem, isSelected: Boolean) {
    val r = PpTheme.ratna
    Box(Modifier.width(DockSpec.ORB_COLUMN.dp).height(DockSpec.HEIGHT.dp), contentAlignment = Alignment.BottomCenter) {
        DockLabel(stringResource(item.label), if (isSelected) r.text else r.text2, Modifier.padding(bottom = 0.dp).clearAndSetSemantics { })
    }
}

@Composable
private fun TabSlot(item: NavItem, isSelected: Boolean, gem: Gem, modifier: Modifier, onClick: () -> Unit) {
    val r = PpTheme.ratna
    val label = stringResource(item.label)
    val tint = when {
        !isSelected -> r.text2 // legibility over scrolling content: chandan2, not chandan3 (see DECISIONS)
        r.isDark -> Color(lighten(gem.argb))
        else -> Color(PpPalette.HALDI_DEEP)
    }
    val lift by animateFloatAsState(if (isSelected) -1f else 0f, PpMotion.fastSpatial(LocalReducedMotion.current), label = "lift")
    Column(
        modifier.height(DockSpec.HEIGHT.dp)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Tab; this.selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(DockSpec.ICON.dp + 12.dp).graphicsLayer { translationY = lift * density }
                .drawBehind {
                    if (isSelected && r.isDark) {
                        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent), center, size.minDimension / 2f))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(DockSpec.ICON.dp))
        }
        DockLabel(label, if (isSelected) r.text else r.text2, Modifier.clearAndSetSemantics { })
        // Underline: 16 x 3 dp, transparent > gem+30 % white > transparent, with a soft gem glow (dark moods only).
        val bar = if (r.isDark) Color(lighten(gem.argb, 0.3f)) else tint
        Box(
            Modifier.padding(top = 3.dp).size(width = 16.dp, height = 3.dp)
                .drawBehind {
                    if (isSelected && r.isDark) {
                        drawCircle(Brush.radialGradient(listOf(Color(gem.argb).copy(alpha = 0.55f), Color.Transparent), center, 10.dp.toPx()), 10.dp.toPx())
                    }
                }
                .background(
                    if (isSelected) Brush.horizontalGradient(listOf(Color.Transparent, bar, Color.Transparent)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                    PpShapes.pill,
                ),
        )
    }
}

/** 11 sp / 800 label, always visible; the font scale is capped at 1.3x so five labels fit, the full text stays in semantics. */
@Composable
private fun DockLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale
    val shrink = (DockSpec.LABEL_SCALE_CAP / fontScale).coerceAtMost(1f)
    val style = PpType.label
    Text(
        text, modifier, color = color, style = style.copy(fontSize = style.fontSize * shrink),
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
    )
}

/**
 * Hide-on-scroll rule: more than 24 dp of cumulative downward scroll hides, any upward scroll shows. Pure so it can be tested.
 * [dy] is the content scroll delta in px as a nested-scroll `available.y` (negative = content moves up = scrolling down).
 */
class DockScrollRule(private val thresholdPx: Float) {
    var visible: Boolean = true
        private set
    private var accumulated = 0f

    fun onScroll(dy: Float): Boolean {
        when {
            dy > 0f -> { accumulated = 0f; visible = true }
            dy < 0f -> {
                accumulated += -dy
                if (accumulated > thresholdPx) visible = false
            }
        }
        return visible
    }

    fun reset() { accumulated = 0f; visible = true }

    companion object { const val THRESHOLD_DP = 24f }
}

/** Visible unless the screen is immersive or the user scrolled down. */
fun dockVisible(immersive: Boolean, scrollVisible: Boolean): Boolean = !immersive && scrollVisible
