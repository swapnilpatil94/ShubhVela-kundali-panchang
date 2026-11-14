package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionTest {
    @Test
    fun toStringHoldsNoPersonalData() {
        val all = listOf<Any>(
            F.profileA, F.profileA.birth, F.profileA.place, F.profileB.place, F.savedPlace, F.bond, F.bond.traits,
            F.settings, F.doc,
        )
        val secrets = listOf(
            F.NAME_A, F.NAME_B, F.BOND_NAME, F.NOTE, "1990", "1985", "06:30", "12.5", "77.25", "Testville",
            "Asia/Kolkata", "hi-IN", "Polar", "-90",
        )
        for (o in all) {
            val s = o.toString()
            for (x in secrets) assertFalse(s.contains(x), "${o::class.simpleName} leaks")
        }
        assertTrue(F.profileA.toString().contains(F.profileA.id.hex))
    }
}
