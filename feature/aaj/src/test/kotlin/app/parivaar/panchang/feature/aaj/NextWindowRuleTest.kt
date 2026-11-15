package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.Synthetic.chog
import app.parivaar.panchang.feature.aaj.Synthetic.day
import app.parivaar.panchang.feature.aaj.Synthetic.t
import app.parivaar.panchang.feature.aaj.Synthetic.win
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class NextWindowRuleTest {
    private val rahu = win(WindowKind.RAHU_KAAL, 15.0, 16.5)
    private val brahma = win(WindowKind.BRAHMA_MUHURTA, 28.5, 29.3)
    private val days = listOf(day(0, listOf(rahu, chog("LABH", 9.0, 10.0))), day(1, listOf(brahma)))

    @Test fun `choghadiya is not a shown window`() {
        val n = NextWindowRule.next(t(8.0), days)!!
        assertEquals(WindowKind.RAHU_KAAL, n.window.kind)
        assertFalse(n.active)
        assertEquals(7.hours(), n.startsIn)
    }

    @Test fun `exactly at start the window is active`() {
        val n = NextWindowRule.next(t(15.0), days)!!
        assertTrue(n.active)
        assertEquals(0.minutes, n.startsIn)
    }

    @Test fun `exactly at end the next window is returned`() {
        assertEquals(WindowKind.BRAHMA_MUHURTA, NextWindowRule.next(t(16.5), days)!!.window.kind)
    }

    @Test fun `across midnight it finds tomorrow's window`() {
        val n = NextWindowRule.next(t(23.98), days)!!
        assertEquals(WindowKind.BRAHMA_MUHURTA, n.window.kind)
        assertEquals(t(28.5) - t(23.98), n.startsIn)
    }

    @Test fun `before sunrise a window of the previous day can still be active`() {
        val dm1 = day(-1, listOf(win(WindowKind.BRAHMA_MUHURTA, 4.5, 5.3)))
        assertTrue(NextWindowRule.next(t(5.0), listOf(dm1, days[0]))!!.active)
    }

    @Test fun `nothing left gives null`() {
        assertNull(NextWindowRule.next(t(40.0), days))
    }

    private fun Int.hours() = (this * 60).minutes
}
