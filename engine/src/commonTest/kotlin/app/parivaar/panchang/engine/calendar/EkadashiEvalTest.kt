package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.calendar.internal.ekadashiDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** docs/specs/M04.md section 4, `EkadashiEvalTest` (synthetic spans, sunrise 06:00 UTC daily). */
class EkadashiEvalTest {
    private val d = LocalDate(2026, 6, 10)
    private val d1 = d.plus(DatePeriod(days = 1))
    private val d2 = d.plus(DatePeriod(days = 2))

    private fun t(day: LocalDate, h: Int, m: Int = 0): Instant =
        Instant.fromEpochSeconds(day.toEpochDays() * 86_400L) + h.hours + m.minutes

    private val sunrise = { day: LocalDate -> t(day, 6) }
    private val containing = { x: Instant ->
        val shifted = x - 6.hours
        LocalDate.fromEpochDays(shifted.epochSeconds.floorDiv(86_400L).toInt())
    }

    private fun run(e: TimeRange, w: TimeRange, tr: Tradition) = ekadashiDay(e, w, tr, sunrise, containing)

    @Test
    fun dashamiViddhaByArunodaya() {
        val w = TimeRange(t(d1, 5), t(d2, 12))
        val early = TimeRange(t(d, 4), t(d1, 5))
        assertEquals(d, run(early, w, Tradition.SMARTA).first)
        assertEquals(d, run(early, w, Tradition.VAISHNAVA).first)
        assertEquals(emptySet(), run(early, w, Tradition.VAISHNAVA).third)
        val edge = TimeRange(t(d, 4, 24), t(d1, 5))
        assertEquals(d, run(edge, w, Tradition.VAISHNAVA).first)
        val late = TimeRange(t(d, 4, 30), t(d1, 5))
        assertEquals(d, run(late, w, Tradition.SMARTA).first)
        val v = run(late, w, Tradition.VAISHNAVA)
        assertEquals(d1, v.first)
        assertEquals(setOf(OccFlag.DASHAMI_VIDDHA), v.third)
    }

    @Test
    fun kshayaAndVriddhi() {
        val w = TimeRange(t(d1, 5), t(d2, 12))
        val kshaya = TimeRange(t(d, 7), t(d1, 5))
        val s = run(kshaya, w, Tradition.SMARTA)
        assertEquals(d, s.first)
        assertEquals(setOf(OccFlag.KSHAYA_TITHI), s.third)
        val v = run(kshaya, w, Tradition.VAISHNAVA)
        assertEquals(d1, v.first)
        assertEquals(setOf(OccFlag.KSHAYA_TITHI, OccFlag.DASHAMI_VIDDHA), v.third)
        val vriddhi = TimeRange(t(d, 5), t(d1, 7))
        val r = run(vriddhi, TimeRange(t(d1, 7), t(d2, 7)), Tradition.SMARTA)
        assertEquals(d, r.first)
        assertEquals(setOf(OccFlag.VRIDDHI_TITHI), r.third)
    }

    @Test
    fun paranaFromHariVasaraToDwadashiEnd() {
        val e = TimeRange(t(d, 4), t(d1, 5))
        assertEquals(TimeRange(t(d1, 11), t(d2, 5)), run(e, TimeRange(t(d1, 5), t(d2, 5)), Tradition.SMARTA).second)
        assertEquals(TimeRange(t(d1, 6), t(d1, 9)), run(e, TimeRange(t(d1, 5), t(d1, 9)), Tradition.SMARTA).second)
        val late = TimeRange(t(d, 4, 30), t(d1, 5))
        val v = run(late, TimeRange(t(d1, 5), t(d2, 5)), Tradition.VAISHNAVA)
        assertNull(v.second)
        assertEquals(setOf(OccFlag.DASHAMI_VIDDHA, OccFlag.PARANA_UNDEFINED), v.third)
    }
}
