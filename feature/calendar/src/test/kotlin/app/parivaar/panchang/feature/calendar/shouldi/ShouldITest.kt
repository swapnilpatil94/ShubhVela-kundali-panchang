package app.parivaar.panchang.feature.calendar.shouldi

import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.Direction
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Shool
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class ShouldITest {
    private val base = LocalDate(2026, 11, 15)
    private val epoch0 = base.toEpochDays() * 86_400L
    private fun t(h: Double) = Instant.fromEpochSeconds(epoch0 + (h * 3600).toLong())
    private fun chog(code: String, a: Double, b: Double) = Window(WindowKind.CHOGHADIYA, 1, code, false, t(a), t(b))
    private fun win(k: WindowKind, a: Double, b: Double) = Window(k, 1, null, false, t(a), t(b))

    private fun day(n: Int, windows: List<Window>, nak: Int = 3): PanchangDay {
        val date = base.plus(DatePeriod(days = n))
        val o = 24.0 * n
        return PanchangDay(
            date = date, utcOffsetSeconds = 0, vaar = Vaar.of(date), sunrise = t(o + 6), sunset = t(o + 18),
            nextSunrise = t(o + 30), flags = emptySet(), backend = null,
            limbs = mapOf(LimbKind.NAKSHATRA to listOf(LimbSpan(LimbKind.NAKSHATRA, nak, t(o + 2), t(o + 20)))),
            windows = windows, unavailable = emptySet(), shool = Shool(Direction.EAST, "x"),
        )
    }

    private val avoid = setOf(WindowKind.RAHU_KAAL, WindowKind.VARJYAM)
    private fun rule(
        tithi: Set<Int> = emptySet(), vaar: Set<Vaar> = emptySet(), nak: Set<Int> = emptySet(),
    ) = IntentRule("x", avoid, setOf("AMRIT", "SHUBH", "LABH"), tithi, vaar, nak, RuleStatus.VERIFY, "s")

    private val days = listOf(
        day(
            0,
            listOf(
                chog("AMRIT", 8.0, 9.5), chog("ROG", 9.5, 11.0),
                win(WindowKind.ABHIJIT, 11.5, 12.3), chog("LABH", 16.0, 17.5),
            ),
        ),
        day(1, listOf(chog("SHUBH", 31.0, 32.5))),
    )

    @Test fun `no days is unavailable`() {
        assertEquals(Verdict.UNAVAILABLE, ShouldI.answer(rule(), t(9.0), emptyList(), 5).verdict)
    }

    @Test fun `polar day gives no days so unavailable and a stale clock too`() {
        assertEquals(Verdict.UNAVAILABLE, ShouldI.answer(rule(), t(31.0), days, 5).verdict)
    }

    @Test fun `no good window anywhere is unavailable`() {
        val d = listOf(day(0, listOf(chog("ROG", 8.0, 9.0))), day(1, listOf(chog("KAAL", 8.0, 9.0))))
        val a = ShouldI.answer(rule(), t(7.0), d, 5)
        assertEquals(Verdict.UNAVAILABLE, a.verdict)
        assertNull(a.best)
    }

    @Test fun `tithi vaar and nakshatra checks name each failure`() {
        val a = ShouldI.answer(rule(tithi = setOf(5), vaar = setOf(Vaar.MONDAY), nak = setOf(9)), t(9.0), days, 5)
        assertEquals(Verdict.NOT_FAVOURED_TODAY, a.verdict)
        assertNull(a.best)
        assertEquals(listOf(ShouldI.WHY_TITHI, ShouldI.WHY_VAAR, ShouldI.WHY_NAKSHATRA), a.whyIds)
    }

    @Test fun `passing checks do not block`() {
        val a = ShouldI.answer(rule(tithi = setOf(6), vaar = setOf(Vaar.SUNDAY), nak = setOf(3)), t(9.0), days, 5)
        assertEquals(Verdict.GOOD_NOW, a.verdict)
    }

    @Test fun `good now`() {
        val a = ShouldI.answer(rule(), t(9.0), days, 5)
        assertEquals(Verdict.GOOD_NOW, a.verdict)
        assertEquals(t(8.0), a.best!!.start)
        assertEquals(t(9.5), a.best!!.end)
        assertEquals(listOf("why_choghadiya_amrit"), a.whyIds)
    }

    @Test fun `end of window is exclusive`() {
        assertEquals(Verdict.GOOD_LATER, ShouldI.answer(rule(), t(9.5), days, 5).verdict)
    }

    @Test fun `good later picks the earliest`() {
        val a = ShouldI.answer(rule(), t(10.0), days, 5)
        assertEquals(Verdict.GOOD_LATER, a.verdict)
        assertEquals(t(11.5), a.best!!.start)
        assertEquals(listOf(ShouldI.WHY_ABHIJIT), a.whyIds)
    }

    @Test fun `no window left today gives tomorrow`() {
        val a = ShouldI.answer(rule(), t(17.6), days, 5)
        assertEquals(Verdict.TOMORROW, a.verdict)
        assertEquals(t(31.0), a.best!!.start)
    }

    @Test fun `night after sunset gives tomorrow`() {
        assertEquals(Verdict.TOMORROW, ShouldI.answer(rule(), t(22.0), days, 5).verdict)
    }

    @Test fun `avoid overlapping prefer trims best`() {
        val d = listOf(
            day(0, listOf(chog("AMRIT", 8.0, 10.0), win(WindowKind.RAHU_KAAL, 9.0, 9.5))),
            day(1, emptyList()),
        )
        val now = ShouldI.answer(rule(), t(8.5), d, 5)
        assertEquals(Verdict.GOOD_NOW, now.verdict)
        assertEquals(t(9.0), now.best!!.end)
        assertTrue("why_avoid_rahu_kaal" in now.whyIds)
        val later = ShouldI.answer(rule(), t(9.2), d, 5)
        assertEquals(Verdict.GOOD_LATER, later.verdict)
        assertEquals(t(9.5), later.best!!.start)
    }

    @Test fun `window wholly inside an avoid is dropped`() {
        val d = listOf(
            day(0, listOf(chog("LABH", 9.0, 9.5), win(WindowKind.VARJYAM, 8.0, 10.0))),
            day(1, listOf(chog("LABH", 32.0, 33.0))),
        )
        assertEquals(Verdict.TOMORROW, ShouldI.answer(rule(), t(7.0), d, 5).verdict)
    }

    @Test fun `only D is given and nothing left is unavailable`() {
        val d = listOf(days[0])
        assertEquals(Verdict.UNAVAILABLE, ShouldI.answer(rule(), t(17.9), d, 5).verdict)
    }
}
