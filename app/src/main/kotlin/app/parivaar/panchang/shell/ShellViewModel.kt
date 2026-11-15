package app.parivaar.panchang.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.parivaar.panchang.R
import app.parivaar.panchang.core.data.DataVault
import app.parivaar.panchang.core.data.PlaceRepository
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.feature.onboarding.OnboardingMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.security.SecureRandom
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Clock
import app.parivaar.panchang.feature.settings.AppearanceModel
import app.parivaar.panchang.feature.settings.ConventionsModel

/**
 * Activity-scoped holder: survives `recreate()` (locale change), so the onboarding machine keeps its state.
 * Geo bindings are lazy wrappers; their first call happens on `Dispatchers.IO` inside the machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        vault: DataVault,
        private val settingsRepo: SettingsRepository,
        places: PlaceRepository,
        profiles: ProfileRepository,
        search: PlaceSearch,
        offsets: OffsetResolver,
        @ApplicationContext context: Context,
    ) : ViewModel() {
        val gate = AppGateModel(vault, settingsRepo, viewModelScope, Dispatchers.IO)

        /** Settings are read only while the vault is Ready; defaults until then. */
        val settings: StateFlow<AppSettings> =
            vault.state
                .flatMapLatest { if (it == VaultState.Ready) settingsRepo.settings.catch { } else emptyFlow() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

        /** Name of the home place for the tab sub-title; held in memory only. */
        val homePlaceName: StateFlow<String?> =
            vault.state
                .flatMapLatest { if (it == VaultState.Ready) places.observeAll().catch { } else emptyFlow() }
                .let { flow ->
                    kotlinx.coroutines.flow.combine(flow, settings) { recs, s -> homeName(recs.items, s) }
                }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val conventions = ConventionsModel(settingsRepo, viewModelScope)
        val appearance = AppearanceModel(settingsRepo, viewModelScope)

        /** Built on first use (only when onboarding is shown), keeping the launch path light. */
        val onboarding: OnboardingMachine by lazy {
            OnboardingMachine(
                search = search,
                offsets = offsets,
                settings = settingsRepo,
                places = places,
                profiles = profiles,
                clock = Clock.System,
                rng = SecureRandom(),
                scope = viewModelScope,
                io = Dispatchers.IO,
                selfName = context.getString(R.string.profile_me),
                deviceLanguageTags = listOf(Locale.getDefault().toLanguageTag()),
            )
        }

        private fun homeName(items: List<SavedPlace>, s: AppSettings): String? =
            items.firstOrNull { it.id == s.homePlaceId }?.place?.name
    }
