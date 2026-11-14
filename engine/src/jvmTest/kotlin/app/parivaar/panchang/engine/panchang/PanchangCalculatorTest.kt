package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.HighLatitudeRule
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.EclipticPos
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.JulianDay
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** docs/specs/M03.md section 4, `PanchangCalculatorTest` (Precision backend; city coordinates only). */
class PanchangCalculatorTest {
    private fun rule(name: String): String =
        File(checkNotNull(System.getProperty("pp.rulesDir")) { "pp.rulesDir is not set" }, name).readText()

    private val tables = PanchangRules.parse(rule("durmuhurta.json"), rule("choghadiya.json"))
    private val eph = GoldenLoader.openDe440()
    private val calc = PanchangCalculator(eph, tables)
    private val delhi = Place(28.6139, 77.209)
    private val ist = 330 * 60
    private val jan1 = LocalDate(2026, 1, 1)

    private val year: List<PanchangDay> by lazy { calc.days(jan1, 365, delhi) { ist } }

    private val printedKinds = setOf(WindowKind.RAHU_KAAL, WindowKind.YAMAGANDA, WindowKind.GULIKA)

    @Test
    fun `a year of days tiles and every span carries the 3_2 flags`() {
        assertEquals(365, year.size)
        for ((a, b) in year.zipWithNext()) {
            assertEquals(a.nextSunrise, b.sunrise, "days tile")
            for (k in LimbKind.entries) {
                val last = a.limbs.getValue(k).last()
                if (last.end != a.nextSunrise) {
                    assertEquals(last.copy(kshaya = false, adhika = false), b.atSunrise(k).copy(adhika = false))
                }
            }
        }
        for (d in year) {
            assertTrue(d.sunrise < d.sunset && d.sunset < d.nextSunrise)
            assertEquals(Vaar.of(d.date), d.vaar)
            assertEquals(WeekdayTables.shool(d.vaar), d.shool)
            assertTrue(d.flags.isEmpty())
            for (k in LimbKind.entries) {
                val spans = d.limbs.getValue(k)
                assertTrue(spans.first().start <= d.sunrise && spans.last().end >= d.nextSunrise)
                for ((x, y) in spans.zipWithNext()) assertEquals(x.end, y.start)
                for (sp in spans) {
                    val hasSunrise = d.sunrise in sp
                    val hasNext = d.nextSunrise in sp
                    assertEquals(!hasSunrise && !hasNext, sp.kshaya, "$k kshaya")
                    assertEquals(hasSunrise && hasNext, sp.adhika, "$k adhika")
                }
                assertTrue(d.sunrise in d.atSunrise(k))
            }
        }
        val kinds = year.flatMap { d -> d.limbs.values.flatten().filter { it.kshaya }.map { it.kind } }.toSet()
        assertTrue(LimbKind.TITHI in kinds && LimbKind.KARANA in kinds, "kshaya tithi and karana occur in a year")
    }

    @Test
    fun `day() equals the days() element on 30 dates with a fresh calculator`() {
        for (i in 0 until 30) {
            val date = jan1.plus(DatePeriod(days = i * 12 + 1))
            assertEquals(year[i * 12 + 1], PanchangCalculator(GoldenLoader.openDe440(), tables).day(date, ist, delhi))
        }
    }

    private fun sameExceptOffset(a: PanchangDay, b: PanchangDay, printed: Boolean) {
        val strip = { d: PanchangDay ->
            d.copy(utcOffsetSeconds = 0, windows = d.windows.filter { !printed || it.kind !in printedKinds })
        }
        assertEquals(strip(a), strip(b))
    }

    private fun checkOffsets(place: Place, date: LocalDate, offsetsMin: List<Int>) {
        val printed = CalcSettings(timingStyle = TimingStyle.PRINTED_6AM)
        val base = calc.day(date, offsetsMin[0] * 60, place)
        val basePrinted = calc.day(date, offsetsMin[0] * 60, place, printed)
        for (m in offsetsMin.drop(1)) {
            sameExceptOffset(base, PanchangCalculator(eph, tables).day(date, m * 60, place), printed = false)
            val other = calc.day(date, m * 60, place, printed)
            sameExceptOffset(basePrinted, other, printed = true)
            val shift = ((offsetsMin[0] - m) * 60).toLong()
            val rahuA = basePrinted.windows.single { it.kind == WindowKind.RAHU_KAAL }
            val rahuB = other.windows.single { it.kind == WindowKind.RAHU_KAAL }
            assertEquals(shift, (rahuB.start - rahuA.start).inWholeSeconds)
        }
    }

    @Test
    fun `results do not depend on the time zone except the offset and PRINTED windows`() {
        for (i in listOf(0, 77, 150, 222, 300)) {
            checkOffsets(delhi, jan1.plus(DatePeriod(days = i)), listOf(330, 270, 390))
        }
        checkOffsets(Place(51.5074, -0.1278), LocalDate(2026, 3, 29), listOf(0, 60))
        val newYork = Place(40.7128, -74.006)
        checkOffsets(newYork, LocalDate(2026, 3, 8), listOf(-300, -240))
        checkOffsets(newYork, LocalDate(2026, 11, 1), listOf(-300, -240))
    }

    @Test
    fun `Tromso polar night uses the noon fallback`() {
        val d = calc.day(LocalDate(2026, 12, 21), 3600, Place(69.6492, 18.9553))
        assertTrue(DayFlag.SUN_FALLBACK in d.flags)
        assertEquals(12.hours, d.sunset - d.sunrise)
    }

    @Test
    fun `offset out of range is rejected without the value`() {
        for (bad in listOf(-43_201, 50_401)) {
            val e = assertFailsWith<IllegalArgumentException> { calc.day(jan1, bad, delhi) }
            assertTrue(e.message.orEmpty().none { it.isDigit() })
        }
    }

    @Test
    fun `constant Sun at 80 degrees with NEAREST_DAY has no sun day`() {
        val stub = object : Ephemeris {
            override fun geoApparent(body: Body, tt: JulianDay) = EclipticPos(270.0, 0.0, 1.0, 0.0)
            override val validRange = JulianDay(2415020.5)..JulianDay(2488069.5)
        }
        val nearest = CalcSettings(highLatitudeRule = HighLatitudeRule.NEAREST_DAY)
        val e = assertFailsWith<PanchangUnavailableException> {
            PanchangCalculator(stub, tables).day(jan1, 0, Place(80.0, 0.0), nearest)
        }
        assertEquals(PanchangError.NO_SUN_DAY, e.code)
        assertEquals("NO_SUN_DAY", e.message)
    }
}
