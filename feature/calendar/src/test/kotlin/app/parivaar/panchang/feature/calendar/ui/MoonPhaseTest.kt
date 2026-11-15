package app.parivaar.panchang.feature.calendar.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoonPhaseTest {
    @Test fun tithiMidpoints() {
        assertEquals(6.0, MoonPhase.tithiElongationDeg(1))
        assertEquals(354.0, MoonPhase.tithiElongationDeg(30))
        assertEquals(6.0, MoonPhase.tithiElongationDeg(0)) // clamped
    }

    @Test fun quartersAndGibbous() {
        val firstQuarter = MoonPhase.shape(90.0)
        assertTrue(firstQuarter.waxing)
        assertEquals(0f, firstQuarter.terminatorFraction, 1e-6f)
        assertTrue(MoonPhase.shape(135.0).gibbous)
        assertFalse(MoonPhase.shape(45.0).gibbous)
        assertFalse(MoonPhase.shape(270.0).waxing)
        assertEquals(0.5, MoonPhase.illumination(90.0), 1e-9)
        assertEquals(1.0, MoonPhase.illumination(180.0), 1e-9)
    }

    @Test fun utsavExcludesVratOnlyDays() {
        assertFalse(UtsavDays.isUtsav(listOf("ekadashi_smarta", "purnima")))
        assertFalse(UtsavDays.isUtsav(emptyList()))
        assertTrue(UtsavDays.isUtsav(listOf("ekadashi_smarta", "karwa_chauth")))
    }
}

class MoonColorsTest {
    private fun card(mood: app.parivaar.panchang.core.designsystem.tokens.RatnaMood): Long =
        app.parivaar.panchang.core.designsystem.tokens.PpPalette.of(mood).lacquerTop

    @Test fun litFillReadsAgainstCardInBothMoods() {
        for ((dark, mood) in listOf(
            true to app.parivaar.panchang.core.designsystem.tokens.RatnaMood.NIGHT,
            false to app.parivaar.panchang.core.designsystem.tokens.RatnaMood.CHANDAN,
        )) {
            val look = MoonColors.of(dark)
            val c = app.parivaar.panchang.core.designsystem.tokens.contrastRatio(look.lit, card(mood))
            assertTrue(c >= 3.0, "lit vs card $mood = $c")
            // Unlit is dark ink in both moods and clearly differs from the lit part.
            assertEquals(0xFF1A1C3E, look.dark)
            assertTrue(app.parivaar.panchang.core.designsystem.tokens.contrastRatio(look.lit, look.dark) >= 3.0)
        }
    }
}
