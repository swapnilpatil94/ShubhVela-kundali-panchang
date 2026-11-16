@file:Suppress("TooManyFunctions")

package app.parivaar.panchang.feature.horoscope

import app.parivaar.panchang.content.horoscope.Period
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.LoadingState
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.engine.jyotish.Rashi

private val PERIOD_LABELS = mapOf(
    Period.DAY to R.string.rashifal_period_day,
    Period.WEEK to R.string.rashifal_period_week,
    Period.MONTH to R.string.rashifal_period_month,
    Period.YEAR to R.string.rashifal_period_year,
)
private val BASIS_LABELS = mapOf(
    Basis.MOON to R.string.rashifal_basis_moon,
    Basis.SUN to R.string.rashifal_basis_sun,
    Basis.LAGNA to R.string.rashifal_basis_lagna,
)

/** Route: carries no profile id or birth data; the view model reads the active profile. Sends the app language. */
@Composable
fun RashifalRoute(vm: RashifalViewModel, state: RashifalState, onBack: () -> Unit) {
    val lang = LocalConfiguration.current.locales[0].language
    LaunchedEffect(lang) { vm.onEvent(RashifalEvent.Language(lang)) }
    LaunchedEffect(Unit) { vm.onEvent(RashifalEvent.Refresh) }
    RashifalScreen(state, vm::onEvent, onBack)
}

/**
 * Rashifal (M11 I5): one hero (the reading), Day/Week/Month/Year tabs, basis picker, 12-sign selector and the
 * "Your day" card on top when a profile exists. Every reading carries the interpretation footer. No ads.
 */
@Composable
fun RashifalScreen(
    state: RashifalState,
    onEvent: (RashifalEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    // Same frame as the other sub-screens (calendar ScreenFrame): a fixed back row, then the scrolling content.
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(r.bg0, r.bg1)))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PpSpace.s, vertical = PpSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget)) {
                Text(stringResource(R.string.rashifal_back), color = r.text2)
            }
        }
        RashifalBody(state, onEvent)
    }
}

@Composable
private fun RashifalBody(state: RashifalState, onEvent: (RashifalEvent) -> Unit) {
    val r = PpTheme.ratna
    val names = stringArrayResource(R.array.rashifal_rashi_names)
    var how by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PpSpace.l)) {
        Text(stringResource(R.string.rashifal_eyebrow), style = PpType.eyebrow, color = r.gold)
        Text(
            stringResource(R.string.rashifal_title), style = PpType.displayL, color = r.text,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(PpSpace.m))
        YourDay(state)
        PillRow {
            Period.entries.forEach { p ->
                val label = stringResource(PERIOD_LABELS.getValue(p))
                Pill(label, p == state.period) { onEvent(RashifalEvent.PeriodTab(p)) }
            }
        }
        if (state.bases.isNotEmpty()) {
            Text(stringResource(R.string.rashifal_basis_label), style = PpType.meta, color = r.text2)
            PillRow {
                Basis.entries.filter { it in state.bases }.forEach { b ->
                    val label = stringResource(BASIS_LABELS.getValue(b))
                    Pill(label, b == state.basis) { onEvent(RashifalEvent.PickBasis(b)) }
                }
            }
        }
        Text(stringResource(R.string.rashifal_sign_label), style = PpType.meta, color = r.text2)
        PillRow {
            Rashi.entries.forEach { s -> Pill(names[s.ordinal], s == state.sign) { onEvent(RashifalEvent.Sign(s)) } }
        }
        Spacer(Modifier.height(PpSpace.m))
        Reading(state, names[state.sign.ordinal])
        if (state.showFallbackNote) {
            Text(stringResource(R.string.rashifal_fallback_note), style = PpType.meta, color = r.text2)
        }
        PpButton(stringResource(R.string.rashifal_how), { how = true }, PpButtonStyle.Outline)
        if (how) Text(stringResource(R.string.rashifal_how_body), style = PpType.meta, color = r.text2)
        Spacer(Modifier.height(BOTTOM_INSET))
    }
}

@Composable
private fun YourDay(state: RashifalState) {
    val r = PpTheme.ratna
    when (state.yourDayStatus) {
        YourDayStatus.SHOWN -> state.yourDay?.let { d ->
            Lacquer {
                Text(stringResource(R.string.rashifal_your_day), style = PpType.eyebrow, color = r.text3)
                Paragraphs(d.lines)
                Footer()
            }
            Spacer(Modifier.height(PpSpace.m))
        }
        YourDayStatus.NEEDS_BIRTH_TIME -> {
            Text(stringResource(R.string.rashifal_your_day_needs_time), style = PpType.meta, color = r.text2)
            Spacer(Modifier.height(PpSpace.s))
        }
        YourDayStatus.NO_PROFILE, YourDayStatus.UNAVAILABLE -> Unit
    }
}

@Composable
private fun Reading(state: RashifalState, signName: String) {
    val r = PpTheme.ratna
    val reading = state.reading
    when {
        state.noPlace && !state.loading -> EmptyState(
            stringResource(R.string.rashifal_no_place_title), stringResource(R.string.rashifal_no_place_body),
        )
        state.failed -> EmptyState(
            stringResource(R.string.rashifal_error_title), stringResource(R.string.rashifal_error_body),
        )
        reading == null -> LoadingState(stringResource(R.string.rashifal_loading))
        else -> Lacquer(hero = true) {
            Text(stringResource(R.string.rashifal_reading_for, signName), style = PpType.eyebrow, color = r.text3)
            // The sign is the display title; the reading itself is body text (never display size).
            Text(signName, style = PpType.displayS, color = r.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(PpSpace.s))
            Paragraphs(reading.lines)
            Footer()
        }
    }
}

/** Reading paragraphs in the body reading size with a PpSpace.s gap between them. */
@Composable
private fun Paragraphs(lines: List<String>) =
    Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
        lines.forEach { Text(it, style = READING, color = PpTheme.ratna.text) }
    }

@Composable
private fun Footer() =
    Text(
        stringResource(R.string.rashifal_footer), style = PpType.meta, color = PpTheme.ratna.text3,
        modifier = Modifier.padding(top = PpSpace.s),
    )

@Composable
private fun PillRow(content: @Composable () -> Unit) =
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = PpSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(PpSpace.s),
    ) { content() }

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val r = PpTheme.ratna
    Text(
        text,
        style = PpType.label,
        color = if (on) r.onGoldFill else r.text,
        modifier = Modifier
            .heightIn(min = PpSpace.minTouchTarget)
            .padding(vertical = PpSpace.xs)
            .border(1.dp, if (on) r.goldFill else r.line, PpShapes.pill)
            .then(if (on) Modifier.background(r.goldFill, PpShapes.pill) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = PpSpace.l, vertical = PpSpace.s10),
    )
}

private val BOTTOM_INSET = 96.dp

/** Body reading style at 16 sp (PpType.body is 14 sp). */
private val READING = PpType.body.copy(fontSize = 16.sp)
