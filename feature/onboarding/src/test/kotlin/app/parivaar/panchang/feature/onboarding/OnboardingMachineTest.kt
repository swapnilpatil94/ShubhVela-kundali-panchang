package app.parivaar.panchang.feature.onboarding

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.PlaceRepository
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.data.Records
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.LocalResolution
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.PlaceHit
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.geo.TzContext
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.core.model.settings.PresetSource
import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.engine.calendar.MonthSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private val pune = GeoPlace(1, "Pune", "Maharashtra", "IN", 18.5196, 73.8553, 560.0, 3_000_000, "Asia/Kolkata", null)
private val ist = ResolvedOffset(19_800, OffsetSource.IANA, emptySet(), null)

private class FakeSearch(var nearestResult: GeoPlace? = pune) : PlaceSearch {
    var searches = 0
    override fun search(query: String, limit: Int): List<PlaceHit> {
        searches++
        return listOf(PlaceHit(pune, "Pune", 0))
    }
    override fun nearest(latDeg: Double, lonDeg: Double, maxKm: Double): GeoPlace? = nearestResult
}

private class FakeOffsets(var alternative: Instant? = null, var flags: Set<OffsetFlag> = emptySet()) : OffsetResolver {
    override fun offsetAt(instant: Instant, ctx: TzContext, manual: ManualOffset?) = error("unused")
    override fun civilDayOffset(date: LocalDate, ctx: TzContext, manual: ManualOffset?) =
        CivilDayOffset.Valid(ist.copy(flags = flags))
    override fun localToInstant(
        local: kotlinx.datetime.LocalDateTime,
        ctx: TzContext,
        choice: AmbiguityChoice,
        manual: ManualOffset?,
    ) = LocalResolution(Instant.fromEpochSeconds(1_000), ist.copy(flags = flags), alternative)
}

private class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = flow
    override suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit> {
        flow.value = f(flow.value)
        return DataResult.Ok(Unit)
    }
}

private class FakePlaces : PlaceRepository {
    val written = mutableListOf<SavedPlace>()
    override fun observeAll(): Flow<Records<SavedPlace>> = emptyFlow()
    override suspend fun upsert(p: SavedPlace): DataResult<Unit> {
        written += p
        return DataResult.Ok(Unit)
    }
    override suspend fun delete(id: RecordId) = DataResult.Ok(Unit)
}

private class FakeProfiles : ProfileRepository {
    val written = mutableListOf<Profile>()
    override fun observeAll(): Flow<Records<Profile>> = emptyFlow()
    override suspend fun get(id: RecordId): DataResult<Profile> = DataResult.Err(DataError.NOT_FOUND)
    override suspend fun upsert(p: Profile): DataResult<Unit> {
        written += p
        return DataResult.Ok(Unit)
    }
    override suspend fun delete(id: RecordId) = DataResult.Ok(Unit)
    override suspend fun reorder(ids: List<RecordId>) = DataResult.Ok(Unit)
}

class OnboardingMachineTest {
    private val search = FakeSearch()
    private val offsets = FakeOffsets()
    private val settings = FakeSettings()
    private val places = FakePlaces()
    private val profiles = FakeProfiles()
    private val clock = object : Clock { override fun now(): Instant = Instant.fromEpochSeconds(1_700_000_000) }
    private val machine = OnboardingMachine(
        search, offsets, settings, places, profiles, clock, SecureRandom(),
        CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, "Me", listOf("fr-FR"), debounceMs = 0,
    )
    private val step get() = machine.state.value.step

    private fun toCity() { machine.next() }
    private fun toTradition() { toCity(); machine.choosePlace(pune); machine.next() }

    @Test fun stepOrderAndFinalSettings() {
        assertEquals(OnboardingStep.LANGUAGE, step)
        assertEquals("en", machine.state.value.localeTag)
        machine.setLocale("hi")
        machine.next(); assertEquals(OnboardingStep.CITY, step)
        machine.choosePlace(pune); machine.next(); assertEquals(OnboardingStep.TRADITION, step)
        machine.setTradition(TraditionChoice.NORTH); machine.next(); assertEquals(OnboardingStep.BIRTH, step)
        machine.skip(); assertEquals(OnboardingStep.REMINDERS, step)
        machine.setReminder(ReminderKind.GRAHAN, true)
        machine.next()
        assertTrue(machine.state.value.finished)
        val homeId = settings.flow.value.homePlaceId
        assertNotNull(homeId)
        assertEquals(
            AppSettings(
                localeTag = "hi", homePlaceId = homeId, monthSystem = MonthSystem.PURNIMANTA,
                presetSource = PresetSource.USER, reminders = setOf(ReminderKind.GRAHAN), onboardingDone = true,
            ),
            settings.flow.value,
        )
        assertEquals(1, places.written.size)
        assertTrue(places.written.single().pinned)
        assertTrue(profiles.written.isEmpty())
    }

