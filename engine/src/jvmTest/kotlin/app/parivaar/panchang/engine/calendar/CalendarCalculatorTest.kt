package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.panchang.DayFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** docs/specs/M04.md section 4, `CalendarCalculatorTest` (Precision backend). */
class CalendarCalculatorTest {
    private val calc = CalendarCalculator(GoldenLoader.openDe440())
    private val delhi = Place(28.6139, 77.2090)

    @Test
    fun delhiJune2024Anchors() {
        val d = calc.day(LocalDate(2024, 6, 1), 19_800, delhi)
        assertEquals(Samvat(1946, 2081, 2080, 38, 38, 1431, 1199), d.samvat)
        assertEquals(Masa.VAISHAKHA, d.amanta.masa)
        assertEquals(Ritu.VASANTA, d.ritu)
        assertEquals(Ayana.UTTARAYANA, d.ayana)
        assertEquals(SolarRegion.entries.toSet(), d.solar.keys)
        assertEquals(Rashi.VRISHABHA, d.solar.getValue(SolarRegion.TAMIL).month)
        assertTrue(d.flags.isEmpty())
        val winter = calc.day(LocalDate(2024, 11, 1), 19_800, delhi)
        assertEquals(Ayana.DAKSHINAYANA, winter.ayana)
    }

    @Test
    fun offsetsWithinTheSameNightGiveTheSameDays() {
        val dates = listOf(LocalDate(2024, 6, 1), LocalDate(2026, 1, 15), LocalDate(2026, 10, 20))
        val cases = listOf(
            delhi to listOf(16_200, 19_800, 23_400),
            Place(51.5074, -0.1278) to listOf(0, 3_600),
            Place(40.7128, -74.0060) to listOf(-18_000, -14_400),
            Place(-36.8485, 174.7633) to listOf(43_200, 46_800),
        )
        for ((place, offsets) in cases) for (date in dates) {
            val days = offsets.map { calc.day(date, it, place) }
            assertTrue(days.all { it == days[0] }, "offset changed a calendar day")
        }
    }

    @Test
    fun daysAgreeWithDay() {
        val first = LocalDate(2026, 5, 10)
        val run = calc.days(first, 40, delhi) { 19_800 }
        assertEquals(40, run.size)
        for ((i, d) in run.withIndex()) {
            assertEquals(first.plus(DatePeriod(days = i)), d.date)
            if (i % 7 == 0) assertEquals(calc.day(d.date, 19_800, delhi), d)
        }
        assertTrue(run.any { it.amanta.adhika } && run.any { !it.amanta.adhika })
        assertTrue(calc.days(first, 0, delhi) { 19_800 }.isEmpty())
        val e = assertFailsWith<IllegalArgumentException> { calc.day(first, 50_401, delhi) }
        assertFalse(e.message.orEmpty().any { it.isDigit() })
    }

    @Test
    fun tromsoPolarNightKeepsTheFallbackFlag() {
        val d = calc.day(LocalDate(2026, 12, 21), 3_600, Place(69.6492, 18.9553))
        assertTrue(DayFlag.SUN_FALLBACK in d.flags)
    }
}
