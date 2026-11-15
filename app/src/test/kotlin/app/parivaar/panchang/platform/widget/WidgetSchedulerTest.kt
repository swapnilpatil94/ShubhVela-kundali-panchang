@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.widget

import app.parivaar.panchang.engine.panchang.Direction
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Shool
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.Transitions
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WidgetSchedulerTest {
    private val offset = 19_800
    private val date = LocalDate(2026, 11, 15)
    private val sr = Instant.parse("2026-11-15T00:56:00Z")
    private val rahu = Window(WindowKind.RAHU_KAAL, 0, null, false, sr + 9.hours, sr + 10.hours)
    private val day =
        PanchangDay(
            date, offset, Vaar.of(date), sr, sr + 12.hours, sr + 24.hours, emptySet(), null,
            mapOf(LimbKind.TITHI to listOf(LimbSpan(LimbKind.TITHI, 5, sr - 2.hours, sr + 20.hours))),
            listOf(rahu), emptySet(), Shool(Direction.EAST, "x"),
        )

    private class FakeAlarm : WidgetAlarm {
        val sets = mutableListOf<Pair<Instant, Duration>>()
        var cancelled = 0
        override fun setWindow(at: Instant, window: Duration) { sets += at to window }
        override fun cancel() { cancelled++ }
    }

    @Test
    fun alarmIsAtNextBoundaryWithOneMinuteWindow() {
        val alarm = FakeAlarm()
        val now = sr + 1.hours
        val at = WidgetScheduler(alarm).scheduleNext(now, listOf(day), offset)
        assertEquals(Transitions.nextBoundary(now, listOf(day), offset), at)
        assertEquals(rahu.start, at)
        assertEquals(listOf(rahu.start to 60.seconds), alarm.sets)
    }

    @Test
    fun afterRahuStartNextIsItsEnd() {
        val alarm = FakeAlarm()
        assertEquals(rahu.end, WidgetScheduler(alarm).scheduleNext(rahu.start, listOf(day), offset))
    }

    @Test
    fun neverSchedulesCloserThanMinGap() {
        val alarm = FakeAlarm()
        val now = rahu.start - 5.seconds
        val at = WidgetScheduler(alarm).scheduleNext(now, listOf(day), offset)
        assertTrue(at >= now + WidgetScheduler.MIN_GAP)
    }

    @Test
    fun noDaysFallsBackToLocalMidnightAndCancelWorks() {
        val alarm = FakeAlarm()
        val now = Instant.parse("2026-11-15T10:00:00Z") // 15:30 local
        val at = WidgetScheduler(alarm).scheduleNext(now, emptyList(), offset)
        assertEquals(Instant.parse("2026-11-15T18:30:00Z"), at) // 00:00 local on the 16th
        WidgetScheduler(alarm).cancel()
        assertEquals(1, alarm.cancelled)
    }
}
