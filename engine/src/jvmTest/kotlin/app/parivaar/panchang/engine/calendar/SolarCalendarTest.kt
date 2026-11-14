package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.calendar.internal.DayLookup
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.internal.SunDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** docs/specs/M04.md section 4, `SolarCalendarTest`. */
class SolarCalendarTest {
    private val p = LocalDate(2026, 4, 14)

    /** Synthetic days at +05:30: sunrise 06:00, sunset 18:00 local, next sunrise 06:00 (night midpoint 00:00). */
    private fun synthetic(offset: (LocalDate) -> Int = { 19_800 }, rise: Duration = 6.hours) =
        DayLookup(offset) { _, m -> SunDay(m, m + rise, m + 18.hours, m + 24.hours + rise, emptySet()) }

    private fun local(d: LocalDate, h: Duration): Instant =
        Instant.fromEpochSeconds(d.toEpochDays() * 86_400L - 19_800) + h

    private fun kept(at: Instant, rule: SankrantiDay, lookup: DayLookup = synthetic()) =
        lookup.sankrantiDate(Sankranti(Rashi.MESHA, at), rule)

    @Test
    fun rulesAroundTheirCutoffs() {
        val next = LocalDate(2026, 4, 15)
        val after = LocalDate(2026, 4, 16)
        val sunset = local(p, 18.hours)
        assertEquals(p, kept(sunset - 1.seconds, SankrantiDay.BEFORE_SUNSET).date)
        assertEquals(next, kept(sunset + 1.seconds, SankrantiDay.BEFORE_SUNSET).date)
        assertEquals(next, kept(sunset, SankrantiDay.BEFORE_SUNSET).date)
        assertEquals(sunset, kept(sunset, SankrantiDay.BEFORE_SUNSET).cutoff)
        val threeFifths = local(p, 6.hours + 432.minutes)
        assertEquals(threeFifths, kept(threeFifths, SankrantiDay.BEFORE_THREE_FIFTHS).cutoff)
        assertEquals(p, kept(threeFifths - 1.seconds, SankrantiDay.BEFORE_THREE_FIFTHS).date)
        assertEquals(next, kept(threeFifths + 1.seconds, SankrantiDay.BEFORE_THREE_FIFTHS).date)
        val mid = local(p, 24.hours)
        assertEquals(mid, kept(mid, SankrantiDay.BEFORE_MIDNIGHT_NEXT).cutoff)
        assertEquals(next, kept(mid - 1.seconds, SankrantiDay.BEFORE_MIDNIGHT_NEXT).date)
        assertEquals(after, kept(mid + 1.seconds, SankrantiDay.BEFORE_MIDNIGHT_NEXT).date)
        assertEquals(next, kept(local(p, 7.hours), SankrantiDay.BEFORE_MIDNIGHT_NEXT).date)
        val preDawn = local(next, 5.hours)
        assertEquals(next, kept(preDawn, SankrantiDay.CIVIL).date)
        assertNull(kept(preDawn, SankrantiDay.CIVIL).cutoff)
        // Odia examples of spec 3.3: civil midnight-to-midnight dates at +05:30.
        val odia = SolarCalendar.ruleOf(SolarRegion.ODIA)
        assertEquals(LocalDate(2024, 4, 13), kept(Instant.parse("2024-04-13T15:45:00Z"), odia).date)
        assertEquals(LocalDate(2025, 4, 14), kept(Instant.parse("2025-04-13T22:00:00Z"), odia).date)
        assertEquals(p, kept(local(next, 0.hours) - 1.seconds, odia).date)
    }

    @Test
    fun civilDateAcrossAnOffsetChange() {
        val change = LocalDate(2026, 3, 8)
        val lookup = synthetic({ if (it >= change) -14_400 else -18_000 })
        // 04:30 UTC on 8 March: 23:30 on 7 March at -05:00, 00:30 on 8 March at -04:00.
        assertEquals(change, lookup.civilDate(Instant.parse("2026-03-08T04:30:00Z")))
        assertEquals(LocalDate(2026, 3, 7), lookup.civilDate(Instant.parse("2026-03-08T03:30:00Z")))
        val bad = synthetic({ 50_401 })
        val e = assertFailsWith<IllegalArgumentException> { bad.midnight(p) }
        assertFalse(e.message.orEmpty().any { it.isDigit() })
    }

    @Test
    fun containingDayAndGapAtAnOffsetChange() {
        val lookup = synthetic()
        assertEquals(p, lookup.containing(local(p, 6.hours)))
        assertEquals(p, lookup.containing(local(p, 30.hours) - 1.seconds))
        assertEquals(LocalDate(2026, 4, 13), lookup.containing(local(p, 6.hours) - 1.seconds))
        // Offset -1 h from 15 April: day 14 ends at 06:00 (+05:30) but day 15 starts one hour later.
        val gap = synthetic({ if (it >= LocalDate(2026, 4, 15)) 16_200 else 19_800 }, 6.hours)
        assertEquals(p, gap.containing(local(p, 30.hours) + 30.minutes))
    }

    @Test
    fun realMonthStartsAndDates() {
        val solar = SolarCalendar(LunarCalendar(GoldenLoader.openDe440()))
        val chennai = Place(13.0827, 80.2707)
        val ist = { _: LocalDate -> 19_800 }
        val first = LocalDate(2024, 1, 1)
        val last = LocalDate(2024, 12, 31)
        for (r in SolarRegion.entries) {
            val starts = solar.monthStarts(r, first, last, chennai, utcOffsetSeconds = ist)
            assertEquals(12, starts.size, r.name)
            for ((a, b) in starts.zipWithNext()) assertEquals((a.month.ordinal + 1) % 12, b.month.ordinal)
            assertTrue(starts.all { it.region == r && it.date in first..last })
            assertEquals(r == SolarRegion.ODIA, starts.all { it.cutoff == null })
            val mesha = starts.single { it.month == Rashi.MESHA }
            assertTrue(mesha.date in LocalDate(2024, 4, 12)..LocalDate(2024, 4, 15))
            val d = solar.dateOf(r, LocalDate(2024, 6, 1), chennai, utcOffsetSeconds = ist)
            val start = starts.last { it.date <= LocalDate(2024, 6, 1) }
            val day = start.date.daysUntilInclusive(LocalDate(2024, 6, 1))
            assertEquals(SolarDate(r, start.month, day, start.date), d)
        }
        val bad = assertFailsWith<IllegalArgumentException> {
            solar.monthStarts(SolarRegion.TAMIL, last, first, chennai, utcOffsetSeconds = ist)
        }
        assertFalse(bad.message.orEmpty().any { it.isDigit() })
    }

    @Test
    fun syntheticFallbackFlagIsKept() {
        val flagged = DayLookup({ 3_600 }) { _, m ->
            SunDay(m, m + 9.hours, m + 15.hours, m + 33.hours, setOf(DayFlag.SUN_FALLBACK))
        }
        assertEquals(setOf(DayFlag.SUN_FALLBACK), flagged.sunDay(p).flags)
    }

    private fun LocalDate.daysUntilInclusive(other: LocalDate): Int = (other.toEpochDays() - toEpochDays()).toInt() + 1
}
