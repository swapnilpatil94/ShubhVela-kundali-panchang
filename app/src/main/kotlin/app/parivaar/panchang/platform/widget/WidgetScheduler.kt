package app.parivaar.panchang.platform.widget

import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.feature.aaj.Transitions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Port over a single non-wakeup inexact alarm that refreshes the widgets. */
interface WidgetAlarm {
    fun setWindow(at: Instant, window: Duration)

    fun cancel()
}

/**
 * Widgets update only when something on them can change: the next [Transitions.nextBoundary] (limb end, window
 * edge, sunrise or local midnight). `updatePeriodMillis` stays 0, and there are no per-second updates.
 */
class WidgetScheduler(private val alarm: WidgetAlarm) {
    /** Schedules the next refresh and returns its time. Never closer than [MIN_GAP] to [now]. */
    fun scheduleNext(now: Instant, days: List<PanchangDay>, utcOffsetSeconds: Int): Instant {
        val boundary = Transitions.nextBoundary(now, days, utcOffsetSeconds)
        val at = maxOf(boundary, now + MIN_GAP)
        alarm.setWindow(at, WINDOW)
        return at
    }

    fun cancel() = alarm.cancel()

    companion object {
        val WINDOW: Duration = 60.seconds
        val MIN_GAP: Duration = 30.seconds
    }
}
