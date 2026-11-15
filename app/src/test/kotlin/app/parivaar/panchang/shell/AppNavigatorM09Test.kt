package app.parivaar.panchang.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorM09Test {
    @Test fun calendarDestinationsPushAndPopToRoot() {
        val n = AppNavigator(Tab.CALENDAR)
        n.push(Dest.FestivalGuide("holi"))
        n.push(Dest.Grahan)
        n.push(Dest.Transits)
        n.push(Dest.ShouldI)
        assertEquals(Dest.ShouldI, n.current.value)
        assertTrue(n.back())
        assertEquals(Dest.Transits, n.current.value)
        assertTrue(n.back())
        assertTrue(n.back())
        assertEquals(Dest.FestivalGuide("holi"), n.current.value)
        assertTrue(n.back())
        assertEquals(Dest.Root(Tab.CALENDAR), n.current.value)
    }

    @Test fun saveRestoreRoundTrip() {
        val n = AppNavigator(Tab.CALENDAR)
        n.push(Dest.FestivalGuide("maha_shivratri"))
        n.push(Dest.Transits)
        n.push(Dest.ShouldI)
        n.push(Dest.Grahan)
        val r = AppNavigator.restore(n.save())
        assertEquals(n.save(), r.save())
        assertEquals(Dest.Grahan, r.current.value)
        r.back()
        r.back()
        r.back()
        assertEquals(Dest.FestivalGuide("maha_shivratri"), r.current.value)
    }

    @Test fun restoreRejectsBadGuideIds() {
        val r = AppNavigator.restore(listOf("CALENDAR", "CALENDAR:guide=,guide=Bad Id,guide=ok_1,guide=../x"))
        assertEquals(Dest.FestivalGuide("ok_1"), r.current.value)
        r.back()
        assertEquals(Dest.Root(Tab.CALENDAR), r.current.value)
    }

    @Test fun guideDestinationsAreSeparatePerId() {
        val n = AppNavigator(Tab.CALENDAR)
        n.push(Dest.FestivalGuide("holi"))
        n.push(Dest.FestivalGuide("diwali_lakshmi_puja"))
        assertEquals(Dest.FestivalGuide("diwali_lakshmi_puja"), n.current.value)
        assertTrue(n.canGoBack.value)
        n.select(Tab.CALENDAR)
        assertEquals(Dest.Root(Tab.CALENDAR), n.current.value)
        assertFalse(n.save().any { it.contains("guide") })
    }
}
