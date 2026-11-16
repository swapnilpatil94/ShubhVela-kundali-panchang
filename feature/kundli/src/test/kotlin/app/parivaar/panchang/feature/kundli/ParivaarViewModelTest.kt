package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.model.profile.FamilyPrefs
import app.parivaar.panchang.core.model.profile.PassingData
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.MonthSystem
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParivaarViewModelTest {
    private fun item(n: Int, kind: FamilyKind, date: LocalDate) =
        FamilyDateItem(KundliTestKit.id(n), kind, date, null, null, null, null, emptySet())

    @Test fun upcomingSortedWithDaysUntil() {
        val today = LocalDate(2026, 11, 16)
        val ps = listOf(KundliTestKit.profile(1), KundliTestKit.profile(2))
        val items = listOf(
            item(1, FamilyKind.TITHI_BIRTHDAY, today.plus(DatePeriod(days = 71))),
            item(2, FamilyKind.SHRADDHA, today.plus(DatePeriod(days = 24))),
            item(1, FamilyKind.NAKSHATRA_BIRTHDAY, today.plus(DatePeriod(days = 3))),
            item(2, FamilyKind.TITHI_BIRTHDAY, today.plus(DatePeriod(days = -1))), // past: dropped
            item(9, FamilyKind.TITHI_BIRTHDAY, today), // unknown profile: dropped
        )
        val rows = Upcoming.rows(items, ps, today)
        assertEquals(listOf(3, 24, 71), rows.map { it.daysUntil })
        assertEquals(KundliTestKit.id(1), rows.first().profile.id)
    }

    @Test fun finderProducesYearlyDatesForEnabledKindsOnly() {
        val engine = KundliTestKit.direct()
        val home = CalInputsLite(
            Place(28.61, 77.21, 216.0), TimeZone.UTC, { 19_800 }, CalcSettings(), MonthSystem.AMANTA, null,
        )
        val p = KundliTestKit.profile(1).copy(
            passing = PassingData(LocalDate(2015, 3, 10), null),
            familyPrefs = FamilyPrefs(tithi = true, nakshatra = false, shraddha = true),
        )
        val first = LocalDate(2027, 1, 1)
        val items = engine.familyDates(listOf(p), first, first.plus(DatePeriod(days = 365)), home).items
        val kinds = items.map { it.kind }.toSet()
        assertEquals(setOf(FamilyKind.TITHI_BIRTHDAY, FamilyKind.SHRADDHA), kinds)
        assertTrue(items.all { it.profileId == p.id && it.date >= first })
        assertTrue(FinderFamilyDates.idOf(3, FamilyKind.SHRADDHA).matches(Regex("fam_[0-9]+")))
    }

    private val homeLite =
        CalInputsLite(Place(28.61, 77.21, 216.0), TimeZone.UTC, { 19_800 }, CalcSettings(), MonthSystem.AMANTA, null)

    @Test fun passingTimeIsHonouredAndDisabledAyanamsaFallsBackWithNote() {
        val engine = KundliTestKit.direct()
        val prefs = FamilyPrefs(tithi = false, nakshatra = false, shraddha = true)
        val base = KundliTestKit.profile(1).copy(familyPrefs = prefs)
        val first = LocalDate(2027, 1, 1)
        val last = first.plus(DatePeriod(days = 365))
        // 2015-03-10: a late-evening time can fall in a later tithi than sunrise; both give one yearly date.
        val atSunrise = engine.familyDates(
            listOf(base.copy(passing = PassingData(LocalDate(2015, 3, 10), null))), first, last, homeLite,
        )
        val late = base.copy(passing = PassingData(LocalDate(2015, 3, 10), kotlinx.datetime.LocalTime(23, 30)))
        val atTime = engine.familyDates(listOf(late), first, last, homeLite)
        assertTrue(atSunrise.items.isNotEmpty() && atTime.items.isNotEmpty())
        assertTrue(atSunrise.notes.isEmpty())
        val raman = CalInputsLite(homeLite.place, homeLite.zone, homeLite.offsetFor,
            CalcSettings(ayanamsa = app.parivaar.panchang.engine.jyotish.AyanamsaId.RAMAN), MonthSystem.AMANTA, null)
        val fb = engine.familyDates(listOf(late), first, last, raman)
        assertTrue(FamilyNote.AYANAMSA_FALLBACK in fb.notes)
        assertTrue(fb.items.isNotEmpty())
    }

    @Test fun lockClearsUpcoming() {
        val today = LocalDate(2026, 11, 16)
        val p = KundliTestKit.profile(1)
        val fake = object : KundliEngine by KundliTestKit.direct() {
            override fun familyDates(
                ps: List<app.parivaar.panchang.core.model.profile.Profile>, first: LocalDate, last: LocalDate,
                home: CalInputsLite,
            ) =
                FamilyDatesResult(listOf(item(1, FamilyKind.TITHI_BIRTHDAY, today.plus(DatePeriod(days = 3)))))
        }
        val profiles = kotlinx.coroutines.flow.MutableStateFlow(listOf(p))
        val home = kotlinx.coroutines.flow.MutableStateFlow<CalInputsLite?>(homeLite)
        val clock = object : kotlin.time.Clock {
            override fun now() = kotlin.time.Instant.parse("2026-11-16T06:00:00Z")
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val vm = ParivaarViewModel(profiles, home, fake, clock, scope, kotlinx.coroutines.Dispatchers.Unconfined)
        assertEquals(1, vm.state.value.upcoming.size)
        // Vault locks: the shell emits no profiles and no home place.
        profiles.value = emptyList()
        home.value = null
        assertTrue(vm.state.value.upcoming.isEmpty())
        assertTrue(vm.state.value.profiles.isEmpty())
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        Unit
    }
}
