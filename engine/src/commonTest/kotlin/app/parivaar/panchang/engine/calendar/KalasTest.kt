package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.calendar.internal.DayLookup
import app.parivaar.panchang.engine.calendar.internal.Kalas
import app.parivaar.panchang.engine.calendar.internal.TithiRuleEval
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.panchang.internal.SunDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** docs/specs/M04.md section 4, `KalasTest` (synthetic sr 00:00Z, ss 12:00Z, nsr 24:00Z). */
class KalasTest {
    private val t0 = Instant.parse("2026-03-01T00:00:00Z")
    private val day = SunDay(t0 - 6.hours, t0, t0 + 12.hours, t0 + 24.hours, emptySet())

    private fun at(h: Int, m: Int = 0) = t0 + h.hours + m.minutes

    private fun w(k: Kala, moon: Instant? = null) = Kalas.window(k, day) { moon }

    @Test
    fun windows() {
        assertEquals(TimeRange(t0, t0), w(Kala.SUNRISE))
        assertEquals(TimeRange(at(-1, -36), t0), w(Kala.ARUNODAYA))
        assertEquals(TimeRange(at(12), at(14, 24)), w(Kala.PRADOSH))
        assertEquals(TimeRange(at(17, 36), at(18, 24)), w(Kala.NISHITA))
        assertEquals(TimeRange(at(4, 48), at(7, 12)), w(Kala.MADHYAHNA))
        assertEquals(TimeRange(at(7, 12), at(9, 36)), w(Kala.APARAHNA))
        assertEquals(TimeRange(t0, at(24)), w(Kala.AHORATRA))
        assertEquals(TimeRange(at(19), at(19)), w(Kala.MOONRISE, at(19)))
        assertNull(w(Kala.MOONRISE))
    }

    @Test
    fun regionalCutoffs() {
        val lookup = DayLookup({ 0 }) { _, m -> SunDay(m, m + 0.hours, m + 12.hours, m + 24.hours, emptySet()) }
        val s = Sankranti(Rashi.MESHA, at(3))
        assertEquals(at(7, 12), lookup.sankrantiDate(s, SankrantiDay.BEFORE_THREE_FIFTHS).cutoff)
        assertEquals(at(18), lookup.sankrantiDate(s, SankrantiDay.BEFORE_MIDNIGHT_NEXT).cutoff)
        assertEquals(LocalDate(2026, 3, 1), lookup.sankrantiDate(s, SankrantiDay.BEFORE_THREE_FIFTHS).date)
        assertEquals(LocalDate(2026, 3, 2), lookup.sankrantiDate(s, SankrantiDay.BEFORE_MIDNIGHT_NEXT).date)
    }

    @Test
    fun coverageAndFreePieces() {
        val x = TimeRange(at(5), at(13))
        assertEquals(1L, Kalas.coverage(Kala.SUNRISE, TimeRange(at(5), at(5)), x))
        assertEquals(0L, Kalas.coverage(Kala.SUNRISE, TimeRange(at(13), at(13)), x))
        assertEquals(0L, Kalas.coverage(Kala.MOONRISE, null, x))
        assertEquals(2L * 3_600_000_000_000L, Kalas.coverage(Kala.PRADOSH, w(Kala.PRADOSH), TimeRange(at(10), at(14))))
        assertEquals(0L, Kalas.coverage(Kala.NISHITA, w(Kala.NISHITA), x))
        val v = listOf(TimeRange(at(6), at(7)), TimeRange(at(9), at(10)))
        assertEquals(
            listOf(TimeRange(at(5), at(6)), TimeRange(at(7), at(9)), TimeRange(at(10), at(13))),
            TithiRuleEval.free(x, v),
        )
        assertEquals(emptyList(), TithiRuleEval.free(TimeRange(at(6), at(7)), v))
    }
}
