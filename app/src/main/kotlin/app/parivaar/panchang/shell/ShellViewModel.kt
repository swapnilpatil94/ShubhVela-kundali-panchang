package app.parivaar.panchang.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.parivaar.panchang.R
import android.widget.Toast
import app.parivaar.panchang.aaj.LazyAajEngine
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.tzContext
import kotlinx.coroutines.flow.first
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.feature.aaj.R as AajR
import app.parivaar.panchang.feature.aaj.share.ShareCardModel
import app.parivaar.panchang.feature.aaj.share.ShareFormat
import app.parivaar.panchang.feature.aaj.speech.AajSpeaker
import app.parivaar.panchang.feature.aaj.travel.TravelMode
import app.parivaar.panchang.platform.share.ShareController
import app.parivaar.panchang.platform.travel.AppCoarseLocator
import app.parivaar.panchang.platform.travel.PermissionBridge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.parivaar.panchang.core.data.DataVault
import app.parivaar.panchang.core.data.PlaceRepository
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.feature.aaj.AajCache
import app.parivaar.panchang.feature.aaj.AajInputs
import app.parivaar.panchang.feature.aaj.AajModel
import app.parivaar.panchang.feature.calendar.MonthCache
import app.parivaar.panchang.feature.calendar.MonthModel
import app.parivaar.panchang.platform.engine.EngineAccess
import app.parivaar.panchang.feature.onboarding.OnboardingMachine
import app.parivaar.panchang.feature.kundli.CalInputsLite
import app.parivaar.panchang.feature.kundli.KundliBundle
import app.parivaar.panchang.feature.kundli.KundliCache
import app.parivaar.panchang.feature.kundli.KundliViewModel
import app.parivaar.panchang.feature.kundli.ParivaarViewModel
import app.parivaar.panchang.feature.kundli.ProfileEditorViewModel
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.platform.zoneSafe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
        private val profiles: ProfileRepository,
        private val search: PlaceSearch,
        private val offsets: OffsetResolver,
        @ApplicationContext private val context: Context,
        private val places: PlaceRepository,
        private val engineAccess: EngineAccess,
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

        /** Stored settings only (no default placeholder), so Today never computes with defaults first. */
        private val readySettings =
            vault.state.flatMapLatest { if (it == VaultState.Ready) settingsRepo.settings.catch { } else emptyFlow() }

        /** Today tab: home place + settings in, engine work on one background thread, geo calls on IO. */
        val aaj: AajModel by lazy {
            val inputs =
                vault.state
                    .flatMapLatest { if (it == VaultState.Ready) places.observeAll().catch { } else emptyFlow() }
                    .let { f ->
                        kotlinx.coroutines.flow.combine(f, readySettings, travel.place) { recs, s, away ->
                            aajInputs(recs.items, s, away)
                        }
                    }
            AajModel(
                inputs, LazyAajEngine(context), offsets, AajCache(), Clock.System, viewModelScope,
                Dispatchers.Default.limitedParallelism(1), Dispatchers.IO,
            )
        }

        /** Calendar tab: month model, loaders and `.ics` share over the one shared engine (under its lock). */
        val calendarController: CalendarController by lazy {
            CalendarController(
                vault.state
                    .flatMapLatest { if (it == VaultState.Ready) places.observeAll().catch { } else emptyFlow() }
                    .map { it.items },
                readySettings, travel.place, offsets, engineAccess.calendarEngine, { engineAccess.festivalDefs },
                { path -> context.assets.open(path).bufferedReader().use { it.readText() } },
                viewModelScope, Dispatchers.Default,
            )
        }

        private var calendarBuilt = false

        /** The date may have changed: moves the calendar's today ring (only once the calendar exists). */
        fun onDateMayHaveChanged() { if (calendarBuilt) calendar.onDateChanged() }

        /** Built once in this activity-scoped holder, so rotation and recreate never recompute a month. */
        val calendar: MonthModel by lazy {
            calendarBuilt = true
            MonthModel(
                calendarController.inputs, calendarController.engine, MonthCache(), Clock.System,
                CalendarController::firstDayOfWeek, viewModelScope, Dispatchers.Default.limitedParallelism(1),
            )
        }

        /** M10: profiles while the vault is open (empty otherwise). Memory only. */
        private val profileList =
            vault.state
                .flatMapLatest { if (it == VaultState.Ready) profiles.observeAll().catch { } else emptyFlow() }
                .map { it.items }

        /** Home place for family dates (D4): place, zone, offsets and calc; null without a home place. */
        private val kundliHome: StateFlow<CalInputsLite?> =
            vault.state
                .flatMapLatest { if (it == VaultState.Ready) places.observeAll().catch { } else emptyFlow() }
                .let { f -> kotlinx.coroutines.flow.combine(f, readySettings) { recs, s -> kundliHome(recs.items, s) } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** Bundle LRU (rule 10): in memory only, cleared whenever the vault is not Ready. */
        private val kundliCache = KundliCache<KundliBundle>()

        val kundliWiring: KundliWiring by lazy {
            val kundli = KundliViewModel(
                profileList, { kundliHome.value?.zone }, { kundliHome.value?.homePlaceId }, engineAccess.kundliEngine,
                offsets, kundliCache, Clock.System, viewModelScope, Dispatchers.Default,
            )
            val parivaar = ParivaarViewModel(
                profileList, kundliHome, engineAccess.kundliEngine, Clock.System, viewModelScope, Dispatchers.Default,
            )
            KundliWiring(kundli, parivaar) {
                ProfileEditorViewModel(
                    profiles, search, offsets, { settings.value.defaultCalc }, { kundli.evict(it.id) }, Clock.System,
                    viewModelScope, Dispatchers.IO,
                )
            }
        }

        init {
            viewModelScope.launch { vault.state.collect { if (it != VaultState.Ready) kundliCache.clear() } }
        }

        private fun kundliHome(items: List<SavedPlace>, s: AppSettings): CalInputsLite? {
            val home = items.firstOrNull { it.id == s.homePlaceId }
            val g = home?.place
            val zone = g?.let { zoneSafe(null) { TimeZone.of(it.zoneId) } }
            if (home == null || g == null || zone == null) return null
            val place = Place(g.latDeg, g.lonDeg, g.elevationM ?: 0.0)
            return CalInputsLite(place, zone, homeOffsets(g), s.defaultCalc, s.monthSystem, home.id)
        }

        /** Civil-day offset at [g]; a skipped civil day uses the offset at its UTC noon. */
        private fun homeOffsets(g: GeoPlace): (LocalDate) -> Int {
            val ctx = g.tzContext()
            return { d ->
                when (val o = offsets.civilDayOffset(d, ctx)) {
                    is CivilDayOffset.Valid -> o.offset.offsetSeconds
                    CivilDayOffset.Nonexistent -> {
                        val noon = LocalDateTime(d, LocalTime(NOON, 0)).toInstant(TimeZone.UTC)
                        offsets.offsetAt(noon, ctx).offsetSeconds
                    }
                }
            }
        }

        /** The activity's permission request, reached through a bridge that survives `recreate()`. */
        val permissionBridge = PermissionBridge()

        /** Travel mode: the session travel place lives here, in memory only. */
        val travel: TravelMode by lazy {
            TravelMode(search, AppCoarseLocator(context), permissionBridge, viewModelScope, Dispatchers.IO)
        }

        /** Read-aloud over the platform engine (offline voices only); started when the Today tab shows. */
        val speaker: AajSpeaker by lazy { AajSpeaker(context) }

        private val shareController by lazy { ShareController(context) }

        init {
            viewModelScope.launch(Dispatchers.IO) { shareController.purge() }
        }

        /** Renders the share image off the main thread, then opens the system share sheet. */
        fun share(activity: Context, format: ShareFormat, model: ShareCardModel) {
            viewModelScope.launch {
                val uri = withContext(Dispatchers.IO) { shareController.prepare(format, model) }
                if (uri == null) {
                    Toast.makeText(activity, AajR.string.aaj_share_failed, Toast.LENGTH_SHORT).show()
                } else {
                    activity.startActivity(shareController.chooser(uri))
                }
            }
        }

        /** One-off `.ics` of the year's public festival dates, then the system share sheet. */
        fun shareIcs(activity: Context, year: Int) = calendarController.shareIcs(activity, year, viewModelScope)

        /** A time zone change: compares the device offset with the home place's offset at this instant. */
        fun onTimeZoneChanged() {
            viewModelScope.launch(Dispatchers.IO) {
                val s = settings.value
                val home = places.observeAll().catch { }.first().items.firstOrNull { it.id == s.homePlaceId }?.place
                val now = Clock.System.now()
                val homeOffset = home?.let { offsets.offsetAt(now, it.tzContext()).offsetSeconds }
                val device = java.util.TimeZone.getDefault().getOffset(now.toEpochMilliseconds()) / MILLIS
                travel.onTimeZoneChanged(s.travelMode, device, homeOffset)
            }
        }

        override fun onCleared() {
            speaker.shutdown()
        }

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

        // Started with the activity, so Today's first content is ready as soon as the vault opens.
        init {
            aaj.refresh()
        }

        private fun aajInputs(items: List<SavedPlace>, s: AppSettings, away: GeoPlace?): AajInputs? {
            val home = items.firstOrNull { it.id == s.homePlaceId } ?: return null
            // The travel place is a session value (a derived record id, never stored); home is untouched.
            val place = away ?: home.place
            val id = if (away != null) RecordId("%032x".format(away.id)) else home.id
            // TODO(owner): derive the festival region from the home place; "all" shows only pan-India rules.
            return AajInputs(place, id, away != null, s.defaultCalc, s.monthSystem, "all", s.ekadashi)
        }

        private fun homeName(items: List<SavedPlace>, s: AppSettings): String? =
            items.firstOrNull { it.id == s.homePlaceId }?.place?.name
    }

private const val MILLIS = 1000
private const val NOON = 12
