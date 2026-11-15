@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.widget

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
            Instant.parse(from), Instant.parse(to), offset, shown, { "t" }, "(next day)", "(previous day)",
        ) { d -> "(${d.monthNumber}/${d.dayOfMonth})" }

    @Test fun rangeOnShownDateHasNoLabels() = assertEquals("t" to "t", range("2026-11-15T06:00:00Z", "2026-11-15T07:00:00Z"))

    @Test fun rangeOnSameNextDayLabelsOnlyTheEnd() = assertEquals("t" to "t (next day)", range("2026-11-15T19:55:00Z", "2026-11-15T21:33:00Z"))

    @Test fun rangeAcrossMidnightLabelsEachEnd() = assertEquals("t" to "t (next day)", range("2026-11-15T10:00:00Z", "2026-11-15T20:00:00Z"))

    @Test fun rangeTwoDaysOutLabelsTheDateOnce() = assertEquals("t" to "t (11/17)", range("2026-11-16T19:55:00Z", "2026-11-16T21:00:00Z"))

    @Test fun paranaTheDayAfterTomorrowLabelsTheDateRelativeToToday() =
        assertEquals("t" to "t (11/17)", range("2026-11-16T19:55:00Z", "2026-11-16T21:00:00Z"))

    @Test fun paranaTomorrowLabelsNextDay() = assertEquals("t" to "t (next day)", range("2026-11-15T19:55:00Z", "2026-11-15T21:00:00Z"))
}
