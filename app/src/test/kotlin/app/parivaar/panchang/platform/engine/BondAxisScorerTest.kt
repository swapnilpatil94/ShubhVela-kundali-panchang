package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.engine.match.AshtakootaResult
import app.parivaar.panchang.engine.match.BondAxis
import app.parivaar.panchang.engine.match.BondAxisScorer
import app.parivaar.panchang.engine.match.BondType
import app.parivaar.panchang.engine.match.Koota
import app.parivaar.panchang.engine.match.KootaScore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shipped content/bond_weights.json through the engine scorer: percent edges, friend exclusions, lowest band. */
class BondAxisScorerTest {
    private val module =
        File(System.getProperty("user.dir")).let { if (File(it, "build.gradle.kts").exists()) it else File(it, "app") }
    private val scorer = BondAxisScorer(File(module.parentFile, "content/bond_weights.json").readText())

    private fun result(q: (Koota) -> Int): AshtakootaResult {
        val scores = Koota.entries.map { KootaScore(it, q(it), emptyList()) }
        return AshtakootaResult(scores, scores.sumOf { it.q }, 144, "VERIFY")
    }

    @Test fun percentEdges() {
        assertEquals(0, scorer.score(BondType.LOVE, result { 0 }).percent)
        assertEquals(100, scorer.score(BondType.LOVE, result { it.maxQ }).percent)
        val friendMax = result { if (it == Koota.BHAKOOT || it == Koota.NADI) 0 else it.maxQ }
        assertEquals(100, scorer.score(BondType.BESTIE, friendMax).percent, "84 of 84")
        assertEquals(58, scorer.score(BondType.LOVE, friendMax).percent, "84 of 144, half-up")
    }

    @Test fun friendTypesExcludeBhakootAndNadi() {
        val s = scorer.score(BondType.SIBLING, result { it.maxQ })
        assertEquals(setOf(Koota.BHAKOOT, Koota.NADI), s.excluded)
        assertTrue(scorer.score(BondType.LOVE, result { it.maxQ }).excluded.isEmpty())
        assertEquals(BondAxis.entries.toSet(), s.axes.keys)
    }

    @Test fun lowestBandIsGrowthBond() {
        assertEquals("bond.label.growth_bond", scorer.score(BondType.LOVE, result { 0 }).labelKey)
        assertEquals("bond.label.cosmic_twins", scorer.score(BondType.LOVE, result { it.maxQ }).labelKey)
        assertTrue(scorer.score(BondType.LOVE, result { 0 }).axes.values.all { it == 0 })
    }
}
