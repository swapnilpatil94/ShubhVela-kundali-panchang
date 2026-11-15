package app.parivaar.panchang.shell

import app.parivaar.panchang.core.designsystem.components.dockVisible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {
    @Test fun startsAtAajRootAndExitsOnBack() {
        val n = AppNavigator()
        assertEquals(Dest.Root(Tab.AAJ), n.current.value)
        assertFalse(n.canGoBack.value)
        assertFalse(n.back())
    }

    @Test fun stacksArePreservedAcrossTabSwitch() {
        val n = AppNavigator()
        n.push(Dest.Settings)
        n.select(Tab.KUNDLI)
        n.push(Dest.Privacy)
        n.select(Tab.AAJ)
        assertEquals(Dest.Settings, n.current.value)
        n.select(Tab.KUNDLI)
        assertEquals(Dest.Privacy, n.current.value)
    }

    @Test fun reselectPopsToRoot() {
        val n = AppNavigator()
        n.push(Dest.Settings)
        n.push(Dest.Conventions)
        n.select(Tab.AAJ)
        assertEquals(Dest.Root(Tab.AAJ), n.current.value)
        assertFalse(n.back())
    }

    @Test fun backPopsThenGoesToAajThenExits() {
        val n = AppNavigator(Tab.BONDS)
        n.push(Dest.Settings)
        assertTrue(n.canGoBack.value)
        assertTrue(n.back())
        assertEquals(Dest.Root(Tab.BONDS), n.current.value)
        assertTrue(n.back())
        assertEquals(Tab.AAJ, n.tab.value)
        assertFalse(n.canGoBack.value)
        assertFalse(n.back())
    }

    @Test fun pushRootSwitchesTab() {
        val n = AppNavigator()
        n.push(Dest.Root(Tab.BHAKTI))
        assertEquals(Tab.BHAKTI, n.tab.value)
    }

    @Test fun saveRestoreRoundTrip() {
        val n = AppNavigator()
        n.push(Dest.Settings)
        n.push(Dest.Conventions)
        n.select(Tab.CALENDAR)
        n.push(Dest.Privacy)
        val r = AppNavigator.restore(n.save())
        assertEquals(n.save(), r.save())
        assertEquals(Tab.CALENDAR, r.tab.value)
        assertEquals(Dest.Privacy, r.current.value)
        r.select(Tab.AAJ)
        assertEquals(Dest.Conventions, r.current.value)
        assertTrue(r.back())
        assertEquals(Dest.Settings, r.current.value)
    }

    @Test fun restoreToleratesGarbage() {
        val r = AppNavigator.restore(listOf("NOPE", "AAJ:bogus,settings", "zzz"))
        assertEquals(Tab.AAJ, r.tab.value)
        assertEquals(Dest.Settings, r.current.value)
        assertEquals(Dest.Root(Tab.AAJ), AppNavigator.restore(emptyList()).current.value)
    }
}

class DestImmersiveTest {
    @Test fun noExistingDestinationIsImmersive() {
        val all = listOf(
            Dest.Settings, Dest.Conventions, Dest.Privacy, Dest.Grahan, Dest.Transits, Dest.ShouldI,
            Dest.FestivalGuide("diwali"), *Tab.entries.map { Dest.Root(it) }.toTypedArray(),
        )
        assertTrue(all.none { it.immersive })
    }

    @Test fun anImmersiveDestinationHidesTheDock() {
        assertFalse(dockVisible(immersive = true, scrollVisible = true))
    }
}
