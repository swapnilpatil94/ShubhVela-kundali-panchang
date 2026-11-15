@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.components.ArcSegment
import app.parivaar.panchang.core.designsystem.components.DayArc
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.feature.aaj.R

/** Tier 1: the answer first. The verdict, the date, the footer and the day arc. */
@Composable
internal fun HeaderCard(
    verdict: String,
    dateText: String,
    arc: ArcData,
    sunrise: String,
    sunset: String,
    onHow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = arc.nowFraction?.let { "${(it * PERCENT).toInt()}%" }
    val arcDescription = stringResource(
        R.string.aaj_arc_description, sunrise, sunset,
        percent?.let { stringResource(R.string.aaj_arc_now, it) }.orEmpty(),
    )
    PpCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
            Text(dateText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(verdict, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(R.string.aaj_footer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DayArc(
                segments = arc.segments, nowFraction = arc.nowFraction, startLabel = sunrise, endLabel = sunset,
                goodLabel = stringResource(R.string.aaj_arc_good), waitLabel = stringResource(R.string.aaj_arc_wait),
                description = arcDescription,
            )
            PpButton(stringResource(R.string.aaj_how_calculated), onHow, PpButtonStyle.Outline)
        }
    }
}

private const val PERCENT = 100
