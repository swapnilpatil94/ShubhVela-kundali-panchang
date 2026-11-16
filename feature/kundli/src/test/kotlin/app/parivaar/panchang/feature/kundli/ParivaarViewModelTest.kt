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
        val items = engine.familyDates(listOf(p), first, first.plus(DatePeriod(days = 365)), home)
        val kinds = items.map { it.kind }.toSet()
        assertEquals(setOf(FamilyKind.TITHI_BIRTHDAY, FamilyKind.SHRADDHA), kinds)
        assertTrue(items.all { it.profileId == p.id && it.date >= first })
        assertTrue(FinderFamilyDates.idOf(3, FamilyKind.SHRADDHA).matches(Regex("fam_[0-9]+")))
    }
}
