@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.ErrorState
import app.parivaar.panchang.core.designsystem.components.LoadingState
import app.parivaar.panchang.core.designsystem.components.LotusPetalShape
import app.parivaar.panchang.core.designsystem.components.NavItem
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.components.PpChip
import app.parivaar.panchang.core.designsystem.components.PpNavigationSuite
import app.parivaar.panchang.core.designsystem.components.PpTopBar
import app.parivaar.panchang.core.designsystem.components.TimeQuality
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

/** Light, dark, 200 % font scale, Devanagari locale, and an RTL locale. */
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "font 2x", fontScale = 2f, showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(name = "rtl", locale = "ar", showBackground = true)
annotation class PpPreviews

private val previewTabs = listOf(
    NavItem("aaj", R.string.tab_aaj, R.drawable.ic_tab_aaj),
    NavItem("calendar", R.string.tab_calendar, R.drawable.ic_tab_calendar),
    NavItem("kundli", R.string.tab_kundli, R.drawable.ic_tab_kundli),
    NavItem("bonds", R.string.tab_bonds, R.drawable.ic_tab_bonds),
    NavItem("bhakti", R.string.tab_bhakti, R.drawable.ic_tab_bhakti),
)

@Composable
private fun Frame(content: @Composable () -> Unit) = PanchangTheme {
    Surface { Column(Modifier.padding(PpSpace.l), verticalArrangement = Arrangement.spacedBy(PpSpace.m)) { content() } }
}

@PpPreviews
@Composable
private fun CardAndButtonsPreview() = Frame {
    PpCard(Modifier.fillMaxWidth()) { Text("Card") }
    PpButton("Primary", {})
    PpButton("Outline", {}, PpButtonStyle.Outline)
    PpButton("Tonal", {}, PpButtonStyle.Tonal)
    PpButton("Disabled", {}, enabled = false)
}

@PpPreviews
@Composable
private fun ChipsPreview() = Frame {
    PpChip("Chip", TimeQuality.AUSPICIOUS)
    PpChip("Chip", TimeQuality.INAUSPICIOUS)
    PpChip("Chip", TimeQuality.NEUTRAL)
}

@PpPreviews
@Composable
private fun StatesPreview() = Frame {
    EmptyState("Title", "Body text", action = "Action" to {})
    LoadingState("Loading")
    ErrorState("Title", "Body text", listOf("Retry" to {}, "Close" to {}))
}

@PpPreviews
@Composable
private fun TopBarPreview() = Frame {
    PpTopBar("Title", "Subtitle", "AB", {}, {})
    Box(Modifier.size(48.dp, 72.dp).background(Color(0xFFE8772E), LotusPetalShape))
}

@Preview(name = "nav bar", widthDp = 360, heightDp = 400, showBackground = true)
@Preview(name = "nav rail", widthDp = 720, heightDp = 400, showBackground = true)
@Preview(name = "nav hi", widthDp = 360, heightDp = 400, locale = "hi", showBackground = true)
@Composable
private fun NavigationPreview() = PanchangTheme {
    PpNavigationSuite(previewTabs, "aaj", {}) { Text("Content") }
}
