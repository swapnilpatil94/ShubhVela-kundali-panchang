package app.parivaar.panchang.feature.onboarding

import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.PlaceHit
import app.parivaar.panchang.core.model.settings.ReminderKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

enum class OnboardingStep { LANGUAGE, CITY, TRADITION, BIRTH, REMINDERS }

enum class TraditionChoice { NORTH, SOUTH_WEST, NOT_SURE }

enum class LocationMessage { DENIED, UNAVAILABLE, NO_MATCH }

enum class BirthPrompt { NONE, AMBIGUOUS_TIME, UNCERTAIN_OFFSET }

enum class OnboardingError { SAVE_FAILED, BIRTH_INCOMPLETE, BIRTH_DATE_INVALID }

data class PlacePickerState(
    val query: String = "",
    val results: List<PlaceHit> = emptyList(),
    val selected: GeoPlace? = null,
)

/** Everything here is in memory only; the machine writes records at each Continue. */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.LANGUAGE,
    val localeTag: String = LanguageOptions.FALLBACK,
    val home: PlacePickerState = PlacePickerState(),
    val locationMessage: LocationMessage? = null,
    val tradition: TraditionChoice? = null,
    val birthDate: LocalDate? = null,
    val birthTime: LocalTime? = null,
    val timeUnknown: Boolean = false,
    val birthPlace: PlacePickerState = PlacePickerState(),
    val birthPrompt: BirthPrompt = BirthPrompt.NONE,
    val manualOffset: ManualOffset? = null,
    val reminders: Set<ReminderKind> = emptySet(),
    val notificationDenied: Boolean = false,
    val busy: Boolean = false,
    val error: OnboardingError? = null,
    val finished: Boolean = false,
) {
    override fun toString(): String = "OnboardingState(step=$step)"

    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.CITY -> home.selected != null
            OnboardingStep.TRADITION -> tradition != null
            OnboardingStep.BIRTH ->
                birthDate != null && birthPlace.selected != null && (timeUnknown || birthTime != null)
            else -> true
        } && !busy

    val canSkip: Boolean get() = (step == OnboardingStep.BIRTH || step == OnboardingStep.REMINDERS) && !busy
}
