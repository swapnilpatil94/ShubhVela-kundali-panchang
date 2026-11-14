package app.parivaar.panchang.engine

import app.parivaar.panchang.engine.jyotish.DashaPeriod
import app.parivaar.panchang.engine.jyotish.DashaTree
import app.parivaar.panchang.engine.jyotish.DashaYear
import app.parivaar.panchang.engine.jyotish.Dashas
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Yogini
import app.parivaar.panchang.engine.jyotish.Zodiac
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** docs/specs/M02.md section 4, `DashaTest`. */
class DashaTest {
    private val birth = Instant.parse("2000-01-01T00:00:00Z")

    private fun instant(text: String): Instant = Instant.parse(text)

    @Test
    fun moonAtZeroJulianYear() {
        val tree = Dashas.vimshottari(0.0, birth, DashaYear.JULIAN_365_25)
        val ketu = tree.maha[0]
        assertEquals(listOf(Graha.KETU), ketu.lords)
        assertEquals(0.0, ketu.startDay)
        assertEquals(birth, ketu.start)
        assertEquals(instant("2006-12-31T18:00:00Z"), ketu.end)
        val venus = tree.maha[1]
        assertEquals(listOf(Graha.VENUS), venus.lords)
        assertEquals(ketu.end, venus.start)
        assertEquals(instant("2026-12-31T18:00:00Z"), venus.end)
        val ketuKetu = tree.children(ketu)[0]
        assertEquals(listOf(Graha.KETU, Graha.KETU), ketuKetu.lords)
        assertEquals(birth, ketuKetu.start)
        assertEquals(instant("2000-05-29T03:27:00Z"), ketuKetu.end)
    }

    @Test
    fun defaultYearIsTheJulianYear() {
        assertEquals(Dashas.vimshottari(0.0, birth, DashaYear.JULIAN_365_25).maha, Dashas.vimshottari(0.0, birth).maha)
        assertEquals(Dashas.yogini(0.0, birth, DashaYear.JULIAN_365_25).maha, Dashas.yogini(0.0, birth).maha)
    }

    @Test
    fun moonAtZeroSavanaYear() {
        val ketu = Dashas.vimshottari(0.0, birth, DashaYear.SAVANA_360).maha[0]
        assertEquals(instant("2006-11-25T00:00:00Z"), ketu.end)
    }

    @Test
    fun moonAtZeroGregorianYear() {
        val ketu = Dashas.vimshottari(0.0, birth, DashaYear.GREGORIAN_365_2425).maha[0]
        assertEquals(instant("2006-12-31T16:44:24Z"), ketu.end)
    }

    @Test
    fun moonAtTenDegreesHasThreeQuartersElapsed() {
        val ketu = Dashas.vimshottari(10.0, birth, DashaYear.JULIAN_365_25).maha[0]
        assertEquals(listOf(Graha.KETU), ketu.lords)
        assertEquals(instant("1994-10-01T10:30:00Z"), ketu.start)
        assertEquals(instant("2001-10-01T04:30:00Z"), ketu.end)
    }

    @Test
    fun vimshottariMahaIsOneCycleFromTheNakshatraLord() {
        assertEquals(
            listOf(
                Graha.KETU, Graha.VENUS, Graha.SUN, Graha.MOON, Graha.MARS,
                Graha.RAHU, Graha.JUPITER, Graha.SATURN, Graha.MERCURY,
            ),
            Dashas.VIMSHOTTARI_ORDER,
        )
        val years = mapOf(
            Graha.KETU to 7, Graha.VENUS to 20, Graha.SUN to 6, Graha.MOON to 10, Graha.MARS to 7,
            Graha.RAHU to 18, Graha.JUPITER to 16, Graha.SATURN to 19, Graha.MERCURY to 17,
        )
        for (n in 0 until 27) {
            val moon = (n + 0.25) * 360.0 / 27.0
            for (year in DashaYear.entries) {
                val tree = Dashas.vimshottari(moon, birth, year)
                assertEquals(5, tree.maxDepth)
                assertEquals(9, tree.maha.size)
                assertEquals(Zodiac.nakshatraLord(n), tree.maha[0].lords.single())
                val first = Dashas.VIMSHOTTARI_ORDER.indexOf(tree.maha[0].lords.single())
                for (j in 0 until 9) {
                    val p = tree.maha[j]
                    val lord = Dashas.VIMSHOTTARI_ORDER[(first + j) % 9]
                    assertEquals(listOf(lord), p.lords)
                    assertEquals(years.getValue(lord) * year.days, p.endDay - p.startDay, 1e-9)
                    if (j > 0) assertEquals(tree.maha[j - 1].endDay, p.startDay, 0.0)
                }
                val firstYears = years.getValue(tree.maha[0].lords.single())
                assertEquals(-0.25 * firstYears * year.days, tree.maha[0].startDay, 1e-9)
                assertEquals(120 * year.days, tree.maha.last().endDay - tree.maha.first().startDay, 1e-9)
            }
        }
    }

