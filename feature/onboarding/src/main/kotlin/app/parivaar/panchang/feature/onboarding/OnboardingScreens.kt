@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress(
    "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "TooManyFunctions", "MatchingDeclarationName",
)

package app.parivaar.panchang.feature.onboarding

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.geo.OffsetPickerModel
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.settings.ReminderKind
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import app.parivaar.panchang.core.designsystem.R as DsR
import app.parivaar.panchang.core.geo.R as GeoR

/** Things only the activity can do. B7 implements this with activity-result launchers and `LocaleApplier`. */
interface OnboardingPlatform {
    fun hasCoarseLocation(): Boolean

    fun requestCoarseLocation(onResult: (granted: Boolean) -> Unit)

    /** True on API 33+ when POST_NOTIFICATIONS is not yet granted. */
    fun needsNotificationPermission(): Boolean

    fun requestNotifications(onResult: (granted: Boolean) -> Unit)

    fun applyLocale(tag: String)
}

/** The whole flow. Call `machine.back()` from the activity's back handler; it returns false on step 1. */
@Composable
fun OnboardingHost(
    machine: OnboardingMachine,
    platform: OnboardingPlatform,
    onOpenPrivacy: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by machine.state.collectAsState()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rationale by remember { mutableStateOf(false) }
    val currentLanguage = LocalConfiguration.current.locales[0].language

    fun fetchLocation() {
        scope.launch {
            val fix = LocationOneShot.fetch(context)
            if (fix == null) machine.onLocationUnavailable() else machine.useApproximateLocation(fix.first, fix.second)
        }
    }

    Column(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()).padding(PpSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(PpSpace.l),
    ) {
        Text(
            stringResource(R.string.onb_step_progress, state.step.ordinal + 1, OnboardingStep.entries.size),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state.step) {
            OnboardingStep.LANGUAGE -> LanguageStep(state, machine, onOpenPrivacy)
            OnboardingStep.CITY -> CityStep(state, machine) { rationale = true }
            OnboardingStep.TRADITION -> TraditionStep(state, machine)
            OnboardingStep.BIRTH -> BirthStep(state, machine)
            OnboardingStep.REMINDERS -> RemindersStep(state, machine)
        }
        if (state.error == OnboardingError.SAVE_FAILED) {
            Text(stringResource(R.string.onb_save_failed), color = MaterialTheme.colorScheme.error)
        }
        PpButton(
            text = stringResource(if (state.step == OnboardingStep.REMINDERS) R.string.onb_finish else R.string.onb_continue),
            onClick = {
                when {
                    state.step == OnboardingStep.LANGUAGE -> {
                        machine.next()
                        if (state.localeTag != currentLanguage) platform.applyLocale(state.localeTag)
                    }
                    state.step == OnboardingStep.REMINDERS && state.reminders.isNotEmpty() &&
                        !state.notificationDenied && platform.needsNotificationPermission() ->
                        platform.requestNotifications { granted ->
                            machine.onNotificationResult(granted)
                            if (granted) machine.next()
                        }
                    else -> machine.next()
                }
            },
            enabled = state.canContinue,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.canSkip) PpButton(stringResource(R.string.onb_skip), machine::skip, PpButtonStyle.Tonal, modifier = Modifier.fillMaxWidth())
        if (state.step != OnboardingStep.LANGUAGE) {
            PpButton(stringResource(R.string.onb_back), { machine.back() }, PpButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
        }
    }

    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false },
            title = { Text(stringResource(R.string.onb_location_rationale_title)) },
            text = { Text(stringResource(R.string.onb_location_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    rationale = false
                    if (platform.hasCoarseLocation()) {
                        fetchLocation()
                    } else {
                        platform.requestCoarseLocation { granted -> if (granted) fetchLocation() else machine.onLocationDenied() }
                    }
                }) { Text(stringResource(R.string.onb_location_rationale_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { rationale = false }) { Text(stringResource(R.string.onb_location_rationale_cancel)) }
            },
        )
    }
}

