package app.parivaar.panchang.engine

import app.parivaar.panchang.engine.astro.BackendId
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.ChartFacts
import app.parivaar.panchang.engine.jyotish.DashaYear
import app.parivaar.panchang.engine.jyotish.DrekkanaVariant
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.HoraVariant
import app.parivaar.panchang.engine.jyotish.HouseResult
import app.parivaar.panchang.engine.jyotish.HouseSystem
import app.parivaar.panchang.engine.jyotish.NodeType
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.Zodiac
import kotlin.math.nextDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/specs/M02.md section 4, `ZodiacTest`, plus the C1 contracts (`CalcSettings` defaults, `ChartFacts`). */
class ZodiacTest {
    @Test
    fun zeroIsMeshaAshwiniFirstPada() {
        assertEquals(Rashi.MESHA, Zodiac.rashiOf(0.0))
        assertEquals(0.0, Zodiac.degInRashi(0.0))
        assertEquals(0, Zodiac.nakshatraOf(0.0))
        assertEquals(1, Zodiac.padaOf(0.0))
    }

    @Test
    fun thirteenAndAHalfIsBharaniFirstPada() {
        assertEquals(Rashi.MESHA, Zodiac.rashiOf(13.5))
        assertEquals(13.5, Zodiac.degInRashi(13.5))
        assertEquals(1, Zodiac.nakshatraOf(13.5))
        assertEquals(1, Zodiac.padaOf(13.5))
    }

    @Test
    fun endOfTheCircleIsMeenaRevatiFourthPada() {
        assertEquals(Rashi.MEENA, Zodiac.rashiOf(359.999999))
        assertEquals(26, Zodiac.nakshatraOf(359.999999))
        assertEquals(4, Zodiac.padaOf(359.999999))
        assertEquals(29.999999, Zodiac.degInRashi(359.999999), 1e-12)
    }

    @Test
    fun justBelowFullCircleClamps() {
        val lon = 360.0 - 1e-13
        assertTrue(lon < 360.0)
        assertEquals(Rashi.MEENA, Zodiac.rashiOf(lon)) // s = 11
        assertEquals(26, Zodiac.nakshatraOf(lon)) // q = 107
        assertEquals(4, Zodiac.padaOf(lon))
        assertTrue(Zodiac.degInRashi(lon) < 30.0)
        val last = 360.0.nextDown()
        assertEquals(Rashi.MEENA, Zodiac.rashiOf(last))
        assertEquals(26, Zodiac.nakshatraOf(last))
        assertEquals(4, Zodiac.padaOf(last))
        assertTrue(Zodiac.degInRashi(last) < 30.0)
    }

    @Test
    fun everySignStartsOnItsMultipleOfThirty() {
        for (s in 0 until 12) {
            val lon = 30.0 * s
            assertEquals(Rashi.entries[s], Zodiac.rashiOf(lon))
            assertEquals(0.0, Zodiac.degInRashi(lon))
            assertEquals(Rashi.entries[(s + 11) % 12], Zodiac.rashiOf(if (s == 0) 360.0.nextDown() else lon.nextDown()))
            // 30 s degrees is quarter 9 s exactly (9 padas per sign).
            assertEquals(9 * s / 4, Zodiac.nakshatraOf(lon))
            assertEquals(9 * s % 4 + 1, Zodiac.padaOf(lon))
            assertEquals(15.0, Zodiac.degInRashi(lon + 15.0))
        }
    }

    @Test
    fun everyQuarterMidpointHasItsNakshatraAndPada() {
        for (q in 0 until 108) {
            val lon = (q + 0.5) * 360.0 / 108.0
            assertEquals(q / 4, Zodiac.nakshatraOf(lon))
            assertEquals(q % 4 + 1, Zodiac.padaOf(lon))
        }
    }

    @Test
    fun signLords() {
        val expected = listOf(
            Graha.MARS, Graha.VENUS, Graha.MERCURY, Graha.MOON, Graha.SUN, Graha.MERCURY,
            Graha.VENUS, Graha.MARS, Graha.JUPITER, Graha.SATURN, Graha.SATURN, Graha.JUPITER,
        )
        assertEquals(expected, Rashi.entries.map { Zodiac.lordOf(it) })
    }

    @Test
    fun exaltationSigns() {
        assertEquals(Rashi.MESHA, Zodiac.exaltationOf(Graha.SUN))
        assertEquals(Rashi.VRISHABHA, Zodiac.exaltationOf(Graha.MOON))
        assertEquals(Rashi.MAKARA, Zodiac.exaltationOf(Graha.MARS))
        assertEquals(Rashi.KANYA, Zodiac.exaltationOf(Graha.MERCURY))
        assertEquals(Rashi.KARKA, Zodiac.exaltationOf(Graha.JUPITER))
        assertEquals(Rashi.MEENA, Zodiac.exaltationOf(Graha.VENUS))
        assertEquals(Rashi.TULA, Zodiac.exaltationOf(Graha.SATURN))
        assertNull(Zodiac.exaltationOf(Graha.RAHU))
        assertNull(Zodiac.exaltationOf(Graha.KETU))
    }