    private fun <L> assertTiles(tree: DashaTree<L>, parent: DashaPeriod<L>, children: List<DashaPeriod<L>>) {
        assertEquals(parent.startDay, children.first().startDay, 0.0)
        assertEquals(parent.endDay, children.last().endDay, 0.0)
        assertEquals(parent.start, children.first().start)
        assertEquals(parent.end, children.last().end)
        for (k in children.indices) {
            val c = children[k]
            assertEquals(parent.lords, c.lords.dropLast(1))
            assertTrue(c.startDay < c.endDay)
            assertEquals(birth + (c.startDay * 86_400_000.0).roundToLong().milliseconds, c.start)
            assertEquals(birth + (c.endDay * 86_400_000.0).roundToLong().milliseconds, c.end)
            if (k > 0) {
                assertEquals(children[k - 1].endDay, c.startDay, 0.0) // bit-equal: no gap, no overlap
                assertEquals(children[k - 1].end, c.start)
            }
        }
        assertEquals(tree.maxDepth >= parent.lords.size + 1, true)
    }

    @Test
    fun fullVimshottariTreeTilesAndAgreesWithAt() {
        val tree = Dashas.vimshottari(123.456, birth, DashaYear.JULIAN_365_25)
        var leaves = 0
        var checkedChains = 0

        fun walk(chain: List<DashaPeriod<Graha>>) {
            val p = chain.last()
            if (p.lords.size == tree.maxDepth) {
                leaves++
                assertFailsWith<IllegalArgumentException> { tree.children(p) }
                if (leaves % 97 == 0) {
                    // The middle of the leaf, on the millisecond grid, well inside it (leaves last hours at least).
                    val mid = birth + ((p.startDay + p.endDay) / 2.0 * 86_400_000.0).roundToLong().milliseconds
                    assertEquals(chain, tree.at(mid))
                    assertEquals(chain.take(2), tree.at(mid, 2))
                    checkedChains++
                }
                return
            }
            val children = tree.children(p)
            assertEquals(9, children.size)
            val first = Dashas.VIMSHOTTARI_ORDER.indexOf(p.lords.last())
            for (k in 0 until 9) assertEquals(Dashas.VIMSHOTTARI_ORDER[(first + k) % 9], children[k].lords.last())
            assertTiles(tree, p, children)
            for (c in children) walk(chain + c)
        }
        for (m in tree.maha) walk(listOf(m))
        assertEquals(59_049, leaves)
        assertEquals(59_049 / 97, checkedChains)
    }

    @Test
    fun antarLengthsAreProportionalToTheLordsYears() {
        val tree = Dashas.vimshottari(200.0, birth, DashaYear.SAVANA_360)
        val years = listOf(7, 20, 6, 10, 7, 18, 16, 19, 17)
        for (maha in tree.maha) {
            val d = maha.endDay - maha.startDay
            for (antar in tree.children(maha)) {
                val y = years[Dashas.VIMSHOTTARI_ORDER.indexOf(antar.lords.last())]
                assertEquals(d * y / 120.0, antar.endDay - antar.startDay, 1e-8)
            }
        }
    }