@Composable
private fun StepTitle(title: Int, body: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Selected state is shown by a thicker border and a check icon, never colour alone. */
@Composable
private fun ChoiceCard(title: String, desc: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = PpSpace.minTouchTarget)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = PpShapes.medium,
        color = scheme.surfaceContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) PpTheme.colors.primaryText else scheme.outlineVariant),
    ) {
        Row(Modifier.padding(PpSpace.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (desc != null) Text(desc, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(painterResource(DsR.drawable.ic_quality_auspicious), contentDescription = stringResource(R.string.onb_language_selected), tint = PpTheme.colors.primaryText, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun LanguageStep(state: OnboardingState, machine: OnboardingMachine, onOpenPrivacy: () -> Unit) {
    StepTitle(R.string.onb_language_title, R.string.onb_language_body)
    LanguageOptions.all.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(PpSpace.s), modifier = Modifier.fillMaxWidth()) {
            row.forEach { opt ->
                ChoiceCard(stringResource(opt.nameRes), null, state.localeTag == opt.tag, { machine.setLocale(opt.tag) }, Modifier.weight(1f))
            }
        }
    }
    Text(stringResource(R.string.onb_privacy_promise), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    PpButton(stringResource(R.string.onb_privacy_link), onOpenPrivacy, PpButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun placeLabel(p: GeoPlace): String =
    if (p.admin1 == null) {
        stringResource(R.string.onb_place_line, p.name, p.countryCode)
    } else {
        stringResource(R.string.onb_place_line_admin, p.name, p.admin1!!, p.countryCode)
    }

@Composable
private fun PlacePicker(picker: PlacePickerState, onQuery: (String) -> Unit, onChoose: (GeoPlace) -> Unit) {
    val chosen = picker.selected
    if (chosen != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            Icon(painterResource(DsR.drawable.ic_location), contentDescription = null, tint = PpTheme.colors.primaryText)
            Text(stringResource(R.string.onb_city_chosen, placeLabel(chosen)), style = MaterialTheme.typography.titleMedium)
        }
    }
    OutlinedTextField(
        value = picker.query, onValueChange = onQuery, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.onb_city_search_label)) },
        supportingText = if (picker.query.trim().length == 1) ({ Text(stringResource(R.string.onb_city_hint)) }) else null,
    )
    picker.results.forEach { hit ->
        val label = placeLabel(hit.place)
        Text(
            label, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().heightIn(min = PpSpace.minTouchTarget)
                .clickable(role = Role.Button) { onChoose(hit.place) }.padding(vertical = PpSpace.m),
        )
    }
}

@Composable
private fun CityStep(state: OnboardingState, machine: OnboardingMachine, onUseLocation: () -> Unit) {
    StepTitle(R.string.onb_city_title, R.string.onb_city_body)
    PlacePicker(state.home, machine::setQuery, machine::choosePlace)
    PpButton(stringResource(R.string.onb_city_use_location), onUseLocation, PpButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
    state.locationMessage?.let {
        val res = when (it) {
            LocationMessage.DENIED -> R.string.onb_location_denied
            LocationMessage.UNAVAILABLE -> R.string.onb_location_unavailable
            LocationMessage.NO_MATCH -> R.string.onb_location_no_match
        }
        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TraditionStep(state: OnboardingState, machine: OnboardingMachine) {
    StepTitle(R.string.onb_tradition_title, R.string.onb_tradition_body)
    val options = listOf(
        Triple(TraditionChoice.NORTH, R.string.onb_tradition_north, R.string.onb_tradition_north_desc),
        Triple(TraditionChoice.SOUTH_WEST, R.string.onb_tradition_south, R.string.onb_tradition_south_desc),
        Triple(TraditionChoice.NOT_SURE, R.string.onb_tradition_notsure, R.string.onb_tradition_notsure_desc),
    )
    options.forEach { (choice, title, desc) ->
        ChoiceCard(stringResource(title), stringResource(desc), state.tradition == choice, { machine.setTradition(choice) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun formatDate(date: LocalDate): String {
    val millis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, LocalConfiguration.current.locales[0])
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date(millis))
}

@Composable
private fun BirthStep(state: OnboardingState, machine: OnboardingMachine) {
    var datePicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    StepTitle(R.string.onb_birth_title, R.string.onb_birth_body)
    Text(stringResource(R.string.onb_birth_date), style = MaterialTheme.typography.titleMedium)
    PpButton(
        state.birthDate?.let { formatDate(it) } ?: stringResource(R.string.onb_birth_date_pick),
        { datePicker = true }, PpButtonStyle.Outline, modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.onb_birth_time), style = MaterialTheme.typography.titleMedium)
    PpButton(
        state.birthTime?.let { stringResource(R.string.onb_birth_time_value, it.hour, it.minute) }
            ?: stringResource(R.string.onb_birth_time_pick),
        { timePicker = true }, PpButtonStyle.Outline, enabled = !state.timeUnknown, modifier = Modifier.fillMaxWidth(),
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = PpSpace.minTouchTarget)
            .toggleable(state.timeUnknown, role = Role.Checkbox, onValueChange = machine::setTimeUnknown),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = state.timeUnknown, onCheckedChange = null)
        Text(stringResource(R.string.onb_birth_time_unknown), Modifier.padding(start = PpSpace.s))
    }
    Text(stringResource(R.string.onb_birth_place), style = MaterialTheme.typography.titleMedium)
    if (state.home.selected != null && state.birthPlace.selected == null) {
        PpButton(stringResource(R.string.onb_birth_same_place), machine::useHomeAsBirthPlace, PpButtonStyle.Tonal, modifier = Modifier.fillMaxWidth())
    }
    PlacePicker(state.birthPlace, machine::setBirthQuery, machine::chooseBirthPlace)
    when (state.error) {
        OnboardingError.BIRTH_INCOMPLETE -> Text(stringResource(R.string.onb_birth_incomplete), color = MaterialTheme.colorScheme.error)
        OnboardingError.BIRTH_DATE_INVALID -> Text(stringResource(R.string.onb_birth_date_invalid), color = MaterialTheme.colorScheme.error)
        else -> Unit
    }
    if (datePicker) {
        val picker = rememberDatePickerState(yearRange = 1900..Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year)
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        machine.setBirthDate(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                    datePicker = false
                }) { Text(stringResource(R.string.onb_dialog_ok)) }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text(stringResource(R.string.onb_dialog_cancel)) } },
        ) { DatePicker(picker) }
    }
    if (timePicker) {
        val picker = rememberTimePickerState(is24Hour = DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { timePicker = false },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(picker) } },
            confirmButton = {
                TextButton(onClick = {
                    machine.setBirthTime(LocalTime(picker.hour, picker.minute))
                    timePicker = false
                }) { Text(stringResource(R.string.onb_dialog_ok)) }
            },
            dismissButton = { TextButton(onClick = { timePicker = false }) { Text(stringResource(R.string.onb_dialog_cancel)) } },
        )
    }
    BirthPrompts(state, machine)
}

