package app.parivaar.panchang.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.PanchangTheme
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
        style = MaterialTheme.typography.headlineSmall,
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
    PpCard(modifier.fillMaxWidth()) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (hint != null) {
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (c in choices) {
            val desc = c.desc?.let { stringResource(it) }
            RadioRow(stringResource(c.label), desc, c.value == selected) { onSelect(c.value) }
        }
    }
}

/** One radio row: a single 48 dp+ target merging label and explanation for TalkBack. */
@Composable
internal fun RadioRow(label: String, desc: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PpSpace.minTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = PpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A labelled switch row (one toggleable target). */
@Composable
internal fun SwitchRow(
    @StringRes label: Int,
    @StringRes hint: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PpSpace.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = PpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

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
        Text(stringResource(R.string.conventions_intro), style = MaterialTheme.typography.bodyMedium)
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
