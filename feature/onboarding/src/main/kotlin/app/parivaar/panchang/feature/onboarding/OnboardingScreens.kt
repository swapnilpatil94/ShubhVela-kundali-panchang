@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress(
    "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "TooManyFunctions", "MatchingDeclarationName",
)

package app.parivaar.panchang.feature.onboarding

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import app.parivaar.panchang.core.designsystem.sky.SkyHeaderSpec
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import app.parivaar.panchang.core.designsystem.components.ToggleRow
import app.parivaar.panchang.core.designsystem.sky.SkyHeader
import app.parivaar.panchang.core.designsystem.sky.SkyHeaderUi
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.gemFor
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
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

private val ONBOARDING_HEADER_HEIGHT = 240.dp

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

    // Step 1 has the dark sky behind the status bar, later steps follow the mood.
    app.parivaar.panchang.core.designsystem.components.StatusBarIcons(
        darkIcons = !PpTheme.ratna.isDark && state.step != OnboardingStep.LANGUAGE,
    )
    val progress = stringResource(R.string.onb_step_progress, state.step.ordinal + 1, OnboardingStep.entries.size)
    Column(
        modifier.fillMaxSize()
            // Step 1 lets its sky run behind the status bar; later steps pad below it.
            .windowInsetsPadding(
                if (state.step == OnboardingStep.LANGUAGE) {
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                } else {
                    WindowInsets.safeDrawing
                },
            ).imePadding(),
    ) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PpSpace.l),
    ) {
        val pad = Modifier.padding(horizontal = PpSpace.screen)
        if (state.step == OnboardingStep.LANGUAGE) {
            // Sky header on step 1 only (spec M07R section 3).
            val gem = remember { gemFor(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek) }
            val ui = SkyHeaderUi(
                gem, progress.uppercase(), stringResource(R.string.onb_language_title),
                AnnotatedString(stringResource(R.string.onb_language_body)),
            )
            SkyHeader(ui, minHeight = ONBOARDING_HEADER_HEIGHT)
        } else {
            Text(
                progress.uppercase(), style = PpType.eyebrow, color = PpTheme.ratna.text2,
                modifier = pad.padding(top = PpSpace.xxl),
            )
        }
        Column(pad.padding(bottom = PpSpace.l), verticalArrangement = Arrangement.spacedBy(PpSpace.l)) {
        when (state.step) {
            OnboardingStep.LANGUAGE -> LanguageStep(state, machine, onOpenPrivacy)
            OnboardingStep.CITY -> CityStep(state, machine) { rationale = true }
            OnboardingStep.TRADITION -> TraditionStep(state, machine)
            OnboardingStep.BIRTH -> BirthStep(state, machine)
            OnboardingStep.REMINDERS -> RemindersStep(state, machine)
        }
        if (state.error == OnboardingError.SAVE_FAILED) {
            Text(stringResource(R.string.onb_save_failed), color = PpTheme.ratna.kumkum)
        }
        }
    }
    // Pinned action bar: always visible, content scrolls above it.
    Column(
        Modifier.fillMaxWidth().padding(horizontal = PpSpace.screen, vertical = PpSpace.s),
        verticalArrangement = Arrangement.spacedBy(PpSpace.xs),
    ) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            if (state.step != OnboardingStep.LANGUAGE) {
                PpButton(stringResource(R.string.onb_back), { machine.back() }, PpButtonStyle.Outline, modifier = Modifier.weight(1f))
            }
            if (state.canSkip) PpButton(stringResource(R.string.onb_skip), machine::skip, PpButtonStyle.Tonal, modifier = Modifier.weight(1f))
        }
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
    Text(stringResource(title), style = PpType.displayL, color = PpTheme.ratna.text, modifier = Modifier.semantics { heading() })
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = PpTheme.ratna.text2)
}

