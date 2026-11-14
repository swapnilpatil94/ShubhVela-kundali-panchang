package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.profile.BirthData
import app.parivaar.panchang.core.model.profile.PlaceSnapshot
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.HouseSystem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** Synthetic family data only: invented names, dates and a fictional-grid place. */
internal object FamilyFixture {
    const val NAME_A = "Testperson Alpha"
    const val NAME_B = "Testperson Beta"
    const val BOND_NAME = "Gamma"
    const val NOTE = "synthetic note"

    fun id(n: Int) = RecordId(n.toString(16).padStart(RecordId.LENGTH, '0'))

    val t0: Instant = Instant.parse("2024-01-01T00:00:00Z")
    val t1: Instant = Instant.parse("2024-02-01T00:00:00Z")

    val placeA = GeoPlace(1L, "Testville", "Testshire", "ZZ", 12.5, 77.25, 900.0, 1234567L, "Asia/Kolkata", null)
    val placeB = GeoPlace(2L, "Polar Test", null, "ZZ", -90.0, 180.0, null, 0L, "Etc/GMT-5", "test_overlay")

    val offsetIana = ResolvedOffset(19_800, OffsetSource.IANA, emptySet(), null)
    val offsetDst = ResolvedOffset(-14_400, OffsetSource.IANA, setOf(OffsetFlag.DST, OffsetFlag.AMBIGUOUS), null)
    val offsetManual = ResolvedOffset(18_000, OffsetSource.MANUAL, setOf(OffsetFlag.GAP_SHIFTED), null)

    val profileA =
        Profile(
            id(1), NAME_A, Relation.SELF, ProfileOrigin.OWN,
            BirthData(LocalDate(1990, 5, 17), LocalTime(6, 30, 15), true),
            PlaceSnapshot(placeA, offsetDst, null, AmbiguityChoice.LATER, Instant.parse("1990-05-17T10:30:15Z")),
            CalcSettings(ayanamsa = AyanamsaId.RAMAN, pressureHPa = 1013.25, temperatureC = 18.5),
            NOTE, t0, t1,
        )

    val profileB =
        Profile(
            id(2), NAME_B, Relation.FRIEND, ProfileOrigin.MANUAL_OTHER,
            BirthData(LocalDate(1985, 11, 2), null, false),
            PlaceSnapshot(placeB, offsetManual, ManualOffset(18_000), AmbiguityChoice.EARLIER, null),
            CalcSettings(),
            null, t0, t0,
        )

    val savedPlace = SavedPlace(id(3), placeA, true, t1)

    val bond =
        Bond(id(4), BondTraits(BOND_NAME, 26, 4, 11, null, 2, 0, 11, 1, 2, 3), "friend", t0)

    val settings =
        AppSettings(CalcSettings(houseSystem = HouseSystem.EQUAL, abhijitOnWednesday = true), id(1), "hi-IN", true)

    val doc = FamilyDocument(listOf(profileA, profileB), listOf(savedPlace), listOf(bond), settings)
}
