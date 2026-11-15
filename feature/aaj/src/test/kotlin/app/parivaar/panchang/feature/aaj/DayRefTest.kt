package app.parivaar.panchang.feature.aaj

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class DayRefTest {
    private val ist = 19_800
    private val shown = LocalDate(2026, 11, 15)

    /** Local time in IST on the given date. */
    private fun ist(d: LocalDate, h: Int, m: Int = 0) =
        Instant.fromEpochSeconds(d.toEpochDays() * 86_400L + h * 3600L + m * 60L - ist)

    @Test fun `same date has no label`() {
        assertEquals(DayRef.Same, dayRef(ist(shown, 0, 0), ist, shown))
        assertEquals(DayRef.Same, dayRef(ist(shown, 23, 59), ist, shown))
    }

    @Test fun `small hours after midnight are the next day`() {
        val next = LocalDate(2026, 11, 16)
        for ((h, m) in listOf(0 to 0, 2 to 1, 2 to 17, 5 to 59)) {
            assertEquals(DayRef.Next, dayRef(ist(next, h, m), ist, shown), "$h:$m")
        }
        assertEquals(DayRef.Next, dayRef(ist(next, 23, 59), ist, shown))
    }

    @Test fun `two days later is an explicit date`() {
        val later = LocalDate(2026, 11, 17)
        assertEquals(DayRef.Other(later), dayRef(ist(later, 4, 20), ist, shown))
        assertEquals(DayRef.Other(later), dayRef(ist(later, 0, 0), ist, shown))
    }

    @Test fun `before the shown date is previous, earlier is explicit`() {
        assertEquals(DayRef.Previous, dayRef(ist(LocalDate(2026, 11, 14), 23, 25), ist, shown))
        assertEquals(DayRef.Other(LocalDate(2026, 11, 13)), dayRef(ist(LocalDate(2026, 11, 13), 23, 0), ist, shown))
    }

    @Test fun `another offset moves the civil date`() {
        // 2026-11-16 02:00 UTC is Nov 15 21:00 in UTC-5 (same day) and Nov 16 07:30 in UTC+5:30 (next day).
        val at = Instant.fromEpochSeconds(LocalDate(2026, 11, 16).toEpochDays() * 86_400L + 2 * 3600L)
        assertEquals(DayRef.Same, dayRef(at, -5 * 3600, shown))
        assertEquals(DayRef.Next, dayRef(at, ist, shown))
        // UTC+13 (Tonga): 2026-11-16 02:00 UTC is Nov 16 15:00; Nov 17 11:00 UTC is Nov 18 00:00 (two days on).
        val tonga = Instant.fromEpochSeconds(LocalDate(2026, 11, 17).toEpochDays() * 86_400L + 11 * 3600L)
        assertEquals(DayRef.Other(LocalDate(2026, 11, 18)), dayRef(tonga, 13 * 3600, shown))
    }

    @Test fun `a range inside the next day is labelled once at the end`() {
        val next = LocalDate(2026, 11, 16)
        assertEquals(DayRef.Same to DayRef.Next, rangeRefs(ist(next, 1, 25), ist(next, 3, 3), ist, shown))
        val later = LocalDate(2026, 11, 17)
        assertEquals(DayRef.Same to DayRef.Other(later), rangeRefs(ist(later, 1, 0), ist(later, 3, 0), ist, shown))
    }

    @Test fun `a range across dates labels each end`() {
        val next = LocalDate(2026, 11, 16)
        assertEquals(DayRef.Same to DayRef.Next, rangeRefs(ist(shown, 22, 0), ist(next, 1, 0), ist, shown))
        val later = LocalDate(2026, 11, 17)
        assertEquals(DayRef.Next to DayRef.Other(later), rangeRefs(ist(next, 23, 0), ist(later, 1, 0), ist, shown))
        assertEquals(DayRef.Same to DayRef.Same, rangeRefs(ist(shown, 9, 0), ist(shown, 10, 0), ist, shown))
    }

    @Test fun `tomorrow wording depends on the date of the start`() {
        // 03:00 on the shown date: the next good time can still be today.
        assertEquals(NextPhrase.LATER_TODAY, nextPhrase(dayRef(ist(shown, 6, 31), ist, shown)))
        assertEquals(NextPhrase.TOMORROW, nextPhrase(dayRef(ist(LocalDate(2026, 11, 16), 6, 31), ist, shown)))
        assertEquals(NextPhrase.DATED, nextPhrase(dayRef(ist(LocalDate(2026, 11, 17), 6, 31), ist, shown)))
    }
}
