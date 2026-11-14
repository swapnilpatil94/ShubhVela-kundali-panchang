package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.BackendId
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.SelectingEphemeris
import app.parivaar.panchang.engine.astro.TimeScales
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** docs/specs/M02.md section 4, `ChartCalculatorTest` (Precision backend through `GoldenLoader`). */
class ChartCalculatorTest {
    private val de440 = GoldenLoader.openDe440()
    private val calculator = ChartCalculator(de440)

    // Synthetic instants and public city coordinates; nobody's birth data.
    private val instants = listOf(
        "1900-03-01T00:00:00Z",
        "1987-06-15T03:20:00Z",
        "2000-01-01T12:00:00Z",
        "2077-11-30T18:45:10Z",
    ).map { Instant.parse(it) }
    private val delhi = Place(28.6139, 77.209)
    private val tromso = Place(69.6492, 18.9553)

    private fun body(g: Graha, node: NodeType): Body = when (g) {
        Graha.SUN -> Body.SUN
        Graha.MOON -> Body.MOON
        Graha.MARS -> Body.MARS
        Graha.MERCURY -> Body.MERCURY
        Graha.JUPITER -> Body.JUPITER
        Graha.VENUS -> Body.VENUS
        Graha.SATURN -> Body.SATURN
        Graha.RAHU -> if (node == NodeType.TRUE) Body.RAHU_TRUE else Body.RAHU_MEAN
        Graha.KETU -> if (node == NodeType.TRUE) Body.KETU_TRUE else Body.KETU_MEAN
    }

    @Test
    fun everyGrahaIsTheSiderealApparentPlace() {
        for (at in instants) {
            for (id in listOf(AyanamsaId.LAHIRI, AyanamsaId.TRUE_CHITRA)) {
                for (node in NodeType.entries) {
                    val settings = CalcSettings(ayanamsa = id, node = node)
                    val facts = calculator.compute(at, delhi, settings)
                    val tt = TimeScales.utcToTt(at)
                    assertEquals(tt, facts.tt)
                    assertEquals(id, facts.ayanamsa)
                    assertEquals(node, facts.node)
                    assertEquals(Ayanamsa.trueDeg(id, tt), facts.ayanamsaDeg, 0.0)
                    val rate = Ayanamsa.trueRateDegPerDay(id, tt)
                    for (g in Graha.entries) {
                        val pos = de440.geoApparent(body(g, node), tt)
                        val f = facts.of(g)
                        assertEquals(Ayanamsa.siderealDeg(pos.lonDeg, id, tt), f.lon, 0.0) // bit-equal
                        assertEquals(pos.latDeg, f.lat, 0.0)
                        assertEquals(pos.speedDegPerDay - rate, f.speedDegPerDay, 0.0)
                        assertEquals(f.speedDegPerDay < 0.0, f.retrograde)
                        assertEquals(Zodiac.rashiOf(f.lon), f.rashi)
                        assertEquals(Zodiac.degInRashi(f.lon), f.degInRashi, 0.0)
                        assertEquals(Zodiac.nakshatraOf(f.lon), f.nakshatra)
                        assertEquals(Zodiac.padaOf(f.lon), f.pada)
                        assertEquals(Zodiac.houseFrom(facts.lagna, f.rashi), f.house)
                    }
                    assertTrue(facts.of(Graha.RAHU).retrograde || node == NodeType.TRUE)
                    assertFalse(facts.of(Graha.SUN).retrograde)
                    assertFalse(facts.of(Graha.MOON).retrograde)
                }
            }
        }
    }

    @Test
    fun ketuIsOppositeRahu() {
        for (at in instants) {
            for (node in NodeType.entries) {
                val facts = calculator.compute(at, delhi, CalcSettings(node = node))
                val rahu = facts.of(Graha.RAHU)
                val ketu = facts.of(Graha.KETU)
                assertTrue(abs(Zodiac.wrap180(ketu.lon - rahu.lon - 180.0)) < 1e-9)
                assertEquals(7, Zodiac.houseFrom(rahu.rashi, ketu.rashi))
                assertEquals(rahu.speedDegPerDay, ketu.speedDegPerDay, 1e-9)
            }
        }
    }

    @Test
    fun nodeSettingSwitchesBetweenTrueAndMeanNode() {
        for (at in instants) {
            val tt = TimeScales.utcToTt(at)
            val trueNode = calculator.compute(at, delhi, CalcSettings(node = NodeType.TRUE))
            val meanNode = calculator.compute(at, delhi, CalcSettings(node = NodeType.MEAN))
            assertEquals(calculator.compute(at, delhi).of(Graha.RAHU), trueNode.of(Graha.RAHU)) // TRUE is the default
            val a = trueNode.ayanamsaDeg
            val trueLon = de440.geoApparent(Body.RAHU_TRUE, tt).lonDeg
            val meanLon = de440.geoApparent(Body.RAHU_MEAN, tt).lonDeg
            assertEquals(Zodiac.wrap360(trueLon - a), trueNode.of(Graha.RAHU).lon, 0.0)
            assertEquals(Zodiac.wrap360(meanLon - a), meanNode.of(Graha.RAHU).lon, 0.0)
            val gap = abs(Zodiac.wrap180(trueNode.of(Graha.RAHU).lon - meanNode.of(Graha.RAHU).lon))
            assertTrue(gap > 1e-6 && gap < 2.0, "true and mean node should differ by a degree or so at most")
            for (g in Graha.entries.filter { it != Graha.RAHU && it != Graha.KETU }) {
                assertEquals(trueNode.of(g), meanNode.of(g))
            }
            assertTrue(meanNode.of(Graha.RAHU).retrograde) // the mean node always regresses
        }
    }

