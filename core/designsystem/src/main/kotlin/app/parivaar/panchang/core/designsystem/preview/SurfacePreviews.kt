@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.components.GlassDock
import app.parivaar.panchang.core.designsystem.components.NavItem
import app.parivaar.panchang.core.designsystem.components.OrbTone
import app.parivaar.panchang.core.designsystem.components.OrbUi
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.components.PpChip
import app.parivaar.panchang.core.designsystem.components.PpTopBar
import app.parivaar.panchang.core.designsystem.components.TimeQuality
import app.parivaar.panchang.core.designsystem.components.TimeWindowCard
import app.parivaar.panchang.core.designsystem.surfaces.GlassSurface
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood

/** Each gallery (Night, Chandan, Paper) at font scale 1.0 and 2.0. */
@Preview(name = "1x", showBackground = true, widthDp = 360)
@Preview(name = "font 2x", fontScale = 2f, showBackground = true, widthDp = 360)
annotation class SurfaceShots

@Composable
private fun Gallery(mood: RatnaMood) = PanchangTheme(mood = mood) {
    Column(
        Modifier.background(PpTheme.ratna.bg0).padding(PpSpace.l),
        verticalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        PpTopBar("Today", "Sample subtitle", "AB", {}, {})
        Lacquer(Modifier.fillMaxWidth()) { Text("Lacquer") }
        Lacquer(Modifier.fillMaxWidth(), hero = true) { Text("Lacquer hero") }
        Lacquer(Modifier.fillMaxWidth(), warn = true) { Text("Lacquer warn") }
        PpCard(Modifier.fillMaxWidth()) { Text("PpCard") }
        GlassSurface(null, PpShapes.tile, Modifier.fillMaxWidth()) {
            Text("Glass (solid fallback in preview)", Modifier.padding(PpSpace.l))
        }
        PpChip("Good", TimeQuality.AUSPICIOUS)
        PpChip("Wait", TimeQuality.INAUSPICIOUS)
        PpChip("Neutral", TimeQuality.NEUTRAL)
        PpButton("Primary", {})
        PpButton("Outline", {}, PpButtonStyle.Outline)
        PpButton("Tonal", {}, PpButtonStyle.Tonal)
        TimeWindowCard("Rahu kaal", "3:00 PM - 4:30 PM", TimeQuality.INAUSPICIOUS, "Wait", activeLabel = "Now", countdown = "0:42:10")
    }
}

@SurfaceShots @Composable private fun NightGallery() = Gallery(RatnaMood.NIGHT)
@SurfaceShots @Composable private fun ChandanGallery() = Gallery(RatnaMood.CHANDAN)
@SurfaceShots @Composable private fun PaperGallery() = Gallery(RatnaMood.PAPER)

@Composable
private fun DockStates(mood: RatnaMood) = PanchangTheme(mood = mood) {
    val items = listOf(
        NavItem("cal", R.string.tab_calendar, R.drawable.ic_tab_calendar),
        NavItem("kun", R.string.tab_kundli, R.drawable.ic_tab_kundli),
        NavItem("aaj", R.string.tab_aaj, R.drawable.ic_tab_aaj, center = true),
        NavItem("bond", R.string.tab_bonds, R.drawable.ic_tab_bonds),
        NavItem("bhakti", R.string.tab_bhakti, R.drawable.ic_tab_bhakti),
    )
    Column(Modifier.background(PpTheme.ratna.bg0).padding(PpSpace.l), verticalArrangement = Arrangement.spacedBy(PpSpace.l)) {
        GlassDock(items, "aaj", Gem.NEELAM, OrbUi(OrbTone.GOOD, 0.49f, "9:33 AM", "Aaj. Good time now, until 9:33 AM."), true, {})
        GlassDock(items, "aaj", Gem.NEELAM, OrbUi(OrbTone.AVOID, 0.3f, "10:57 AM", "Aaj. Better to wait, until 10:57 AM."), true, {})
        GlassDock(items, "cal", Gem.PANNA, OrbUi(OrbTone.NEUTRAL, null, null, "Aaj. Open today's timings."), true, {})
    }
}

@SurfaceShots @Composable private fun NightDock() = DockStates(RatnaMood.NIGHT)
@SurfaceShots @Composable private fun ChandanDock() = DockStates(RatnaMood.CHANDAN)
