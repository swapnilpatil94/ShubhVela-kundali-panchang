@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.sky.GemChip
import app.parivaar.panchang.core.designsystem.sky.SkyHeader
import app.parivaar.panchang.core.designsystem.sky.SkyHeaderUi
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood

/** All 9 sky grades, at font scale 1.0 and 2.0 (Night and Chandan below). The hora planet slot shows a stand-in disc. */
@Preview(name = "1x", showBackground = true, widthDp = 360, heightDp = 3300)
@Preview(name = "font 2x", fontScale = 2f, showBackground = true, widthDp = 360, heightDp = 4600)
annotation class SkyShots

@Composable
private fun SkyGrades(mood: RatnaMood) = PanchangTheme(mood = mood) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PpSpace.l)) {
        Gem.entries.forEachIndexed { i, gem ->
            SkyHeader(
                SkyHeaderUi(gem, "Sample eyebrow", "Sample title", AnnotatedString("Sample meta line, place and date")),
                planet = if (i % 2 == 0) {
                    { Box(Modifier.padding(PpSpace.s).size(96.dp).clip(CircleShape).background(Color(0xFFB8AF9C))) }
                } else null,
            ) { GemChip(gem, "Sample - ${gem.name}", Modifier.padding(top = PpSpace.s)) }
        }
    }
}

@SkyShots @Composable private fun SkyGradesNight() = SkyGrades(RatnaMood.NIGHT)
@SkyShots @Composable private fun SkyGradesChandan() = SkyGrades(RatnaMood.CHANDAN)
