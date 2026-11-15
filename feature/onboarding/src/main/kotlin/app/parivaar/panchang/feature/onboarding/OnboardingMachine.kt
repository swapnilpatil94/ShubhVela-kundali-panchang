package app.parivaar.panchang.feature.onboarding

import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.PlaceRepository
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.data.newRecordId
import app.parivaar.panchang.core.geo.OffsetPickerModel
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.geo.UnknownZoneException
import app.parivaar.panchang.core.model.geo.tzContext
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.profile.BirthData
import app.parivaar.panchang.core.model.profile.PlaceSnapshot
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.core.model.settings.PresetSource
import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.engine.calendar.MonthSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.security.SecureRandom
import kotlin.time.Clock

/**
 * The five onboarding steps. Each Continue persists that step's choice. Search results, place lookups and the
 * approximate fix live in memory only: the user's own coordinates are never stored (only the matched [GeoPlace]).
 * Hold this in an activity-scoped ViewModel so it survives `recreate()`.
 */
@Suppress("LongParameterList", "TooManyFunctions", "ReturnCount")
class OnboardingMachine(
    private val search: PlaceSearch,
    private val offsets: OffsetResolver,
    private val settings: SettingsRepository,
    private val places: PlaceRepository,
    private val profiles: ProfileRepository,
    private val clock: Clock,
    private val rng: SecureRandom,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val selfName: String,
    deviceLanguageTags: List<String>,
    private val debounceMs: Long = SEARCH_DEBOUNCE_MS,
) {
    private val _state = MutableStateFlow(OnboardingState(localeTag = LanguageOptions.preselect(deviceLanguageTags)))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var homeSearch: Job? = null
    private var birthSearch: Job? = null
    private var working = false
    private var homeRecordId: RecordId? = null
    private var selfRecordId: RecordId? = null
    private var ambiguity: AmbiguityChoice? = null
    private var uncertainAcknowledged = false

    // Step 1
    fun setLocale(tag: String) {
        if (LanguageOptions.all.any { it.tag == tag }) _state.update { it.copy(localeTag = tag) }
    }

    // Step 2
    fun setQuery(q: String) {
        _state.update { it.copy(home = it.home.copy(query = q), locationMessage = null) }
        homeSearch?.cancel()
        homeSearch = scope.launch {
            runSearch(q) { r -> _state.update { s -> s.copy(home = s.home.copy(results = r)) } }
        }
    }

    fun choosePlace(place: GeoPlace) {
        homeSearch?.cancel()
        _state.update { it.copy(home = PlacePickerState(selected = place), locationMessage = null) }
    }

    /** [latDeg]/[lonDeg] are used for one lookup and dropped; only the matched place is kept. */
    fun useApproximateLocation(latDeg: Double, lonDeg: Double) {
        scope.launch {
            val match = withContext(io) { search.nearest(latDeg, lonDeg, NEAREST_KM) }
            if (match == null) {
                _state.update { it.copy(locationMessage = LocationMessage.NO_MATCH) }
            } else {
                choosePlace(match)
            }
        }
    }

    fun onLocationDenied() = _state.update { it.copy(locationMessage = LocationMessage.DENIED) }

    fun onLocationUnavailable() = _state.update { it.copy(locationMessage = LocationMessage.UNAVAILABLE) }

    // Step 3
    fun setTradition(choice: TraditionChoice) = _state.update { it.copy(tradition = choice) }

    // Step 4
    fun setBirthDate(date: LocalDate?) = editBirth { it.copy(birthDate = date) }

    fun setBirthTime(time: LocalTime?) = editBirth { it.copy(birthTime = time, timeUnknown = false) }

    fun setTimeUnknown(unknown: Boolean) =
        editBirth { it.copy(timeUnknown = unknown, birthTime = if (unknown) null else it.birthTime) }

    fun setBirthQuery(q: String) {
        _state.update { it.copy(birthPlace = it.birthPlace.copy(query = q)) }
        birthSearch?.cancel()
        birthSearch = scope.launch {
            runSearch(q) { r -> _state.update { s -> s.copy(birthPlace = s.birthPlace.copy(results = r)) } }
        }
    }

    fun chooseBirthPlace(place: GeoPlace) {
        birthSearch?.cancel()
        editBirth { it.copy(birthPlace = PlacePickerState(selected = place)) }
    }

    fun useHomeAsBirthPlace() {
        _state.value.home.selected?.let { chooseBirthPlace(it) }
    }

    fun chooseAmbiguity(choice: AmbiguityChoice) {
        ambiguity = choice
        _state.update { it.copy(birthPrompt = BirthPrompt.NONE) }
        next()
    }

    /** Parses the manual offset text; returns the error code, or null when accepted (and continues). */
    fun setManualOffset(text: String): OffsetPickerModel.ErrorCode? =
        when (val r = OffsetPickerModel.parse(text)) {
            is OffsetPickerModel.Result.Error -> r.code
            is OffsetPickerModel.Result.Ok -> {
                _state.update { it.copy(manualOffset = r.offset, birthPrompt = BirthPrompt.NONE) }
                next()
                null
            }
        }

    fun acceptUncertainOffset() {
        uncertainAcknowledged = true
        _state.update { it.copy(birthPrompt = BirthPrompt.NONE) }
        next()
    }

    fun dismissBirthPrompt() = _state.update { it.copy(birthPrompt = BirthPrompt.NONE) }

    // Step 5
    fun setReminder(kind: ReminderKind, on: Boolean) =
        _state.update { it.copy(reminders = if (on) it.reminders + kind else it.reminders - kind) }

    fun onNotificationResult(granted: Boolean) = _state.update { it.copy(notificationDenied = !granted) }

    // Navigation
    fun next() {
        val s = _state.value
        if (working || !s.canContinue) return
        working = true
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                when (s.step) {
                    OnboardingStep.LANGUAGE -> saveLanguage(s)
                    OnboardingStep.CITY -> saveCity(s)
                    OnboardingStep.TRADITION -> saveTradition(s)
                    OnboardingStep.BIRTH -> saveBirth(s)
                    OnboardingStep.REMINDERS -> finish(s.reminders)
                }
            } finally {
                working = false
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Only steps 4 and 5 can be skipped. Skipping step 5 finishes with every reminder off. */
    fun skip() {
        val s = _state.value
        if (working || !s.canSkip) return
        if (s.step == OnboardingStep.BIRTH) {
            goTo(OnboardingStep.REMINDERS)
        } else {
            working = true
            scope.launch {
                try {
                    finish(emptySet())
                } finally {
                    working = false
                }
            }
        }
    }

    /** False when already on the first step (the caller lets the system handle back). */
    fun back(): Boolean {
        val s = _state.value
        if (working || s.step == OnboardingStep.LANGUAGE) return false
        goTo(OnboardingStep.entries[s.step.ordinal - 1])
        return true
    }

    private fun goTo(step: OnboardingStep) =
        _state.update { it.copy(step = step, error = null, birthPrompt = BirthPrompt.NONE) }

    private fun editBirth(f: (OnboardingState) -> OnboardingState) {
        ambiguity = null
        uncertainAcknowledged = false
        _state.update { f(it).copy(manualOffset = null, birthPrompt = BirthPrompt.NONE, error = null) }
    }

    private suspend fun runSearch(q: String, deliver: (List<app.parivaar.panchang.core.model.geo.PlaceHit>) -> Unit) {
        if (q.trim().length < MIN_QUERY) {
            deliver(emptyList())
            return
        }
        delay(debounceMs)
        deliver(withContext(io) { search.search(q.trim(), RESULT_LIMIT) })
    }

    private fun fail(e: OnboardingError) = _state.update { it.copy(error = e) }

    private suspend fun saveLanguage(s: OnboardingState) {
        val ok = settings.update { it.copy(localeTag = s.localeTag) }
        if (ok is DataResult.Ok) goTo(OnboardingStep.CITY) else fail(OnboardingError.SAVE_FAILED)
    }

    private suspend fun saveCity(s: OnboardingState) {
        val place = s.home.selected ?: return
        val id = homeRecordId ?: newRecordId(rng).also { homeRecordId = it }
        val saved = places.upsert(SavedPlace(id, place, pinned = true, lastUsedAt = clock.now()))
        val set = if (saved is DataResult.Ok) settings.update { it.copy(homePlaceId = id) } else saved
        if (set is DataResult.Ok) goTo(OnboardingStep.TRADITION) else fail(OnboardingError.SAVE_FAILED)
    }

    private suspend fun saveTradition(s: OnboardingState) {
        val choice = s.tradition ?: return
        val (month, source) = when (choice) {
            TraditionChoice.NORTH -> MonthSystem.PURNIMANTA to PresetSource.USER
            TraditionChoice.SOUTH_WEST -> MonthSystem.AMANTA to PresetSource.USER
            TraditionChoice.NOT_SURE -> {
                val preset = s.home.selected?.let { RegionPresets.forPlace(it) }
                if (preset != null) preset to PresetSource.USER else MonthSystem.AMANTA to PresetSource.NOT_SURE_DEFAULT
            }
        }
        val ok = settings.update { it.copy(monthSystem = month, presetSource = source) }
        if (ok is DataResult.Ok) goTo(OnboardingStep.BIRTH) else fail(OnboardingError.SAVE_FAILED)
    }

    private data class BirthResolution(val offset: ResolvedOffset, val instant: kotlin.time.Instant?)

    private suspend fun saveBirth(s: OnboardingState) {
        val date = s.birthDate
        val place = s.birthPlace.selected
        if (date == null || place == null) return fail(OnboardingError.BIRTH_INCOMPLETE)
        val resolved = try {
            withContext(io) { resolve(s, date, place) }
        } catch (_: UnknownZoneException) {
            return fail(OnboardingError.BIRTH_INCOMPLETE)
        } ?: return
        if (resolved.offset.flags.any { it in UNCERTAIN } && s.manualOffset == null && !uncertainAcknowledged) {
            _state.update { it.copy(birthPrompt = BirthPrompt.UNCERTAIN_OFFSET) }
            return
        }
        val now = clock.now()
        val birthTime = s.birthTime.takeUnless { s.timeUnknown }
        val id = selfRecordId ?: newRecordId(rng).also { selfRecordId = it }
        val calc = settings.settings.first().defaultCalc
        val profile = Profile(
            id = id,
            displayName = selfName,
            relation = Relation.SELF,
            origin = ProfileOrigin.OWN,
            birth = BirthData(date, birthTime, timeKnown = birthTime != null),
            place = PlaceSnapshot(
                place, resolved.offset, s.manualOffset, ambiguity ?: AmbiguityChoice.EARLIER, resolved.instant,
            ),
            calc = calc,
            notes = null,
            createdAt = now,
            updatedAt = now,
        )
        val saved = profiles.upsert(profile)
        val set = if (saved is DataResult.Ok) settings.update { it.copy(selfProfileId = id) } else saved
        if (set is DataResult.Ok) goTo(OnboardingStep.REMINDERS) else fail(OnboardingError.SAVE_FAILED)
    }

    @Suppress("ReturnCount")
    /** Returns null when the result needs a user choice or the date is invalid (state already updated). */
    private fun resolve(s: OnboardingState, date: LocalDate, place: GeoPlace): BirthResolution? {
        val ctx = place.tzContext()
        val manual: ManualOffset? = s.manualOffset
        val time = s.birthTime
        if (s.timeUnknown || time == null) {
            return when (val r = offsets.civilDayOffset(date, ctx, manual)) {
                is CivilDayOffset.Valid -> BirthResolution(r.offset, null)
                CivilDayOffset.Nonexistent -> {
                    fail(OnboardingError.BIRTH_DATE_INVALID)
                    null
                }
            }
        }
        val local = LocalDateTime(date, time)
        val r = offsets.localToInstant(local, ctx, ambiguity ?: AmbiguityChoice.EARLIER, manual)
        if (r.alternative != null && ambiguity == null) {
            _state.update { it.copy(birthPrompt = BirthPrompt.AMBIGUOUS_TIME) }
            return null
        }
        return BirthResolution(r.offset, r.instant)
    }

    private suspend fun finish(reminders: Set<ReminderKind>) {
        val ok = settings.update { it.copy(reminders = reminders, onboardingDone = true) }
        if (ok is DataResult.Ok) _state.update { it.copy(finished = true) } else fail(OnboardingError.SAVE_FAILED)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
        const val MIN_QUERY = 2
        const val RESULT_LIMIT = 20
        const val NEAREST_KM = 100.0
        val UNCERTAIN = setOf(OffsetFlag.HISTORICAL_VERIFY, OffsetFlag.PRE_STANDARD_LMT, OffsetFlag.NO_OVERLAY_DATA)
    }
}
