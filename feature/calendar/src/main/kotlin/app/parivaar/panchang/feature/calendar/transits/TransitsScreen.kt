@file:Suppress("MaxLineLength", "MatchingDeclarationName", "MagicNumber") // Compose layout code and preview data

package app.parivaar.panchang.feature.calendar.transits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.Masa
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.feature.aaj.ui.AajNames
import app.parivaar.panchang.feature.calendar.R
import app.parivaar.panchang.feature.calendar.TransitEvent
import app.parivaar.panchang.feature.calendar.common.CalFormat
import app.parivaar.panchang.feature.calendar.common.ScreenFrame
import app.parivaar.panchang.feature.calendar.common.previewFormat
import app.parivaar.panchang.feature.calendar.common.previewNames
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Pure ordering: events by the day they fall on (stations at day resolution), then by graha. */
object TransitRows {
    fun sorted(events: List<TransitEvent>, dateOf: (Instant) -> LocalDate): List<TransitEvent> =
        events.sortedWith(
            compareBy<TransitEvent> {
                when (it) {
                    is TransitEvent.SignChange -> dateOf(it.at)
                    is TransitEvent.Station -> it.date
                }
            }.thenBy { it.graha.ordinal },
        )
}

/**
 * Stateless month transits list (Dest.Transits). [monthLabel] is the formatted month; [adhika] comes from
 * `MonthHeader.adhika`. Retrograde turn dates are day-resolution and shown as "around <date>".
 */
@Composable
fun TransitsScreen(
    monthLabel: String,
    events: List<TransitEvent>,
    adhika: LunarLabel?,
    names: AajNames,
    fmt: CalFormat,
    onMonth: (Int) -> Unit,
    onBack: () -> Unit,
) {
    ScreenFrame(stringResource(R.string.transits_title), stringResource(R.string.guide_back), onBack) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton({ onMonth(-1) }, Modifier.heightIn(min = PpSpace.minTouchTarget)) {
                Text(stringResource(R.string.transits_prev_month))
            }
            Text(monthLabel, style = PpType.displayS, color = PpTheme.ratna.text)
            TextButton({ onMonth(1) }, Modifier.heightIn(min = PpSpace.minTouchTarget)) {
                Text(stringResource(R.string.transits_next_month))
            }
        }
        adhika?.let { Text(stringResource(R.string.transits_adhika, names.masa(it.masa))) }
        if (events.isEmpty()) Text(stringResource(R.string.transits_empty))
        for (e in events) {
            PpCard(Modifier.fillMaxWidth()) {
                Text(line(e, names, fmt), style = PpType.body, color = PpTheme.ratna.text)
            }
        }
        Text(stringResource(R.string.transits_footer), style = PpType.meta, color = PpTheme.ratna.text3)
    }
}

@Composable
private fun line(e: TransitEvent, names: AajNames, fmt: CalFormat): String = when (e) {
    is TransitEvent.SignChange -> stringResource(
        if (e.retrograde) R.string.transits_sign_change_retro else R.string.transits_sign_change,
        names.graha(e.graha.name), names.rashi(e.into), fmt.dateTime(e.at),
    )
    is TransitEvent.Station -> stringResource(
        if (e.turnsRetrograde) R.string.transits_station_retro else R.string.transits_station_direct,
        names.graha(e.graha.name), fmt.date(e.date),
    )
}

@PpPreviews
@Composable
private fun TransitsPreview() = PanchangTheme {
    val ev = listOf(
        TransitEvent.SignChange(Graha.SUN, Rashi.VRISHCHIKA, Instant.parse("2026-11-16T10:00:00Z"), false),
        TransitEvent.Station(Graha.MERCURY, true, LocalDate(2026, 11, 20)),
    )
    TransitsScreen(
        "November 2026", ev, LunarLabel(Masa.ASHVINA, true, null), previewNames(),
        previewFormat(), {}, {},
    )
}