/** Selected state is a gold border plus a check icon, never colour alone. Lacquer-look tile, radio semantics. */
@Composable
private fun ChoiceCard(title: String, desc: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    val border = if (selected) BorderStroke(1.5.dp, r.gold) else BorderStroke(1.dp, r.line)
    Row(
        modifier.heightIn(min = PpSpace.minTouchTarget).clip(PpShapes.lacquer)
            .background(Brush.verticalGradient(listOf(r.lacquerTop, r.lacquerBottom)))
            .border(border, PpShapes.lacquer)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = PpSpace.l, vertical = PpSpace.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PpSpace.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = r.text)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodyMedium, color = r.text2)
        }
        if (selected) {
            Icon(painterResource(DsR.drawable.ic_quality_auspicious), contentDescription = stringResource(R.string.onb_language_selected), tint = r.gold, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LanguageStep(state: OnboardingState, machine: OnboardingMachine, onOpenPrivacy: () -> Unit) {
    LanguageOptions.all.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(PpSpace.s), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            row.forEach { opt ->
                ChoiceCard(stringResource(opt.nameRes), null, state.localeTag == opt.tag, { machine.setLocale(opt.tag) }, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
    Text(stringResource(R.string.onb_privacy_promise), style = MaterialTheme.typography.bodyMedium, color = PpTheme.ratna.text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth().heightIn(min = PpSpace.minTouchTarget)) {
        Text(stringResource(R.string.onb_privacy_link), color = PpTheme.ratna.gold, style = MaterialTheme.typography.labelLarge)
    }
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
            Text(stringResource(R.string.onb_city_chosen, placeLabel(chosen)), style = MaterialTheme.typography.titleMedium, color = PpTheme.ratna.text)
        }
    }
    OutlinedTextField(
        value = picker.query, onValueChange = onQuery, singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = PpShapes.lacquer,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PpTheme.ratna.gold, unfocusedBorderColor = PpTheme.ratna.text3,
            focusedContainerColor = PpTheme.ratna.ink3, unfocusedContainerColor = PpTheme.ratna.ink3,
        ),
        label = { Text(stringResource(R.string.onb_city_search_label)) },
        supportingText = if (picker.query.trim().length == 1) ({ Text(stringResource(R.string.onb_city_hint)) }) else null,
    )
    picker.results.forEach { hit ->
        val label = placeLabel(hit.place)
        Text(
            label, style = MaterialTheme.typography.bodyLarge, color = PpTheme.ratna.text,
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
        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = PpTheme.ratna.text2)
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
    Text(stringResource(R.string.onb_birth_date), style = MaterialTheme.typography.titleMedium, color = PpTheme.ratna.text)
    PpButton(
        state.birthDate?.let { formatDate(it) } ?: stringResource(R.string.onb_birth_date_pick),
        { datePicker = true }, PpButtonStyle.Outline, modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.onb_birth_time), style = MaterialTheme.typography.titleMedium, color = PpTheme.ratna.text)
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
        Text(stringResource(R.string.onb_birth_time_unknown), Modifier.padding(start = PpSpace.s), color = PpTheme.ratna.text)
    }
    Text(stringResource(R.string.onb_birth_place), style = MaterialTheme.typography.titleMedium, color = PpTheme.ratna.text)
    if (state.home.selected != null && state.birthPlace.selected == null) {
        PpButton(stringResource(R.string.onb_birth_same_place), machine::useHomeAsBirthPlace, PpButtonStyle.Tonal, modifier = Modifier.fillMaxWidth())
    }
    PlacePicker(state.birthPlace, machine::setBirthQuery, machine::chooseBirthPlace)
    when (state.error) {
        OnboardingError.BIRTH_INCOMPLETE -> Text(stringResource(R.string.onb_birth_incomplete), color = PpTheme.ratna.kumkum)
        OnboardingError.BIRTH_DATE_INVALID -> Text(stringResource(R.string.onb_birth_date_invalid), color = PpTheme.ratna.kumkum)
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
        ToggleRow(stringResource(label), null, kind in state.reminders, onChange = { machine.setReminder(kind, it) })
    }
    if (state.notificationDenied) Text(stringResource(R.string.onb_reminders_denied), style = MaterialTheme.typography.bodyMedium, color = PpTheme.ratna.text2)
}

@app.parivaar.panchang.core.designsystem.preview.PpPreviews
@Composable
private fun LanguageTilesPreview() = app.parivaar.panchang.core.designsystem.PanchangTheme {
    Column(Modifier.padding(PpSpace.l), verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(PpSpace.s), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            ChoiceCard(stringResource(R.string.lang_hi), null, true, {}, Modifier.weight(1f).fillMaxHeight())
            ChoiceCard(stringResource(R.string.lang_en), null, false, {}, Modifier.weight(1f).fillMaxHeight())
        }
        ChoiceCard(stringResource(R.string.onb_tradition_north), stringResource(R.string.onb_tradition_north_desc), true, {}, Modifier.fillMaxWidth())
        ToggleRow(stringResource(R.string.onb_reminder_vrat_eve), null, true, onChange = {})
    }
}
