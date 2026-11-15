@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.feature.aaj.R

/** A label and a value on one line (wraps at large font scales). */
@Composable
fun FactRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PpSpace.m), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

/** One limb (tithi, nakshatra, ...) with each span's end time in the place's local time. */
@Composable
fun LimbCard(kind: LimbKind, spans: List<LimbSpan>, day: PanchangDay, names: AajNames, fmt: TimeFmt) {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            Text(
                names.limbTitle(kind), style = MaterialTheme.typography.titleMedium, color = PpTheme.colors.primaryText,
                modifier = Modifier.semantics { heading() },
            )
            spans.forEach { span ->
                val name = names.limbSpan(kind, span.index)
                // A span that began before sunrise shows its start too, so it never reads as ending early today.
                val until = if (span.start < day.sunrise) {
                    res.getString(R.string.aaj_limb_from_until, name, fmt.stamp(res, span.start), fmt.stamp(res, span.end))
                } else {
                    res.getString(R.string.aaj_limb_until, name, fmt.stamp(res, span.end))
                }
                Text(until, style = MaterialTheme.typography.bodyLarge)
                if (span.kshaya) Text(stringResource(R.string.aaj_badge_kshaya) + ": " + stringResource(R.string.aaj_badge_kshaya_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (span.adhika) Text(stringResource(R.string.aaj_badge_adhika) + ": " + stringResource(R.string.aaj_badge_adhika_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
