@file:Suppress("MaxLineLength", "MatchingDeclarationName", "MagicNumber") // Compose layout code and preview data

package app.parivaar.panchang.feature.calendar.shouldi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.feature.calendar.R
import app.parivaar.panchang.feature.calendar.common.CalFormat
import app.parivaar.panchang.feature.calendar.common.ScreenFrame
import app.parivaar.panchang.feature.calendar.common.previewFormat
import app.parivaar.panchang.feature.calendar.common.previewNames
import kotlin.time.Instant

/**
 * Stateless "Should I...?" screen (Dest.ShouldI). Answer-first: verdict, best window, then the expandable reasons
 * and the mandatory footer. [selected] and [answer] are held by the caller; [query] is local UI state.
 */
@Composable
fun ShouldIScreen(
    intentIds: List<String>,
    selected: String?,
    answer: ShouldIAnswer?,
    fmt: CalFormat,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ScreenFrame(stringResource(R.string.should_i_title), stringResource(R.string.should_i_back), onBack) {
        if (selected != null && answer != null) {
            AnswerCard(answer, fmt)
        } else {
            Text(stringResource(R.string.should_i_pick_prompt), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.should_i_search_hint)) }, modifier = Modifier.fillMaxWidth(),
        )
        val shown = intentIds.filter { id ->
            val res = ShouldILabels.label(id)
            res != null && (query.isBlank() || matches(res, query))
        }
        if (shown.isEmpty()) Text(stringResource(R.string.should_i_no_match))
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
            for (id in shown) {
                val res = ShouldILabels.label(id) ?: continue
                TextButton(
                    onClick = { onSelect(id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = PpSpace.minTouchTarget),
                ) { Text(stringResource(res), modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun matches(labelRes: Int, query: String): Boolean =
    stringResource(labelRes).lowercase().contains(query.trim().lowercase())

@Composable
private fun AnswerCard(a: ShouldIAnswer, fmt: CalFormat) {
    var open by rememberSaveable { mutableStateOf(false) }
    val r = PpTheme.ratna
    // One hero per screen: the verdict. Gold border only for a good window; kumkum for not favoured (words always carry the meaning).
    val good = a.verdict == Verdict.GOOD_NOW || a.verdict == Verdict.GOOD_LATER || a.verdict == Verdict.TOMORROW
    Lacquer(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, hero = good, warn = a.verdict == Verdict.NOT_FAVOURED_TODAY) {
        Text(verdictLine(a, fmt), style = PpType.displayM, color = r.text)
        val best = a.best
        if (best != null && (a.verdict == Verdict.GOOD_NOW || a.verdict == Verdict.TOMORROW)) {
            Text(
                stringResource(R.string.should_i_best_window, range(best, fmt)),
                modifier = Modifier.padding(top = PpSpace.xs),
            )
        }
        if (a.whyIds.isNotEmpty()) {
            TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget)) {
                Text(stringResource(if (open) R.string.should_i_why_hide else R.string.should_i_why_show))
            }
            if (open) {
                for (id in a.whyIds) ShouldILabels.why(id)?.let { Text(stringResource(it)) }
            }
        }
        Text(
            stringResource(R.string.should_i_footer), style = PpType.meta, color = r.text3,
            modifier = Modifier.padding(top = PpSpace.s),
        )
    }
}

@Composable
private fun range(r: TimeRange, fmt: CalFormat) =
    stringResource(R.string.should_i_range, fmt.time(r.start), fmt.time(r.end))

@Composable
private fun verdictLine(a: ShouldIAnswer, fmt: CalFormat): String {
    val best = a.best
    return when {
        a.verdict == Verdict.GOOD_NOW && best != null -> stringResource(R.string.should_i_verdict_good_now, fmt.time(best.end))
        a.verdict == Verdict.GOOD_LATER && best != null -> stringResource(R.string.should_i_verdict_later, range(best, fmt))
        a.verdict == Verdict.TOMORROW && best != null -> stringResource(R.string.should_i_verdict_tomorrow, fmt.time(best.start))
        a.verdict == Verdict.NOT_FAVOURED_TODAY -> stringResource(R.string.should_i_verdict_not_favoured)
        else -> stringResource(R.string.should_i_verdict_unavailable)
    }
}

@PpPreviews
@Composable
private fun ShouldIPreview() = PanchangTheme {
    val t = Instant.parse("2026-11-15T10:10:00Z")
    ShouldIScreen(
        listOf("vehicle_purchase", "haircut", "puja_start"), "haircut",
        ShouldIAnswer(Verdict.GOOD_LATER, TimeRange(t, Instant.parse("2026-11-15T11:40:00Z")), listOf("why_abhijit")),
        previewFormat(), {}, {},
    )
}