    @Test
    fun nakshatraLordsRepeatEveryNine() {
        val cycle = listOf(
            Graha.KETU, Graha.VENUS, Graha.SUN, Graha.MOON, Graha.MARS,
            Graha.RAHU, Graha.JUPITER, Graha.SATURN, Graha.MERCURY,
        )
        for (n in 0 until 27) assertEquals(cycle[n % 9], Zodiac.nakshatraLord(n))
        assertFailsWith<IllegalArgumentException> { Zodiac.nakshatraLord(-1) }
        assertFailsWith<IllegalArgumentException> { Zodiac.nakshatraLord(27) }
    }

    @Test
    fun houseFromCountsInclusively() {
        assertEquals(12, Zodiac.houseFrom(Rashi.MESHA, Rashi.MEENA))
        assertEquals(2, Zodiac.houseFrom(Rashi.MEENA, Rashi.MESHA))
        assertEquals(7, Zodiac.houseFrom(Rashi.KARKA, Rashi.MAKARA))
        for (a in Rashi.entries) {
            assertEquals(1, Zodiac.houseFrom(a, a))
            for (b in Rashi.entries) {
                val h = Zodiac.houseFrom(a, b)
                assertTrue(h in 1..12)
                if (a != b) assertEquals(14, h + Zodiac.houseFrom(b, a))
            }
        }
    }

    @Test
    fun aspects() {
        assertEquals(setOf(7), Zodiac.aspectsOf(Graha.SUN))
        assertEquals(setOf(7), Zodiac.aspectsOf(Graha.MOON))
        assertEquals(setOf(7), Zodiac.aspectsOf(Graha.MERCURY))
        assertEquals(setOf(7), Zodiac.aspectsOf(Graha.VENUS))
        assertEquals(setOf(4, 7, 8), Zodiac.aspectsOf(Graha.MARS))
        assertEquals(setOf(5, 7, 9), Zodiac.aspectsOf(Graha.JUPITER))
        assertEquals(setOf(3, 7, 10), Zodiac.aspectsOf(Graha.SATURN))
        assertEquals(emptySet(), Zodiac.aspectsOf(Graha.RAHU))
        assertEquals(emptySet(), Zodiac.aspectsOf(Graha.KETU))
    }

    @Test
    fun wrap180IsHalfOpenAtMinus180() {
        assertEquals(180.0, Zodiac.wrap180(180.0))
        assertEquals(180.0, Zodiac.wrap180(-180.0))
        assertEquals(-179.0, Zodiac.wrap180(181.0))
        assertEquals(179.0, Zodiac.wrap180(-181.0))
        assertEquals(0.0, Zodiac.wrap180(360.0))
        assertEquals(180.0, Zodiac.wrap180(540.0))
        assertEquals(-1e-9, Zodiac.wrap180(-1e-9)) // small differences keep every bit
        assertEquals(1e-9, Zodiac.wrap180(1e-9))
        assertEquals(-10.0, Zodiac.wrap180(350.0))
    }

    @Test
    fun wrap360IsHalfOpenAt360() {
        assertEquals(0.0, Zodiac.wrap360(0.0))
        assertEquals(0.0, Zodiac.wrap360(360.0))
        assertEquals(0.0, Zodiac.wrap360(-360.0))
        assertEquals(0.5, Zodiac.wrap360(720.5))
        assertEquals(350.0, Zodiac.wrap360(-10.0))
        assertEquals(359.5, Zodiac.wrap360(359.5))
        // -1e-20 + 360 rounds to 360.0, which is outside [0, 360): it must come back as 0.
        assertEquals(0.0, Zodiac.wrap360(-1e-20))
        assertTrue(Zodiac.wrap360(-1e-9) < 360.0)
        assertEquals(Double.POSITIVE_INFINITY, 1.0 / Zodiac.wrap360(-0.0)) // never -0.0
        assertEquals(Double.POSITIVE_INFINITY, 1.0 / Zodiac.wrap180(-0.0))
    }

