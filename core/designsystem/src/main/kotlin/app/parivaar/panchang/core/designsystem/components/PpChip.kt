@file:Suppress("MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
    @DrawableRes val icon: Int,
    @StringRes val name: Int,
)

/** Always icon + label + colour; never colour alone. */
@Composable
fun PpChip(label: String, quality: TimeQuality, modifier: Modifier = Modifier) {
    val pp = PpTheme.colors
    val scheme = MaterialTheme.colorScheme
    val look = when (quality) {
        TimeQuality.AUSPICIOUS -> ChipLook(
            pp.auspiciousBg, pp.auspiciousFg, R.drawable.ic_quality_auspicious, R.string.quality_auspicious,
        )
        TimeQuality.INAUSPICIOUS -> ChipLook(
            pp.inauspiciousBg, pp.inauspiciousFg, R.drawable.ic_quality_inauspicious, R.string.quality_inauspicious,
        )
        TimeQuality.NEUTRAL -> ChipLook(
            scheme.surfaceVariant, scheme.onSurface, R.drawable.ic_quality_neutral, R.string.quality_neutral,
        )
    }
    val description = stringResource(R.string.chip_description, label, stringResource(look.name))
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .background(look.bg, PpShapes.pill)
            .padding(horizontal = PpSpace.m, vertical = PpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PpSpace.xs),
    ) {
        Icon(painterResource(look.icon), contentDescription = null, tint = look.fg, modifier = Modifier.size(16.dp))
        Text(label, color = look.fg, style = MaterialTheme.typography.labelLarge)
    }
}
