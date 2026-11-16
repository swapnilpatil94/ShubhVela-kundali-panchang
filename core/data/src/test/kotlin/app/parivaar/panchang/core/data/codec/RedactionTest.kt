package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import app.parivaar.panchang.core.model.geo.PlaceHit
import app.parivaar.panchang.core.model.geo.tzContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionTest {
    @Test
    fun toStringHoldsNoPersonalData() {
        val all = listOf<Any>(
            F.profileA, F.profileA.birth, F.profileA.place, F.profileB.place, F.savedPlace, F.bond, F.bond.traits,
            F.settings, F.doc,
            // Fable review item 6: geo types never print name, coordinates or zone.
            F.placeA, F.placeB, PlaceHit(F.placeA, "Testville", 0), F.placeA.tzContext(), F.placeB.tzContext(),
        )
        val secrets = listOf(
            F.NAME_A, F.NAME_B, F.BOND_NAME, F.NOTE, "1990", "1985", "06:30", "12.5", "77.25", "Testville",
            "Asia/Kolkata", "hi-IN", "Polar", "-90",
        )
        for (o in all) {
            val s = o.toString()
            for (x in secrets) assertFalse(s.contains(x), "${o::class.simpleName} leaks")
        }
        // M10 review: the record id is not printed either (it links log lines to one person's row).
        assertFalse(F.profileA.toString().contains(F.profileA.id.hex))
        assertTrue(F.profileA.toString() == "Profile(redacted)")
        val withPassing = F.profileA.copy(
            passing = app.parivaar.panchang.core.model.profile.PassingData(
                kotlinx.datetime.LocalDate(2019, 3, 4), null,
            ),
        )
        for (o in listOf<Any>(withPassing, withPassing.passing!!, withPassing.familyPrefs)) {
            assertFalse(o.toString().contains("2019"), "${o::class.simpleName} leaks")
            assertFalse(o.toString().contains(F.NAME_A))
        }
    }
}