    @Test
    fun longitudesOutsideTheCircleAreTakenModulo360AndNonFiniteOnesAreRejected() {
        assertEquals(Rashi.MEENA, Zodiac.rashiOf(-1e-9))
        assertEquals(26, Zodiac.nakshatraOf(-1e-9))
        assertEquals(4, Zodiac.padaOf(-1e-9))
        assertEquals(Rashi.MESHA, Zodiac.rashiOf(360.0))
        assertEquals(0.0, Zodiac.degInRashi(360.0))
        assertEquals(Rashi.VRISHABHA, Zodiac.rashiOf(390.0))
        assertEquals(1, Zodiac.nakshatraOf(373.5))
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Zodiac.rashiOf(bad) }
            assertFailsWith<IllegalArgumentException> { Zodiac.degInRashi(bad) }
            assertFailsWith<IllegalArgumentException> { Zodiac.nakshatraOf(bad) }
            assertFailsWith<IllegalArgumentException> { Zodiac.padaOf(bad) }
        }
    }

    @Test
    fun calcSettingsDefaultsAreTheJyotishDefaults() {
        val s = CalcSettings()
        assertEquals(AyanamsaId.LAHIRI, s.ayanamsa)
        assertEquals(NodeType.TRUE, s.node)
        assertEquals(HouseSystem.WHOLE_SIGN, s.houseSystem)
        assertEquals(DashaYear.JULIAN_365_25, s.dashaYear)
        assertEquals(HoraVariant.PARASHARA, s.hora)
        assertEquals(DrekkanaVariant.PARASHARA, s.drekkana)
        // The M01 fields keep their positions and defaults.
        assertEquals(SunriseConvention.UPPER_LIMB_REFRACTED, s.sunrise)
        assertEquals(HighLatitudeRule.LOCAL_NOON_PM_6H, s.highLatitudeRule)
        assertEquals(1010.0, s.pressureHPa)
        assertEquals(10.0, s.temperatureC)
        assertEquals(listOf(365.25, 365.2425, 360.0), DashaYear.entries.map { it.days })
    }

    private val tt = JulianDay(2451545.0)

    // Graha order: SUN, MOON, MARS, MERCURY, JUPITER, VENUS, SATURN, RAHU, KETU.
    private val lons = doubleArrayOf(5.0, 45.0, 215.0, 359.5, 100.0, 13.5, 280.0, 40.0, 220.0)
    private val lats = doubleArrayOf(0.0, 4.5, -1.0, 2.0, 0.5, -3.0, 1.5, 0.0, 0.0)
    private val speeds = doubleArrayOf(1.0, 13.2, 0.5, -0.8, 0.08, 1.2, -0.03, -0.05, -0.05)

    private fun wholeSign(ascDeg: Double): HouseResult.Cusps {
        val first = Zodiac.rashiOf(ascDeg).ordinal
        val starts = List(12) { 30.0 * ((first + it) % 12) }
        return HouseResult.Cusps(HouseSystem.WHOLE_SIGN, ascDeg, Zodiac.wrap360(ascDeg + 270.0), starts, starts)
    }

    private fun equal(ascDeg: Double): HouseResult.Cusps {
        val cusps = List(12) { Zodiac.wrap360(ascDeg + 30.0 * it) }
        val starts = cusps.map { Zodiac.wrap360(it - 15.0) }
        return HouseResult.Cusps(HouseSystem.EQUAL, ascDeg, Zodiac.wrap360(ascDeg + 270.0), cusps, starts)
    }

    @Test
    fun fromLongitudesFillsTheDerivedFields() {
        val settings = CalcSettings(node = NodeType.MEAN)
        val houses = wholeSign(65.0) // MITHUNA lagna
        val facts = ChartFacts.fromLongitudes(
            tt, settings, 23.85, BackendId.PRECISION_DE440, houses, false, lons, lats, speeds,
        )
        assertEquals(tt, facts.tt)
        assertEquals(AyanamsaId.LAHIRI, facts.ayanamsa)
        assertEquals(23.85, facts.ayanamsaDeg)
        assertEquals(NodeType.MEAN, facts.node)
        assertEquals(BackendId.PRECISION_DE440, facts.backend)
        assertEquals(65.0, facts.ascDeg)
        assertEquals(houses.mcDeg, facts.mcDeg)
        assertEquals(HouseSystem.WHOLE_SIGN, facts.houseSystem)
        assertFalse(facts.houseFallback)
        assertEquals(houses.cuspsDeg, facts.cuspsDeg)
        assertEquals(houses.startsDeg, facts.startsDeg)
        assertEquals(Rashi.MITHUNA, facts.lagna)
        assertEquals(Graha.entries.toList(), facts.grahas.map { it.graha })
        for (g in Graha.entries) {
            val f = facts.of(g)
            val i = g.ordinal
            assertEquals(g, f.graha)
            assertEquals(lons[i], f.lon)
            assertEquals(lats[i], f.lat)
            assertEquals(speeds[i], f.speedDegPerDay)
            assertEquals(speeds[i] < 0.0, f.retrograde)
            assertEquals(Zodiac.rashiOf(lons[i]), f.rashi)
            assertEquals(Zodiac.degInRashi(lons[i]), f.degInRashi)
            assertEquals(Zodiac.nakshatraOf(lons[i]), f.nakshatra)
            assertEquals(Zodiac.padaOf(lons[i]), f.pada)
            assertEquals(Zodiac.houseFrom(Rashi.MITHUNA, f.rashi), f.house)
        }
        assertEquals(11, facts.of(Graha.SUN).house) // MESHA from MITHUNA
        assertEquals(6, facts.of(Graha.MARS).house) // VRISHCHIKA from MITHUNA
        assertTrue(facts.of(Graha.MERCURY).retrograde)
        assertFalse(facts.of(Graha.SUN).retrograde)
    }

    @Test
    fun fromLongitudesUsesTheHouseStartsForOtherSystemsAndKeepsTheFallbackFlag() {
        val houses = equal(65.0) // house 1 = [50, 80), house 2 = [80, 110), ..., house 12 = [20, 50)
        val settings = CalcSettings(houseSystem = HouseSystem.PLACIDUS)
        val facts = ChartFacts.fromLongitudes(tt, settings, 23.85, null, houses, true, lons, lats, speeds)
        assertEquals(HouseSystem.EQUAL, facts.houseSystem) // the system actually used
        assertTrue(facts.houseFallback)
        assertNull(facts.backend)
        assertEquals(11, facts.of(Graha.SUN).house) // 5 in [350, 20)
        assertEquals(12, facts.of(Graha.MOON).house) // 45 in [20, 50)
        assertEquals(12, facts.of(Graha.RAHU).house) // 40
        assertEquals(2, facts.of(Graha.JUPITER).house) // 100 in [80, 110)
        assertEquals(6, facts.of(Graha.MARS).house) // 215 in [200, 230)
        for (f in facts.grahas) assertEquals(houses.houseOf(f.lon), f.house)
    }

    @Test
    fun houseOfPutsEveryCuspInItsOwnHouseAndCoversTheCircle() {
        for (asc in listOf(0.0, 14.999, 65.0, 344.9, 359.999)) {
            for (houses in listOf(wholeSign(asc), equal(asc))) {
                for (i in 0 until 12) {
                    assertEquals(i + 1, houses.houseOf(houses.cuspsDeg[i]))
                    assertEquals(i + 1, houses.houseOf(houses.startsDeg[i]))
                }
                var lon = 0.0
                while (lon < 360.0) {
                    assertTrue(houses.houseOf(lon) in 1..12)
                    lon += 0.37
                }
            }
        }
        assertEquals(1, wholeSign(65.0).houseOf(60.0))
        assertEquals(12, wholeSign(65.0).houseOf(59.999))
        assertEquals(1, equal(65.0).houseOf(65.0 + 360.0)) // modulo 360
    }

    @Test
    fun wrongSizesAreRejected() {
        val houses = wholeSign(65.0)
        assertFailsWith<IllegalArgumentException> {
            ChartFacts.fromLongitudes(tt, CalcSettings(), 23.85, null, houses, false, DoubleArray(8), lats, speeds)
        }
        assertFailsWith<IllegalArgumentException> {
            ChartFacts.fromLongitudes(tt, CalcSettings(), 23.85, null, houses, false, lons, DoubleArray(10), speeds)
        }
        assertFailsWith<IllegalArgumentException> {
            ChartFacts.fromLongitudes(tt, CalcSettings(), 23.85, null, houses, false, lons, lats, DoubleArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            HouseResult.Cusps(HouseSystem.EQUAL, 0.0, 270.0, List(11) { 30.0 * it }, List(12) { 30.0 * it })
        }
        val facts = ChartFacts.fromLongitudes(tt, CalcSettings(), 23.85, null, houses, false, lons, lats, speeds)
        assertFailsWith<IllegalArgumentException> { facts.copy(grahas = facts.grahas.reversed()) }
        assertFailsWith<IllegalArgumentException> { facts.copy(grahas = facts.grahas.take(8)) }
    }

    @Test
    fun textFormsCarryNoChartData() {
        val houses = equal(65.0)
        val facts = ChartFacts.fromLongitudes(tt, CalcSettings(), 23.85, null, houses, false, lons, lats, speeds)
        assertEquals("ChartFacts(redacted)", facts.toString())
        val moon = facts.of(Graha.MOON)
        val texts = listOf(facts.toString(), moon.toString(), facts.grahas.toString(), houses.toString())
        for (text in texts) assertFalse(text.any { it.isDigit() })
        val e = assertFailsWith<IllegalArgumentException> {
            ChartFacts.fromLongitudes(
                tt, CalcSettings(), 23.85, null, houses, false,
                doubleArrayOf(5.0, Double.NaN, 215.0, 359.5, 100.0, 13.5, 280.0, 40.0, 220.0), lats, speeds,
            )
        }
        assertFalse(e.message.orEmpty().any { it.isDigit() })
    }
}