    @Test
    fun housesAreTheSiderealHouses() {
        for (at in instants) {
            for (system in HouseSystem.entries) {
                val settings = CalcSettings(houseSystem = system)
                val houses = assertIs<HouseResult.Cusps>(Houses.sidereal(at, delhi, settings))
                val facts = calculator.compute(at, delhi, settings)
                assertEquals(houses.ascDeg, facts.ascDeg, 0.0)
                assertEquals(houses.mcDeg, facts.mcDeg, 0.0)
                assertEquals(houses.cuspsDeg, facts.cuspsDeg)
                assertEquals(houses.startsDeg, facts.startsDeg)
                assertEquals(system, facts.houseSystem)
                assertFalse(facts.houseFallback)
                assertEquals(Zodiac.rashiOf(houses.ascDeg), facts.lagna)
                for (f in facts.grahas) {
                    val expected = if (system == HouseSystem.WHOLE_SIGN) {
                        Zodiac.houseFrom(facts.lagna, f.rashi)
                    } else {
                        houses.houseOf(f.lon)
                    }
                    assertEquals(expected, f.house)
                }
            }
        }
    }

    @Test
    fun placidusAtTromsoFallsBackToWholeSign() {
        for (at in instants) {
            val facts = calculator.compute(at, tromso, CalcSettings(houseSystem = HouseSystem.PLACIDUS))
            assertTrue(facts.houseFallback)
            assertEquals(HouseSystem.WHOLE_SIGN, facts.houseSystem)
            val whole = assertIs<HouseResult.Cusps>(Houses.sidereal(at, tromso, CalcSettings()))
            assertEquals(whole.ascDeg, facts.ascDeg, 0.0)
            assertEquals(whole.startsDeg, facts.startsDeg)
            assertEquals(whole.cuspsDeg, facts.cuspsDeg)
            for (f in facts.grahas) assertEquals(Zodiac.houseFrom(facts.lagna, f.rashi), f.house)
            // Everything but the houses is what a whole-sign request gives.
            assertEquals(calculator.compute(at, tromso, CalcSettings()).grahas, facts.grahas)
        }
    }

    @Test
    fun backendIsReportedOnlyByTheSelectingEphemeris() {
        val at = instants[2]
        assertNull(calculator.compute(at, delhi).backend)
        val base = AstronomyEngineEphemeris()
        val both = ChartCalculator(SelectingEphemeris(GoldenLoader.openDe440(), base))
        assertEquals(BackendId.PRECISION_DE440, both.compute(at, delhi).backend)
        assertEquals(calculator.compute(at, delhi).grahas, both.compute(at, delhi).grahas)
        val baseOnly = ChartCalculator(SelectingEphemeris(null, base)).compute(at, delhi)
        assertEquals(BackendId.BASE_ASTRONOMY_ENGINE, baseOnly.backend)
        val precise = calculator.compute(at, delhi)
        for (g in Graha.entries) {
            assertTrue(abs(Zodiac.wrap180(baseOnly.of(g).lon - precise.of(g).lon)) < 60.0 / 3600.0)
        }
    }

    @Test
    fun sunAndMoonAtJ2000AreWhereTheTextbooksPutThem() {
        // Tropical Sun 280.4 deg and Moon 223.3 deg at J2000.0, Lahiri about 23.85 deg.
        val facts = calculator.compute(instants[2], delhi)
        assertEquals(256.5, facts.of(Graha.SUN).lon, 0.1)
        assertEquals(Rashi.DHANU, facts.of(Graha.SUN).rashi)
        assertEquals(19, facts.of(Graha.SUN).nakshatra)
        assertEquals(Rashi.TULA, facts.of(Graha.MOON).rashi)
        assertEquals(23.85, facts.ayanamsaDeg, 0.01)
    }

    @Test
    fun disabledAyanamsaIsRejected() {
        val e = assertFailsWith<AyanamsaUnavailableException> {
            calculator.compute(instants[0], delhi, CalcSettings(ayanamsa = AyanamsaId.KP_NEW))
        }
        assertEquals(AyanamsaId.KP_NEW, e.id)
    }

    @Test
    fun textFormCarriesNoDigit() {
        val facts = calculator.compute(instants[1], delhi)
        assertEquals("ChartFacts(redacted)", facts.toString())
        assertFalse(facts.toString().any { it.isDigit() })
        assertFalse(facts.grahas.toString().any { it.isDigit() })
    }
}