    @Test
    fun atPicksHalfOpenPeriodsAndRejectsInstantsOutsideTheCycle() {
        val tree = Dashas.vimshottari(10.0, birth, DashaYear.JULIAN_365_25)
        assertEquals(listOf(tree.maha[0]), tree.at(birth, 1))
        assertEquals(5, tree.at(birth).size)
        assertEquals(listOf(tree.maha[0]), tree.at(tree.maha[0].start, 1)) // start is inside
        assertEquals(listOf(tree.maha[1]), tree.at(tree.maha[0].end, 1)) // end belongs to the next period
        assertEquals(listOf(tree.maha[0]), tree.at(tree.maha[0].end - 1.milliseconds, 1))
        assertEquals(listOf(tree.maha[8]), tree.at(tree.maha[8].end - 1.milliseconds, 1))
        val e = assertFailsWith<IllegalArgumentException> { tree.at(tree.maha[8].end) }
        assertFalse(e.message.orEmpty().any { it.isDigit() })
        assertFailsWith<IllegalArgumentException> { tree.at(tree.maha[0].start - 1.milliseconds) }
        assertFailsWith<IllegalArgumentException> { tree.at(birth, 0) }
        assertFailsWith<IllegalArgumentException> { tree.at(birth, 6) }
    }

    @Test
    fun yoginiStartsFromTheNakshatra() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), Yogini.entries.map { it.years })
        fun firstLord(n: Int) = Dashas.yogini((n + 0.5) * 360.0 / 27.0, birth).maha[0].lords.single()
        assertEquals(Yogini.BHRAMARI, firstLord(0))
        assertEquals(Yogini.MANGALA, firstLord(5))
        assertEquals(Yogini.SANKATA, firstLord(4))
        for (n in 0 until 27) assertEquals(Yogini.entries[(n + 3) % 8], firstLord(n))
    }

    @Test
    fun yoginiTreeHasFourCyclesAndThreeLevels() {
        for (year in DashaYear.entries) {
            val tree = Dashas.yogini(77.7, birth, year)
            assertEquals(3, tree.maxDepth)
            assertEquals(32, tree.maha.size)
            val first = tree.maha[0].lords.single().ordinal
            for (j in 0 until 32) {
                val p = tree.maha[j]
                val lord = Yogini.entries[(first + j) % 8]
                assertEquals(listOf(lord), p.lords)
                assertEquals(lord.years * year.days, p.endDay - p.startDay, 1e-9)
                if (j > 0) assertEquals(tree.maha[j - 1].endDay, p.startDay, 0.0)
            }
            assertTrue(tree.maha[0].startDay <= 0.0 && tree.maha[0].endDay > 0.0)
            assertEquals(4 * 36 * year.days, tree.maha.last().endDay - tree.maha.first().startDay, 1e-8)
            var leaves = 0
            for (maha in tree.maha) {
                val antars = tree.children(maha)
                assertEquals(8, antars.size)
                assertTiles(tree, maha, antars)
                for (k in 0 until 8) {
                    assertEquals(Yogini.entries[(maha.lords.last().ordinal + k) % 8], antars[k].lords.last())
                    val d = maha.endDay - maha.startDay
                    assertEquals(d * antars[k].lords.last().years / 36.0, antars[k].endDay - antars[k].startDay, 1e-8)
                }
                for (antar in antars) {
                    val pratyantars = tree.children(antar)
                    assertTiles(tree, antar, pratyantars)
                    leaves += pratyantars.size
                    assertFailsWith<IllegalArgumentException> { tree.children(pratyantars[0]) }
                }
            }
            assertEquals(32 * 64, leaves)
            assertEquals(3, tree.at(birth).size)
            assertEquals(tree.maha[0], tree.at(birth)[0])
        }
    }

    @Test
    fun moonLongitudeIsReadModulo360AndMustBeFinite() {
        assertEquals(Dashas.vimshottari(10.0, birth).maha, Dashas.vimshottari(370.0, birth).maha)
        assertEquals(Graha.MERCURY, Dashas.vimshottari(360.0.nextDownForTest(), birth).maha[0].lords.single())
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Dashas.vimshottari(bad, birth) }
            assertFailsWith<IllegalArgumentException> { Dashas.yogini(bad, birth) }
        }
    }

    private fun Double.nextDownForTest(): Double = Double.fromBits(toBits() - 1)

    @Test
    fun textFormCarriesNoDates() {
        val tree = Dashas.vimshottari(10.0, birth)
        assertFalse(tree.maha[0].toString().any { it.isDigit() })
        assertFalse(tree.maha.toString().any { it.isDigit() })
        assertFalse(tree.toString().contains("2000"))
    }
}