@Composable
private fun BirthPrompts(state: OnboardingState, machine: OnboardingMachine) {
    when (state.birthPrompt) {
        BirthPrompt.NONE -> Unit
        BirthPrompt.AMBIGUOUS_TIME -> AlertDialog(
            onDismissRequest = machine::dismissBirthPrompt,
            title = { Text(stringResource(R.string.onb_ambiguous_title)) },
            text = { Text(stringResource(R.string.onb_ambiguous_body)) },
            confirmButton = {
                TextButton(onClick = { machine.chooseAmbiguity(AmbiguityChoice.LATER) }) { Text(stringResource(R.string.onb_ambiguous_later)) }
            },
            dismissButton = {
                TextButton(onClick = { machine.chooseAmbiguity(AmbiguityChoice.EARLIER) }) { Text(stringResource(R.string.onb_ambiguous_earlier)) }
            },
        )
        BirthPrompt.UNCERTAIN_OFFSET -> UncertainOffsetDialog(machine)
    }
}

@Composable
private fun UncertainOffsetDialog(machine: OnboardingMachine) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<OffsetPickerModel.ErrorCode?>(null) }
    AlertDialog(
        onDismissRequest = machine::dismissBirthPrompt,
        title = { Text(stringResource(R.string.onb_uncertain_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                Text(stringResource(R.string.onb_uncertain_body))
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, isError = error != null,
                    label = { Text(stringResource(GeoR.string.geo_picker_label)) },
                    supportingText = {
                        error?.let {
                            Text(
                                stringResource(
                                    when (it) {
                                        OffsetPickerModel.ErrorCode.EMPTY -> GeoR.string.geo_picker_error_empty
                                        OffsetPickerModel.ErrorCode.FORMAT -> GeoR.string.geo_picker_error_format
                                        OffsetPickerModel.ErrorCode.OUT_OF_RANGE -> GeoR.string.geo_picker_error_range
                                    },
                                ),
                            )
                        }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = { error = machine.setManualOffset(text) }) { Text(stringResource(R.string.onb_uncertain_enter)) } },
        dismissButton = { TextButton(onClick = machine::acceptUncertainOffset) { Text(stringResource(R.string.onb_uncertain_keep)) } },
    )
}

@Composable
private fun RemindersStep(state: OnboardingState, machine: OnboardingMachine) {
    StepTitle(R.string.onb_reminders_title, R.string.onb_reminders_body)
    val labels = listOf(
        ReminderKind.VRAT_EVE to R.string.onb_reminder_vrat_eve,
        ReminderKind.FAMILY_DATES to R.string.onb_reminder_family_dates,
        ReminderKind.GRAHAN to R.string.onb_reminder_grahan,
        ReminderKind.SUNRISE_BRAHMA to R.string.onb_reminder_sunrise_brahma,
    )
    labels.forEach { (kind, label) ->
        val on = kind in state.reminders
        Row(
            Modifier.fillMaxWidth().heightIn(min = PpSpace.minTouchTarget)
                .toggleable(on, role = Role.Switch) { machine.setReminder(kind, it) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.m),
        ) {
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = on, onCheckedChange = null)
        }
    }
    if (state.notificationDenied) Text(stringResource(R.string.onb_reminders_denied), style = MaterialTheme.typography.bodyMedium)
}
