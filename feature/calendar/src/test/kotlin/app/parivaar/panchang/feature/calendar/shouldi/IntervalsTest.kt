package app.parivaar.panchang.feature.calendar.shouldi

import app.parivaar.panchang.engine.calendar.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class IntervalsTest {
    private fun r(a: Long, b: Long) = TimeRange(Instant.fromEpochSeconds(a), Instant.fromEpochSeconds(b))

    @Test fun `merge joins overlapping and touching and drops empty`() {
        val input = listOf(r(20, 30), r(0, 10), r(10, 25), r(40, 50), r(60, 60))
        assertEquals(listOf(r(0, 30), r(40, 50)), Intervals.merge(input))
    }

    @Test fun `subtract trims splits and removes`() {
        assertEquals(listOf(r(0, 10), r(20, 30)), Intervals.subtract(listOf(r(0, 30)), listOf(r(10, 20))))
        assertEquals(listOf(r(5, 30)), Intervals.subtract(listOf(r(0, 30)), listOf(r(-5, 5))))
        assertEquals(emptyList(), Intervals.subtract(listOf(r(0, 30)), listOf(r(0, 30))))
        assertEquals(listOf(r(0, 30)), Intervals.subtract(listOf(r(0, 30)), listOf(r(30, 40))))
    }

    @Test fun `clip and overlaps`() {
        assertEquals(listOf(r(5, 10)), Intervals.clip(listOf(r(0, 10), r(20, 30)), r(5, 15)))
        assertTrue(Intervals.overlaps(r(0, 10), r(9, 20)))
        assertFalse(Intervals.overlaps(r(0, 10), r(10, 20)))
    }
}
