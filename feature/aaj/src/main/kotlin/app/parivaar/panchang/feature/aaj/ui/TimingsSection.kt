@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.components.PpChip
import app.parivaar.panchang.core.designsystem.components.TimeQuality
import app.parivaar.panchang.core.designsystem.components.TimeWindowCard
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.AajUi
import app.parivaar.panchang.feature.aaj.NextWindowRule
import app.parivaar.panchang.feature.aaj.R
import kotlin.time.Instant

@Composable
private fun SectionHeading(text: String) = Text(
    text, style = MaterialTheme.typography.titleLarge, color = PpTheme.colors.primaryText,
    modifier = Modifier.semantics { heading() },
)

@Composable
private fun qualityLabel(q: TimeQuality) = when (q) {
    TimeQuality.AUSPICIOUS -> stringResource(R.string.aaj_arc_good)
    TimeQuality.INAUSPICIOUS -> stringResource(R.string.aaj_arc_wait)
    TimeQuality.NEUTRAL -> stringResource(R.string.aaj_not_rated)
}

/** Tier 2: the next or active window with a live countdown, then today's windows and the day parts. */
@Composable
internal fun TimingsSection(
    c: AajUi.Content,
    now: Instant,
    names: AajNames,
    fmt: TimeFmt,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val day = c.day
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
        SectionHeading(stringResource(R.string.aaj_section_timings))
        // Recomputed here from `now` so the countdown stays right between model refreshes.
        val next = NextWindowRule.next(now, c.days)
        if (next != null) {
            val w = next.window
            val q = qualityOf(w.kind)
            TimeWindowCard(
                title = names.window(w.kind), range = fmt.range(res, w.start, w.end), quality = q,
                qualityLabel = qualityLabel(q),
                countdown = if (next.active) countdownText(res, w.end - now, true) else countdownText(res, w.start - now, false),
                activeLabel = if (next.active) stringResource(R.string.aaj_now) else null,
            )
        }
        SectionHeading(stringResource(R.string.aaj_section_windows))
        shownWindows(day).forEach { w ->
            val q = qualityOf(w.kind)
            TimeWindowCard(
                names.window(w.kind), fmt.range(res, w.start, w.end), q, qualityLabel(q),
                activeLabel = if (w.start <= now && now < w.end) stringResource(R.string.aaj_now) else null,
            )
        }
        c.unavailable.sorted().forEach { id ->
            TimeWindowCard(
                names.unavailable(id), stringResource(R.string.aaj_not_available), TimeQuality.NEUTRAL,
                qualityLabel(TimeQuality.NEUTRAL),
            )
        }
        PartsCard(day, false, now, names, fmt)
        PartsCard(day, true, now, names, fmt)
        HoraCard(day, now, names, fmt)
    }
}

@Composable
private fun WindowRow(title: String, range: String, quality: TimeQuality, now: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(range, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (now) Text(stringResource(R.string.aaj_now), style = MaterialTheme.typography.labelLarge, color = PpTheme.colors.primaryText)
        PpChip(qualityLabel(quality), quality)
    }
}

@Composable
private fun PartsCard(day: PanchangDay, night: Boolean, now: Instant, names: AajNames, fmt: TimeFmt) {
    val res = LocalContext.current.resources
    val parts = day.windows.filter { it.kind == WindowKind.CHOGHADIYA && it.night == night }.sortedBy { it.start }
    if (parts.isEmpty()) return
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
            Text(
                stringResource(if (night) R.string.aaj_section_choghadiya_night else R.string.aaj_section_choghadiya_day),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            parts.forEach { w: Window ->
                WindowRow(names.choghadiyaWithLord(w.code), fmt.range(res, w.start, w.end), qualityOfChoghadiya(w.code), w.start <= now && now < w.end)
            }
        }
    }
}

@Composable
private fun HoraCard(day: PanchangDay, now: Instant, names: AajNames, fmt: TimeFmt) {
    val res = LocalContext.current.resources
    var open by rememberSaveable { mutableStateOf(false) }
    val horas = day.windows.filter { it.kind == WindowKind.HORA }.sortedBy { it.start }
    if (horas.isEmpty()) return
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
            Text(
                stringResource(R.string.aaj_section_hora), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (open) {
                horas.forEach { w ->
                    WindowRow(names.graha(w.code), fmt.range(res, w.start, w.end), TimeQuality.NEUTRAL, w.start <= now && now < w.end)
                }
            }
            PpButton(
                stringResource(if (open) R.string.aaj_collapse else R.string.aaj_expand), { open = !open },
                PpButtonStyle.Tonal,
            )
        }
    }
}
