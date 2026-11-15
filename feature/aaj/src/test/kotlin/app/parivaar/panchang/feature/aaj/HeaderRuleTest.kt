package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.Synthetic.chog
import app.parivaar.panchang.feature.aaj.Synthetic.day
import app.parivaar.panchang.feature.aaj.Synthetic.t
import app.parivaar.panchang.feature.aaj.Synthetic.win
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderRuleTest {
    @Test fun `no days or now outside every day is unavailable`() {
        assertEquals(Header.Unavailable, HeaderRule.evaluate(t(10.0), emptyList()))
        assertEquals(Header.Unavailable, HeaderRule.evaluate(t(100.0), listOf(day(0))))
    }

    @Test fun `inside an interval waits until its end and names the next good time`() {
        val d0 = day(0, listOf(win(WindowKind.RAHU_KAAL, 15.0, 16.5), chog("SHUBH", 16.5, 18.0)))
        assertEquals(
            Header.WaitUntil(WindowKind.RAHU_KAAL, t(16.5), t(16.5)),
            HeaderRule.evaluate(t(15.5), listOf(d0, day(1))),
        )
    }

    @Test fun `overlapping intervals pick the latest end`() {
        val d0 = day(
            0,
            listOf(
                win(WindowKind.RAHU_KAAL, 15.0, 16.5), win(WindowKind.YAMAGANDA, 15.0, 17.0),
                chog("LABH", 17.0, 18.0),
            ),
        )
        assertEquals(
            Header.WaitUntil(WindowKind.YAMAGANDA, t(17.0), t(17.0)),
            HeaderRule.evaluate(t(15.2), listOf(d0)),
        )
    }

    @Test fun `inside a good interval it is good until its end`() {
        val d0 = day(0, listOf(chog("SHUBH", 9.0, 12.0)))
        assertEquals(Header.GoodUntil(t(12.0)), HeaderRule.evaluate(t(10.0), listOf(d0)))
    }

    @Test fun `an interval inside the good time caps it`() {
        val d0 = day(0, listOf(chog("SHUBH", 9.0, 12.0), win(WindowKind.RAHU_KAAL, 11.0, 12.5)))
        assertEquals(Header.GoodUntil(t(11.0)), HeaderRule.evaluate(t(10.0), listOf(d0)))
    }

    @Test fun `later good time in the day is next good`() {
        val d0 = day(0, listOf(chog("LABH", 9.0, 10.0), chog("KAAL", 10.0, 11.0)))
        assertEquals(Header.NextGood(t(9.0), t(10.0)), HeaderRule.evaluate(t(7.0), listOf(d0)))
    }

    @Test fun `only bad-neutral choghadiya is not good`() {
        val d0 = day(0, listOf(chog("UDVEG", 9.0, 10.0), chog("ROG", 10.0, 11.0)))
        assertEquals(Header.TomorrowFrom(null), HeaderRule.evaluate(t(7.0), listOf(d0, day(1))))
    }

    @Test fun `after the day's good times it points at tomorrow`() {
        val d0 = day(0, listOf(chog("AMRIT", 9.0, 10.0)))
        val d1 = day(1, listOf(chog("AMRIT", 31.0, 32.0)))
        assertEquals(Header.TomorrowFrom(t(31.0)), HeaderRule.evaluate(t(20.0), listOf(d0, d1)))
        assertEquals(Header.TomorrowFrom(null), HeaderRule.evaluate(t(20.0), listOf(d0, day(1))))
    }

    @Test fun `abhijit counts as good`() {
        val d0 = day(0, listOf(win(WindowKind.ABHIJIT, 11.6, 12.4)))
        assertEquals(Header.GoodUntil(t(12.4)), HeaderRule.evaluate(t(12.0), listOf(d0)))
    }

    @Test fun `before sunrise the previous day is the current one`() {
        val dm1 = day(-1, listOf(chog("SHUBH", 1.0, 4.0)))
        val d0 = day(0, listOf(chog("AMRIT", 7.0, 8.0)))
        assertEquals(Header.GoodUntil(t(4.0)), HeaderRule.evaluate(t(3.0), listOf(dm1, d0)))
        assertEquals(Header.TomorrowFrom(t(7.0)), HeaderRule.evaluate(t(5.0), listOf(dm1, d0)))
    }
}
