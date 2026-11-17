package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import app.parivaar.panchang.engine.jyotish.YogaRules
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** docs/specs/M12.md rule 12, `ManglikLevelTest`. Synthetic charts only. */
class ManglikLevelTest {
    private val rules = YogaRules.parse(RuleTestSupport.rule("yogas.json"))

    /** A chart with the ascendant, Moon, Venus and Mars at the given longitudes (others at 200 deg). */
    private fun chart(asc: Double, moon: Double, venus: Double, mars: Double) =
        RuleTestSupport.chart(asc, DoubleArray(9) { 200.0 }.also {
            it[Graha.MOON.ordinal] = moon
            it[Graha.VENUS.ordinal] = venus
            it[Graha.MARS.ordinal] = mars
        })

    private fun both(asc: Double, moon: Double, venus: Double, mars: Double, known: Boolean): Int {
        val f = chart(asc, moon, venus, mars)
        val a = manglikLevel(f, known)
        assertEquals(a, manglikLevel(rules.evaluate(f), known), "parity with rules/yogas.json")
        return a
    }

    @Test
    fun `k = 0 to 3 with the lagna known`() {
        assertEquals(0, both(5.0, 5.0, 5.0, 65.0, true)) // Mars in house 3 from all three
        assertEquals(1, both(5.0, 65.0, 65.0, 5.0, true)) // lagna only
        assertEquals(2, both(5.0, 5.0, 65.0, 5.0, true)) // lagna and Moon
        assertEquals(2, both(5.0, 5.0, 5.0, 5.0, true)) // all three
    }

    @Test
    fun `the lagna rule is skipped when the lagna is unknown`() {
        assertEquals(0, both(5.0, 5.0, 5.0, 65.0, false))
        assertEquals(0, both(5.0, 65.0, 65.0, 5.0, false)) // only the lagna fired
        assertEquals(1, both(5.0, 5.0, 65.0, 5.0, false)) // Moon only
        assertEquals(2, both(5.0, 5.0, 5.0, 5.0, false)) // Moon and Venus
    }

    @Test
    fun `parity with the MANGLIK rules on random charts`() {
        val rnd = Random(0x5EED)
        repeat(3000) {
            val known = rnd.nextBoolean()
            both(rnd.nextDouble(360.0), rnd.nextDouble(360.0), rnd.nextDouble(360.0), rnd.nextDouble(360.0), known)
        }
    }

    @Test
    fun `pair notes`() {
        assertEquals(MANGLIK_PAIR_BALANCED, manglikPairNoteId(0, 0))
        assertEquals(MANGLIK_PAIR_BALANCED, manglikPairNoteId(1, 2))
        assertEquals(MANGLIK_PAIR_CONSULT, manglikPairNoteId(0, 1))
        assertEquals(MANGLIK_PAIR_CONSULT, manglikPairNoteId(2, 0))
        assertEquals("BASE_NO_CANCELLATION", MANGLIK_VARIANT)
        assertFailsWith<IllegalArgumentException> { manglikPairNoteId(3, 0) }
    }
}
