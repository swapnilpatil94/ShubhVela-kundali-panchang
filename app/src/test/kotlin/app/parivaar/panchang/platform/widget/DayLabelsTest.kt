@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.widget

import app.parivaar.panchang.feature.aaj.PlaceClock
import app.parivaar.panchang.feature.aaj.dayRef
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DayLabelsTest {
    private val offset = 19_800 // +05:30
    private val shown = LocalDate(2026, 11, 15)

    private fun label(utc: String): String {
        val at = Instant.parse(utc)
        return timeWithDayRef("2:01 AM", dayRef(at, offset, shown), "(next day)", "(previous day)") { d -> "(${d.monthNumber}/${d.dayOfMonth})" }
    }

    @Test fun sameDayHasNoLabel() = assertEquals("2:01 AM", label("2026-11-15T10:00:00Z"))

    @Test fun plusOneDayIsNextDay() = assertEquals("2:01 AM (next day)", label("2026-11-15T20:31:00Z")) // 02:01 local on the 16th

    @Test fun plusTwoDaysIsExplicitDate() = assertEquals("2:01 AM (11/17)", label("2026-11-16T20:31:00Z"))

    @Test fun minusOneDayIsPreviousDay() = assertEquals("2:01 AM (previous day)", label("2026-11-14T10:00:00Z"))

    @Test fun localMidnightEdges() {
        assertEquals("2:01 AM", label("2026-11-14T18:30:00Z")) // 00:00 local on the 15th
        assertEquals("2:01 AM (next day)", label("2026-11-15T18:30:00Z")) // 00:00 local on the 16th
    }

    private fun range(from: String, to: String) =
        rangeWithDayRef(
            Instant.parse(from), Instant.parse(to), PlaceClock.fixed(offset), shown, { "t" }, "(next day)", "(previous day)",
        ) { d -> "(${d.monthNumber}/${d.dayOfMonth})" }

    @Test fun rangeOnShownDateHasNoLabels() = assertEquals("t" to "t", range("2026-11-15T06:00:00Z", "2026-11-15T07:00:00Z"))

    @Test fun rangeOnSameNextDayLabelsOnlyTheEnd() = assertEquals("t" to "t (next day)", range("2026-11-15T19:55:00Z", "2026-11-15T21:33:00Z"))

    @Test fun rangeAcrossMidnightLabelsEachEnd() = assertEquals("t" to "t (next day)", range("2026-11-15T10:00:00Z", "2026-11-15T20:00:00Z"))

    @Test fun rangeTwoDaysOutLabelsTheDateOnce() = assertEquals("t" to "t (11/17)", range("2026-11-16T19:55:00Z", "2026-11-16T21:00:00Z"))

    @Test fun paranaTheDayAfterTomorrowLabelsTheDateRelativeToToday() =
        assertEquals("t" to "t (11/17)", range("2026-11-16T19:55:00Z", "2026-11-16T21:00:00Z"))

    @Test fun dstFallBackEveningIsStillTheShownDayInNewYork() {
        val day = LocalDate(2026, 11, 1)
        val ny = PlaceClock("America/New_York", -14_400, day)
        // 23:10 and 23:50 EST on Nov 1: a fixed EDT offset would call them 00:10 and 00:50 "next day".
        val r = rangeWithDayRef(Instant.parse("2026-11-02T04:10:00Z"), Instant.parse("2026-11-02T04:50:00Z"), ny, day, { "t" }, "(next day)", "(previous day)") { "x" }
        assertEquals("t" to "t", r)
        assertEquals("t (next day)", timeWithDayRef("t", ny.dayRef(Instant.parse("2026-11-02T05:00:00Z"), day), "(next day)", "(previous day)") { "x" })
    }

    @Test fun paranaTomorrowLabelsNextDay() = assertEquals("t" to "t (next day)", range("2026-11-15T19:55:00Z", "2026-11-15T21:00:00Z"))
}
