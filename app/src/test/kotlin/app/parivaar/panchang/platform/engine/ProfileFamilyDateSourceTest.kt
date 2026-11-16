package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.profile.BirthData
import app.parivaar.panchang.core.model.profile.FamilyPrefs
import app.parivaar.panchang.core.model.profile.PlaceSnapshot
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.jyotish.DashaPeriod
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.feature.kundli.CalInputsLite
import app.parivaar.panchang.feature.kundli.ChartRequest
import app.parivaar.panchang.feature.kundli.FamilyDateItem
import app.parivaar.panchang.feature.kundli.FamilyDatesResult
import app.parivaar.panchang.feature.kundli.FamilyKind
import app.parivaar.panchang.feature.kundli.KundliBundle
import app.parivaar.panchang.feature.kundli.KundliEngine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProfileFamilyDateSourceTest {
    private val name = "Synthetic Secretname"
    private val place = GeoPlace(1L, "Secretville", null, "ZZ", 10.0, 20.0, null, 1L, "UTC", null)

    private fun profile(n: Int, prefs: FamilyPrefs) = Profile(
        RecordId(n.toString(16).padStart(32, '0')), name, Relation.PARENT, ProfileOrigin.MANUAL_OTHER,
        BirthData(LocalDate(1950, 1, 1), null, false),
        PlaceSnapshot(
            place, ResolvedOffset(0, OffsetSource.IANA, emptySet(), null), null, AmbiguityChoice.EARLIER, null,
        ),
        CalcSettings(), null, Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(0), familyPrefs = prefs,
    )

    private val d1 = LocalDate(2027, 2, 1)
    private val d2 = LocalDate(2027, 3, 1)
    private val d3 = LocalDate(2027, 4, 1)

    private val engine = object : KundliEngine {
        override fun bundle(r: ChartRequest): KundliBundle = error("not used")
        override fun dasha(r: ChartRequest, path: List<Graha>): List<DashaPeriod<Graha>> = error("not used")
        override fun familyDates(ps: List<Profile>, first: LocalDate, last: LocalDate, home: CalInputsLite) =
            FamilyDatesResult(
                listOf(
                    FamilyDateItem(ps[0].id, FamilyKind.TITHI_BIRTHDAY, d1, 5, null, null, null, emptySet()),
                    FamilyDateItem(ps[0].id, FamilyKind.NAKSHATRA_BIRTHDAY, d2, null, null, 3, null, emptySet()),
                    FamilyDateItem(ps[0].id, FamilyKind.SHRADDHA, d3, 9, null, null, null, emptySet()),
                ),
            )
    }

    private val home = CalInputsLite(Place(0.0, 0.0), TimeZone.UTC, { 0 }, CalcSettings(), MonthSystem.AMANTA, null)

    @Test fun disabledKindExcludedAndOutputIsBareDates() {
        val p = profile(1, FamilyPrefs(tithi = true, nakshatra = false, shraddha = true))
        val src = ProfileFamilyDateSource({ listOf(p) }, { home }, engine)
        val out = src.dates(d1, d3)
        assertEquals(listOf(d1, d3), out)
        // Bare LocalDates only: nothing personal can reach the planner or a notification.
        assertTrue(out.none { it.toString().contains("Secret") })
    }

    @Test fun noHomeOrNoProfilesGivesNothing() {
        val p = profile(1, FamilyPrefs())
        assertEquals(emptyList(), ProfileFamilyDateSource({ listOf(p) }, { null }, engine).dates(d1, d3))
        assertEquals(emptyList(), ProfileFamilyDateSource({ emptyList() }, { home }, engine).dates(d1, d3))
    }
}
