package app.parivaar.panchang.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.ChoiceRow
import app.parivaar.panchang.core.designsystem.components.ToggleRow
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.core.model.settings.ChartStyle
import app.parivaar.panchang.core.model.settings.PresetSource
import app.parivaar.panchang.engine.SunriseConvention
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.DashaYear

@Composable
internal fun ScreenTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = PpType.displayM,
        color = PpTheme.ratna.text,
        modifier = Modifier.semantics { heading() },
    )
}

/** A titled card of radio rows; a row is one 48 dp+ target that merges its label and explanation for TalkBack. */
@Composable
internal fun <T> ChoiceGroup(
    @StringRes title: Int,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes hint: Int? = null,
) {
    PpCard(modifier.fillMaxWidth(), mergeSemantics = false) {
        SectionTitle(title)
        if (hint != null) Hint(hint)
        for (c in choices) {
            val desc = c.desc?.let { stringResource(it) }
            RadioRow(stringResource(c.label), desc, c.value == selected) { onSelect(c.value) }
        }
    }
}

@Composable
internal fun SectionTitle(@StringRes res: Int) =
    Text(
        stringResource(res), style = MaterialTheme.typography.titleMedium, color = PpTheme.ratna.text,
        modifier = Modifier.semantics { heading() },
    )

@Composable
internal fun Hint(@StringRes res: Int) =
    Text(stringResource(res), style = MaterialTheme.typography.bodySmall, color = PpTheme.ratna.text2)

/** One radio row: a single 48 dp+ target merging label and explanation for TalkBack. */
@Composable
internal fun RadioRow(label: String, desc: String?, selected: Boolean, onClick: () -> Unit) =
    ChoiceRow(label, desc, selected, onClick)

/** A labelled switch row (one toggleable target, gold track when on). */
@Composable
internal fun SwitchRow(
    @StringRes label: Int,
    @StringRes hint: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = ToggleRow(stringResource(label), stringResource(hint), checked, onChange, modifier)

/** Settings → Conventions (J4). Stateless: [settings] in, writes through [model]. System back leaves the screen. */
@Composable
fun ConventionsScreen(
    settings: AppSettings,
    model: ConventionsModel,
    modifier: Modifier = Modifier,
    regionalEnabled: Boolean = false,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(PpSpace.l),
        verticalArrangement = Arrangement.spacedBy(PpSpace.l),
    ) {
        val shownAyanamsa = settings.defaultCalc.ayanamsa.takeIf { Ayanamsa.isEnabled(it) } ?: AyanamsaId.LAHIRI
        ScreenTitle(R.string.conventions_title)
        Text(
            stringResource(R.string.conventions_intro), style = MaterialTheme.typography.bodyMedium,
            color = PpTheme.ratna.text2,
        )
        ChoiceGroup(
            R.string.conventions_ayanamsa_title, ayanamsaChoices, shownAyanamsa, model::setAyanamsa,
        )
        ChoiceGroup(R.string.conventions_sunrise_title, sunriseChoices, settings.defaultCalc.sunrise, model::setSunrise)
        ChoiceGroup(
            R.string.conventions_month_title, monthChoices, settings.monthSystem, model::setMonthSystem,
            hint = R.string.conventions_default_hint.takeIf { settings.presetSource == PresetSource.NOT_SURE_DEFAULT },
        )
        ChoiceGroup(R.string.conventions_dasha_title, dashaChoices, settings.defaultCalc.dashaYear, model::setDashaYear)
        ChoiceGroup(R.string.conventions_ekadashi_title, ekadashiChoices, settings.ekadashi, model::setEkadashi)
        ChoiceGroup(
            R.string.conventions_calview_title, calendarViewChoices(regionalEnabled),
            if (regionalEnabled) settings.calendarView else CalendarView.STANDARD, model::setCalendarView,
        )
        ChoiceGroup(R.string.conventions_chart_title, chartChoices, settings.chartStyle, model::setChartStyle)
    }
}

@PpPreviews
@Composable
private fun ConventionsPreview() =
    PanchangTheme {
        ConventionsScreen(
            AppSettings(presetSource = PresetSource.NOT_SURE_DEFAULT),
            ConventionsModel(NoopSettings, kotlinx.coroutines.MainScope()),
        )
    }
