package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.NakshatraTable
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import app.parivaar.panchang.engine.jyotish.Zodiac
import app.parivaar.panchang.engine.match.VashyaClass.CHATUSHPADA
import app.parivaar.panchang.engine.match.VashyaClass.JALACHARA
import app.parivaar.panchang.engine.match.VashyaClass.KEETA
import app.parivaar.panchang.engine.match.VashyaClass.MANAVA
import app.parivaar.panchang.engine.match.VashyaClass.VANACHARA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** docs/specs/M12.md rules 1-10 and the three hand cases. */
class AshtakootaTest {
    private val nak = NakshatraTable.parse(RuleTestSupport.rule("nakshatra.json"))
    private val tables = KootaTables.parse(RuleTestSupport.rule("koota.json"))
    private val calc = AshtakootaCalculator(tables, nak)

    private fun q(r: AshtakootaResult) = r.scores.map { it.q }

    @Test
    fun `hand case 1 - Ashwini p1 with itself is 112 Q (28 gunas)`() {
        val a = MoonPoint(0, 1, CHATUSHPADA)
        val r = calc.match(a, a)
        assertEquals(listOf(4, 8, 12, 16, 20, 24, 28, 0), q(r))
        assertEquals(112, r.totalQ)
        assertEquals(144, r.maxQ)
        assertEquals("VERIFY", r.tableStatus)
        assertEquals(listOf("koota.nadi.same"), r.of(Koota.NADI).noteIds) // same pada: no exception note
    }

    @Test
    fun `hand case 2 - Ashwini p1 with Bharani p1 is 136 Q (34 gunas)`() {
        val r = calc.match(MoonPoint(0, 1, CHATUSHPADA), MoonPoint(1, 1, CHATUSHPADA))
        assertEquals(listOf(4, 8, 12, 8, 20, 24, 28, 32), q(r))
        assertEquals(136, r.totalQ)
    }

    @Test
    fun `hand case 3 - Ashwini p1 with Hasta p1`() {
        val r = calc.match(MoonPoint(0, 1, CHATUSHPADA), MoonPoint(12, 1, MANAVA))
        assertEquals(4, r.of(Koota.VARNA).q)
        assertEquals(6, r.of(Koota.TARA).q)
        assertEquals(0, r.of(Koota.YONI).q)
        assertEquals(2, r.of(Koota.GRAHA_MAITRI).q)
        assertEquals(24, r.of(Koota.GANA).q)
        assertEquals(0, r.of(Koota.BHAKOOT).q)
        assertEquals(listOf("koota.bhakoot.dosha"), r.of(Koota.BHAKOOT).noteIds)
        assertEquals(0, r.of(Koota.NADI).q)
        assertEquals(listOf("koota.nadi.same"), r.of(Koota.NADI).noteIds)
    }

    @Test
    fun `exception notes never change Q`() {
        // Mesha and Vrishchika (6/8, same lord Mars): dosha with the exception note, still 0 Q.
        val r = calc.match(MoonPoint(0, 1, CHATUSHPADA), MoonPoint(16, 2, KEETA))
        assertEquals(0, r.of(Koota.BHAKOOT).q)
        assertEquals(listOf("koota.bhakoot.dosha", "koota.bhakoot.exception_possible"), r.of(Koota.BHAKOOT).noteIds)
        // Ashwini p1 with Ashwini p2: same nadi, same nakshatra, different pada.
        val s = calc.match(MoonPoint(0, 1, CHATUSHPADA), MoonPoint(0, 2, CHATUSHPADA))
        assertEquals(0, s.of(Koota.NADI).q)
        assertEquals(listOf("koota.nadi.same", "koota.nadi.exception_possible"), s.of(Koota.NADI).noteIds)
    }

    @Test
    fun `matchSymmetric is the exact mean of both orientations`() {
        val a = MoonPoint(0, 1, CHATUSHPADA) // Deva, Kshatriya
        val b = MoonPoint(3, 1, CHATUSHPADA) // Rohini: Manushya, Vrishabha (Vaishya)
        val s = calc.matchSymmetric(a, b)
        assertEquals(s.scores, calc.matchSymmetric(b, a).scores)
        assertEquals(22, s.of(Koota.GANA).q) // (24 + 20) / 2
        assertEquals(2, s.of(Koota.VARNA).q) // (4 + 0) / 2
        assertEquals(s.scores.sumOf { it.q }, s.totalQ)
    }

