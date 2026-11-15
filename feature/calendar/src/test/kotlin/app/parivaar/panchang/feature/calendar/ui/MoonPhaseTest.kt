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
