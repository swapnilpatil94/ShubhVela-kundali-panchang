@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

/**
 * One time window: name, range, quality chip and an optional live [countdown]. An [activeLabel] marks the window
 * that is running now (text, not colour alone). A window without data passes its "not available yet" text as [range].
 */
@Composable
fun TimeWindowCard(
    title: String,
    range: String,
    quality: TimeQuality,
    qualityLabel: String,
    modifier: Modifier = Modifier,
    countdown: String? = null,
    activeLabel: String? = null,
    note: String? = null,
) {
    PpCard(
        modifier.fillMaxWidth().semantics { activeLabel?.let { stateDescription = it } },
        warn = quality == TimeQuality.INAUSPICIOUS && activeLabel != null,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(range, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
                PpChip(qualityLabel, quality)
                if (activeLabel != null) {
                    Text(activeLabel, style = MaterialTheme.typography.labelLarge, color = PpTheme.colors.primaryText)
                }
                if (countdown != null) {
                    Text(
                        countdown, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End,
                        modifier = Modifier.padding(top = PpSpace.xs),
                    )
                }
            }
        }
    }
}

@PpPreviews
@Composable
private fun TimeWindowCardPreview() = PanchangTheme {
    Column(Modifier.padding(PpSpace.l), verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
        TimeWindowCard(
            "Rahu kaal", "3:00 PM - 4:30 PM", TimeQuality.INAUSPICIOUS, "Wait", countdown = "0:42:10",
            activeLabel = "Now",
        )
        TimeWindowCard("Abhijit muhurta", "11:55 AM - 12:42 PM", TimeQuality.AUSPICIOUS, "Good time")
        TimeWindowCard("Varjyam", "Not available yet", TimeQuality.NEUTRAL, "Not rated")
    }
}
