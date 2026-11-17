package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.jyotish.RuleFormatException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** docs/specs/M12.md rules 10-11 (engine side; content/bond_weights.json is tested by BondWeightsContentTest). */
class BondAxesTest {
    private val pairAxes = """{"VIBE":{"GANA":1,"YONI":1},"TALK":{"GRAHA_MAITRI":1,"VARNA":1},
        "TRUST":{"TARA":1,"VASHYA":1},"FIGHTS":{"BHAKOOT":1,"GANA":1},"GROWTH":{"NADI":1,"TARA":1}}"""
    private val friendAxes = """{"VIBE":{"GANA":1,"YONI":1},"TALK":{"GRAHA_MAITRI":1,"VARNA":1},
        "TRUST":{"TARA":1,"VASHYA":1},"FIGHTS":{"GANA":1,"YONI":1},"GROWTH":{"TARA":1,"GRAHA_MAITRI":1}}"""

    private fun json(friendExclude: String = "\"BHAKOOT\",\"NADI\"", friend: String = friendAxes): String {
        val types = BondType.entries.joinToString(",") {
            if (it == BondType.BESTIE || it == BondType.SIBLING) {
                "\"${it.name}\":{\"exclude\":[$friendExclude],\"weights\":$friend}"
            } else {
                "\"${it.name}\":{\"exclude\":[],\"weights\":$pairAxes}"
            }
        }
        return """{"version":1,"bands":[{"min":80,"labelKey":"bond.label.cosmic_twins"},
            {"min":65,"labelKey":"bond.label.soul_squad"},{"min":50,"labelKey":"bond.label.spicy_combo"},
            {"min":35,"labelKey":"bond.label.opposites_attract"},{"min":0,"labelKey":"bond.label.growth_bond"}],
            "types":{$types}}"""
    }

    private fun result(vararg q: Int) = AshtakootaResult(
        Koota.entries.mapIndexed { i, k -> KootaScore(k, q[i], emptyList()) }, q.sum(), 144, "VERIFY",
    )

    private val scorer = BondAxisScorer(json())
    private val zero = result(0, 0, 0, 0, 0, 0, 0, 0)
    private val full = result(4, 8, 12, 16, 20, 24, 28, 32)

    @Test
    fun `percent edges 0, 144 and 84`() {
        assertEquals(0, scorer.score(BondType.LOVE, zero).percent)
        assertEquals("bond.label.growth_bond", scorer.score(BondType.LOVE, zero).labelKey)
        assertEquals(100, scorer.score(BondType.LOVE, full).percent)
        assertEquals("bond.label.cosmic_twins", scorer.score(BondType.LOVE, full).labelKey)
        val friendFull = result(4, 8, 12, 16, 20, 24, 0, 0)
        val s = scorer.score(BondType.BESTIE, friendFull)
        assertEquals(100, s.percent) // 84 of 84
        assertEquals(setOf(Koota.BHAKOOT, Koota.NADI), s.excluded)
        assertEquals(58, scorer.score(BondType.LOVE, friendFull).percent) // 84 of 144 = 58.33
    }

    @Test
    fun `percent is integer half-up`() {
        // 72 of 144 = 50.0; 1 of 144 = 0.69 -> 1; hand case 1 112 of 144 = 77.78 -> 78
        assertEquals(50, scorer.score(BondType.WORK, result(4, 8, 12, 16, 20, 12, 0, 0)).percent)
        assertEquals(1, scorer.score(BondType.WORK, result(0, 1, 0, 0, 0, 0, 0, 0)).percent)
        assertEquals(78, scorer.score(BondType.LOVE, result(4, 8, 12, 16, 20, 24, 28, 0)).percent)
        assertEquals("bond.label.soul_squad", scorer.score(BondType.LOVE, result(4, 8, 12, 16, 20, 24, 28, 0)).labelKey)
    }

    @Test
    fun `axes are half-up over the weighted maxima`() {
        val s = scorer.score(BondType.LOVE, result(4, 8, 12, 16, 20, 24, 28, 0))
        assertEquals(10, s.axes[BondAxis.VIBE])
        assertEquals(3, s.axes[BondAxis.GROWTH]) // (0 + 12) / (32 + 12) = 2.73 -> 3
        assertEquals(BondAxis.entries.toSet(), s.axes.keys)
        assertEquals(0, scorer.score(BondType.LOVE, zero).axes.values.max())
    }

    @Test
    fun `friend types must exclude Bhakoot and Nadi with zero weight`() {
        assertEquals("FRIEND_EXCLUDES", assertFailsWith<RuleFormatException> { BondAxisScorer(json("\"NADI\"")) }.code)
        val bad = friendAxes.replace("\"FIGHTS\":{\"GANA\":1,\"YONI\":1}", "\"FIGHTS\":{\"BHAKOOT\":1,\"GANA\":1}")
        val e1 = assertFailsWith<RuleFormatException> { BondAxisScorer(json(friend = bad)) }
        assertEquals("EXCLUDED_WEIGHT", e1.code)
        val zeroAxis = friendAxes.replace("\"VIBE\":{\"GANA\":1,\"YONI\":1}", "\"VIBE\":{\"GANA\":0}")
        val e2 = assertFailsWith<RuleFormatException> { BondAxisScorer(json(friend = zeroAxis)) }
        assertEquals("ZERO_WEIGHT", e2.code)
    }

    @Test
    fun `bands must descend to 0`() {
        val j = json().replace("{\"min\":0,\"labelKey\"", "{\"min\":5,\"labelKey\"")
        assertEquals("BAD_VALUE", assertFailsWith<RuleFormatException> { BondAxisScorer(j) }.code)
        val k = json().replace("{\"min\":65,", "{\"min\":85,")
        assertEquals("NOT_DESCENDING", assertFailsWith<RuleFormatException> { BondAxisScorer(k) }.code)
    }
}
