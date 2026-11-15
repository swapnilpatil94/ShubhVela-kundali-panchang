@file:Suppress("MatchingDeclarationName", "MaxLineLength")

package app.parivaar.panchang.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

enum class TimeQuality { AUSPICIOUS, INAUSPICIOUS, NEUTRAL }

private data class ChipLook(
    val bg: Color,
    val fg: Color,
    val border: Color,
    @DrawableRes val icon: Int,
    @StringRes val name: Int,
)

/** Gold / kumkum / okay pill. Always icon + label + colour; never colour alone. */
@Composable
fun PpChip(label: String, quality: TimeQuality, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    // .pill: ink3 ground; good = haldi text + haldi border 35 %, bad = kumkum, neutral = okay text + hairline.
    val look = when (quality) {
        TimeQuality.AUSPICIOUS -> ChipLook(
            r.ink3, r.gold, r.gold.copy(alpha = 0.35f), R.drawable.ic_quality_auspicious, R.string.quality_auspicious,
        )
        TimeQuality.INAUSPICIOUS -> ChipLook(
            r.ink3, r.kumkum, r.kumkum.copy(alpha = 0.35f), R.drawable.ic_quality_inauspicious, R.string.quality_inauspicious,
        )
        TimeQuality.NEUTRAL -> ChipLook(
            r.ink3, r.okay, r.line, R.drawable.ic_quality_neutral, R.string.quality_neutral,
        )
    }
    val description = stringResource(R.string.chip_description, label, stringResource(look.name))
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .background(look.bg, PpShapes.pill)
            .border(1.dp, look.border, PpShapes.pill)
            .padding(horizontal = PpSpace.s10, vertical = PpSpace.xs + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PpSpace.xs),
    ) {
        Icon(painterResource(look.icon), contentDescription = null, tint = look.fg, modifier = Modifier.size(16.dp))
        Text(label, color = look.fg, style = MaterialTheme.typography.labelLarge)
    }
}