    @Test fun skipOnlyOnSteps4And5() {
        machine.skip(); assertEquals(OnboardingStep.LANGUAGE, step)
        toCity(); machine.skip(); assertEquals(OnboardingStep.CITY, step)
        machine.choosePlace(pune); machine.next(); machine.skip(); assertEquals(OnboardingStep.TRADITION, step)
        machine.setTradition(TraditionChoice.SOUTH_WEST); machine.next()
        assertTrue(machine.state.value.canSkip)
        machine.skip(); assertTrue(machine.state.value.canSkip)
        machine.skip()
        assertTrue(machine.state.value.finished)
        assertTrue(settings.flow.value.reminders.isEmpty())
    }

    @Test fun cityIsRequired() {
        toCity()
        machine.next()
        assertEquals(OnboardingStep.CITY, step)
        assertTrue(places.written.isEmpty())
    }

    @Test fun notSureFallsBackToAmantaDefault() {
        toCity(); machine.choosePlace(pune); machine.next()
        machine.setTradition(TraditionChoice.NOT_SURE); machine.next()
        assertEquals(MonthSystem.AMANTA, settings.flow.value.monthSystem)
        assertEquals(PresetSource.NOT_SURE_DEFAULT, settings.flow.value.presetSource)
    }

    @Test fun locationDeniedStaysOnCity() {
        toCity()
        machine.onLocationDenied()
        assertEquals(OnboardingStep.CITY, step)
        assertEquals(LocationMessage.DENIED, machine.state.value.locationMessage)
        machine.next()
        assertEquals(OnboardingStep.CITY, step)
    }

    @Test fun noMatchKeepsSearchOpen() {
        search.nearestResult = null
        toCity()
        machine.useApproximateLocation(0.0, 0.0)
        assertEquals(LocationMessage.NO_MATCH, machine.state.value.locationMessage)
        assertNull(machine.state.value.home.selected)
    }

    @Test fun userCoordinatesAreNeverWritten() {
        toCity()
        machine.useApproximateLocation(18.520412, 73.856787)
        assertEquals(pune, machine.state.value.home.selected)
        machine.next()
        val written = places.written.map { it.place }
        assertTrue(written.all { it.latDeg != 18.520412 && it.lonDeg != 73.856787 })
        assertEquals(pune, written.single())
    }

    @Test fun backReturnsAndFirstStepReturnsFalse() {
        assertFalse(machine.back())
        toCity()
        assertTrue(machine.back()); assertEquals(OnboardingStep.LANGUAGE, step)
    }

    @Test fun birthWithUnknownTimeSavesSelfProfile() {
        toTradition(); machine.setTradition(TraditionChoice.SOUTH_WEST); machine.next()
        machine.next(); assertEquals(OnboardingStep.BIRTH, step)
        machine.setBirthDate(LocalDate(1990, 5, 17)); machine.setTimeUnknown(true); machine.useHomeAsBirthPlace()
        machine.next()
        assertEquals(OnboardingStep.REMINDERS, step)
        val p = profiles.written.single()
        assertEquals("Me", p.displayName)
        assertFalse(p.birth.timeKnown)
        assertNull(p.birth.time)
        assertNull(p.place.birthInstant)
        assertEquals(p.id, settings.flow.value.selfProfileId)
    }

    @Test fun ambiguousTimeAsksThenSaves() {
        offsets.alternative = Instant.fromEpochSeconds(2_000)
        toTradition(); machine.setTradition(TraditionChoice.NORTH); machine.next()
        machine.setBirthDate(LocalDate(1990, 5, 17)); machine.setBirthTime(LocalTime(1, 30))
        machine.useHomeAsBirthPlace()
        machine.next()
        assertEquals(BirthPrompt.AMBIGUOUS_TIME, machine.state.value.birthPrompt)
        assertTrue(profiles.written.isEmpty())
        machine.chooseAmbiguity(AmbiguityChoice.LATER)
        assertEquals(AmbiguityChoice.LATER, profiles.written.single().place.ambiguity)
        assertEquals(OnboardingStep.REMINDERS, step)
    }

    @Test fun uncertainOffsetOffersManualEntry() {
        offsets.flags = setOf(OffsetFlag.HISTORICAL_VERIFY)
        toTradition(); machine.setTradition(TraditionChoice.NORTH); machine.next()
        machine.setBirthDate(LocalDate(1940, 5, 17)); machine.setTimeUnknown(true); machine.useHomeAsBirthPlace()
        machine.next()
        assertEquals(BirthPrompt.UNCERTAIN_OFFSET, machine.state.value.birthPrompt)
        assertNotNull(machine.setManualOffset("bad"))
        assertNull(machine.setManualOffset("+04:51"))
        assertEquals(ManualOffset(4 * 3600 + 51 * 60), profiles.written.single().place.manualOffset)
    }
}