    @Test
    fun `vashyaOf follows rule 2 and agrees with nakshatra json`() {
        val fixed = listOf(
            CHATUSHPADA, CHATUSHPADA, MANAVA, JALACHARA, VANACHARA, MANAVA, MANAVA, KEETA,
            null, null, MANAVA, JALACHARA,
        )
        for (r in 0 until 12) {
            for (d in listOf(0.0, 14.999999, 15.0, 29.999999)) {
                val v = vashyaOf(30.0 * r + d)
                val want = fixed[r] ?: when (r) {
                    8 -> if (d < 15.0) MANAVA else CHATUSHPADA
                    else -> if (d < 15.0) CHATUSHPADA else JALACHARA
                }
                assertEquals(want, v, "sign $r deg $d")
                val names = nak.vashyas(r)
                assertEquals(if (names.size == 1 || d < 15.0) names[0] else names[1], v.name)
            }
        }
    }

    @Test
    fun `vashyaChoices has two classes only for PA p1 and Shravana p2`() {
        val all = (0 until 27).flatMap { n -> (1..4).map { n to it } }
        val twos = all.filter { (n, p) -> vashyaChoices(n, p).size == 2 }
        assertEquals(listOf(19 to 1, 21 to 2), twos)
        assertEquals(listOf(MANAVA, CHATUSHPADA), vashyaChoices(19, 1))
        assertEquals(listOf(CHATUSHPADA, JALACHARA), vashyaChoices(21, 2))
        for (n in 0 until 27) for (p in 1..4) {
            val start = n * 40.0 / 3.0 + (p - 1) * 10.0 / 3.0
            assertTrue(vashyaOf(start + 1e-6) in vashyaChoices(n, p))
            assertTrue(vashyaOf(start + 10.0 / 3.0 - 1e-6) in vashyaChoices(n, p))
            assertEquals(Zodiac.rashiOf(start + 1e-6).ordinal, MoonPoint(n, p, vashyaChoices(n, p)[0]).rashi)
        }
        assertFailsWith<IllegalArgumentException> { MoonPoint(0, 1, MANAVA) }
        assertFailsWith<IllegalArgumentException> { MoonPoint(27, 1, CHATUSHPADA) }
        assertFailsWith<IllegalArgumentException> { MoonPoint(0, 5, CHATUSHPADA) }
    }

    @Test
    fun `sign lords and natural relations equal the spec`() {
        val lords = "MARS VENUS MERCURY MOON SUN MERCURY VENUS MARS JUPITER SATURN SATURN JUPITER".split(" ")
        assertEquals(lords, Rashi.entries.map { Zodiac.lordOf(it).name })
        val spec = mapOf(
            Graha.SUN to "F MOON MARS JUPITER N MERCURY E VENUS SATURN",
            Graha.MOON to "F SUN MERCURY N MARS JUPITER VENUS SATURN E",
            Graha.MARS to "F SUN MOON JUPITER N VENUS SATURN E MERCURY",
            Graha.MERCURY to "F SUN VENUS N MARS JUPITER SATURN E MOON",
            Graha.JUPITER to "F SUN MOON MARS N SATURN E MERCURY VENUS",
            Graha.VENUS to "F MERCURY SATURN N MARS JUPITER E SUN MOON",
            Graha.SATURN to "F MERCURY VENUS N JUPITER E SUN MOON MARS",
        )
        for ((g, s) in spec) {
            var rel = Relation.FRIEND
            for (tok in s.split(" ")) {
                when (tok) {
                    "F" -> rel = Relation.FRIEND
                    "N" -> rel = Relation.NEUTRAL
                    "E" -> rel = Relation.ENEMY
                    else -> assertEquals(rel, tables.relation(g, Graha.valueOf(tok)), "$g $tok")
                }
            }
        }
    }
}
