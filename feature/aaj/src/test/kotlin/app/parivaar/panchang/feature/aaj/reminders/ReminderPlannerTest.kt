@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.reminders

import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Shool
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ReminderPlannerTest {
    private val pune = TimeZone.of("Asia/Kolkata")
    private val ny = TimeZone.of("America/New_York")
    private fun at(z: TimeZone, d: LocalDate, h: Int, m: Int = 0): Instant = LocalDateTime(d, kotlinx.datetime.LocalTime(h, m)).toInstant(z)
    private val d0 = LocalDate(2026, 11, 15)
    private fun d(n: Int) = d0.plus(DatePeriod(days = n))
    private val all = ReminderKind.entries.toSet()

    private fun occ(id: String, date: LocalDate) = FestivalOccurrence(id, date, null, null, null, null, null, null, emptySet())

    private fun eclipse(z: TimeZone, date: LocalDate): app.parivaar.panchang.engine.calendar.LocalEclipse = FakeEclipse.make(at(z, date, 21), at(z, date, 23))

    private fun day(z: TimeZone, date: LocalDate, brahmaH: Int = 4, brahmaM: Int = 30): PanchangDay {
        val sr = at(z, date, 6, 30)
        return PanchangDay(
            date = date, utcOffsetSeconds = sr.offsetIn(z).totalSeconds, vaar = Vaar.of(date), sunrise = sr,
            sunset = at(z, date, 18), nextSunrise = at(z, date.plus(DatePeriod(days = 1)), 6, 30), flags = emptySet(),
            backend = null,
            limbs = mapOf(LimbKind.TITHI to listOf(LimbSpan(LimbKind.TITHI, 1, sr - 1.hours, sr + 30.hours))),
            windows = listOf(Window(WindowKind.BRAHMA_MUHURTA, 0, null, false, at(z, date, brahmaH, brahmaM), at(z, date, 5, 18))),
            unavailable = emptySet(), shool = Shool(app.parivaar.panchang.engine.panchang.Direction.EAST, "x"),
        )
    }

    private fun days(z: TimeZone, n: Int = 15, from: LocalDate = d0) = (0 until n).map { day(z, from.plus(DatePeriod(days = it))) }

    private fun plan(
        now: Instant,
        z: TimeZone = pune,
        days: List<PanchangDay> = days(z),
        fest: List<FestivalOccurrence> = emptyList(),
        ecl: List<app.parivaar.panchang.engine.calendar.LocalEclipse> = emptyList(),
        fam: List<LocalDate> = emptyList(),
        on: Set<ReminderKind> = all,
    ) = ReminderPlanner.plan(now, z, days, fest, setOf("ekadashi_x"), ecl, fam, on)

    @Test
    fun emptySettingsPlanNothing() {
        assertEquals(emptyList(), plan(at(pune, d0, 8), fest = listOf(occ("ekadashi_x", d(3))), on = emptySet()))
    }

    @Test
    fun vratEveIs1900TheEveningBefore() {
        val p = plan(at(pune, d0, 8), fest = listOf(occ("ekadashi_x", d(3))), on = setOf(ReminderKind.VRAT_EVE))
        assertEquals(listOf(at(pune, d(2), 19)), p.map { it.triggerAt })
        assertEquals(d(3), p.single().eventDate)
    }

    @Test
    fun nonVratFestivalIgnored() {
        assertTrue(plan(at(pune, d0, 8), fest = listOf(occ("diwali", d(3))), on = setOf(ReminderKind.VRAT_EVE)).isEmpty())
    }

    @Test
    fun brahmaFiresAtItsStart() {
        val p = plan(at(pune, d0, 1), on = setOf(ReminderKind.SUNRISE_BRAHMA))
        assertEquals(at(pune, d0, 4, 30), p.first().triggerAt)
        assertEquals(14, p.size)
    }

    @Test
    fun pastTriggersDroppedIncludingExactlyNow() {
        val now = at(pune, d0, 4, 30)
        val p = plan(now, on = setOf(ReminderKind.SUNRISE_BRAHMA))
        assertEquals(d(1), p.first().eventDate)
    }

    @Test
    fun sunriseEdgeBrahmaAfterNowSameDay() {
        val p = plan(at(pune, d0, 6, 30), on = setOf(ReminderKind.SUNRISE_BRAHMA))
        assertEquals(d(1), p.first().eventDate)
    }

    @Test
    fun midnightEdgeEveOfFirstDay() {
        // 00:00 on d0: eve of d1 fires 19:00 d0, inside the horizon.
        val p = plan(at(pune, d0, 0), fest = listOf(occ("ekadashi_x", d(1))), on = setOf(ReminderKind.VRAT_EVE))
        assertEquals(at(pune, d0, 19), p.single().triggerAt)
    }

    @Test
    fun capTwoPerDayWithPriority() {
        val p = plan(
            at(pune, d0, 1), fest = listOf(occ("ekadashi_x", d(2))), ecl = listOf(eclipse(pune, d(2))), fam = listOf(d(2)),
        ).filter { it.triggerAt.toLocalDateTime(pune).date == d(1) }
        assertEquals(listOf(ReminderKind.GRAHAN, ReminderKind.VRAT_EVE), p.map { it.kind })
    }

    @Test
    fun familyBeatsBrahmaButLosesToVrat() {
        val p = plan(at(pune, d0, 1), fam = listOf(d(2)), fest = listOf(occ("ekadashi_x", d(2))))
            .filter { it.triggerAt.toLocalDateTime(pune).date == d(1) }
        assertEquals(setOf(ReminderKind.VRAT_EVE, ReminderKind.FAMILY_DATES), p.map { it.kind }.toSet())
        assertEquals(2, p.size)
    }

    @Test
    fun disabledCategoryProducesNone() {
        val p = plan(at(pune, d0, 1), fest = listOf(occ("ekadashi_x", d(2))), ecl = listOf(eclipse(pune, d(2))), on = setOf(ReminderKind.FAMILY_DATES))
        assertTrue(p.isEmpty())
    }

    @Test
    fun horizonBoundary() {
        val now = at(pune, d0, 1)
        val last = plan(now, fest = listOf(occ("ekadashi_x", d(14))), on = setOf(ReminderKind.VRAT_EVE))
        assertEquals(1, last.size) // eve is d0+13, the last horizon day
        val beyond = plan(now, fest = listOf(occ("ekadashi_x", d(15))), on = setOf(ReminderKind.VRAT_EVE))
        assertTrue(beyond.isEmpty())
    }

    @Test
    fun dstDayKeeps1900Local() {
        val nyDay = LocalDate(2026, 11, 1) // fall back
        val now = at(ny, nyDay.plus(DatePeriod(days = -3)), 8)
        val p = ReminderPlanner.plan(now, ny, emptyList(), listOf(occ("ekadashi_x", nyDay.plus(DatePeriod(days = 1)))), setOf("ekadashi_x"), emptyList(), emptyList(), setOf(ReminderKind.VRAT_EVE))
        val t = p.single().triggerAt.toLocalDateTime(ny)
        assertEquals(nyDay, t.date)
        assertEquals(19, t.hour)
        assertEquals(at(ny, nyDay, 19), p.single().triggerAt)
        assertEquals(-5 * 3600, p.single().triggerAt.offsetIn(ny).totalSeconds)
    }

    @Test
    fun requestCodesStableAndDistinct() {
        val p = plan(at(pune, d0, 1), fest = listOf(occ("ekadashi_x", d(2))))
        assertEquals(p.size, p.map { it.requestCode }.toSet().size)
        assertEquals(PlannedReminder.requestCodeOf(ReminderKind.VRAT_EVE, d(2)), PlannedReminder.requestCodeOf(ReminderKind.VRAT_EVE, d(2)))
    }

    @Test
    fun replaceCancelsThenSetsTwoMinuteWindows() {
        val calls = mutableListOf<String>()
        val sink = object : AlarmSink {
            override fun setWindow(requestCode: Int, triggerAt: Instant, window: kotlin.time.Duration) { calls += "set:${window.inWholeMinutes}" }
            override fun cancelAll() { calls += "cancel" }
        }
        sink.replace(plan(at(pune, d0, 1), fest = listOf(occ("ekadashi_x", d(2))), on = setOf(ReminderKind.VRAT_EVE)))
        assertEquals(listOf("cancel", "set:2"), calls)
    }

    @Test
    fun contentTypesCarryNoNamesOrPlaces() {
        val fields = listOf(
            ReminderContent.VratEve::class, ReminderContent.Grahan::class, ReminderContent.Brahma::class, PlannedReminder::class,
        ).flatMap { c -> c.java.declaredFields.map { it.name.lowercase() } }
        for (bad in listOf("name", "city", "place", "profile", "birth", "lat", "lon")) assertTrue(fields.none { bad in it }, bad)
        assertEquals(0, ReminderContent.FamilyDate::class.java.declaredFields.count { !java.lang.reflect.Modifier.isStatic(it.modifiers) })
        assertEquals("PlannedReminder(redacted)", plan(at(pune, d0, 1)).first().toString())
        assertTrue(FamilyDateSource.None.dates(d0, d(5)).isEmpty())
    }
}

internal object FakeEclipse {
    fun make(start: Instant, end: Instant): app.parivaar.panchang.engine.calendar.LocalEclipse {
        val g = app.parivaar.panchang.engine.calendar.GlobalEclipse(
            false, start, 1.0, app.parivaar.panchang.engine.calendar.EclipseKind.LUNAR_TOTAL, 1.2, 2.0, emptyMap(),
        )
        return app.parivaar.panchang.engine.calendar.LocalEclipse(
            g, app.parivaar.panchang.engine.calendar.EclipseKind.LUNAR_TOTAL, 1.2, start, emptyMap(), TimeRange(start, end), null,
        )
    }
}
