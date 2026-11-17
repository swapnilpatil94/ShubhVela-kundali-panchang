package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.jyotish.ChartFacts
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.YogaFinding
import app.parivaar.panchang.engine.jyotish.Zodiac

/**
 * Manglik level (docs/specs/M12.md rule 12), variant [MANGLIK_VARIANT]: no cancellation is applied (the
 * cancellation list is VERIFY). Ids only; the UI phrases the result calmly, with no fear words and no remedies.
 */
const val MANGLIK_VARIANT = "BASE_NO_CANCELLATION"

/** Note id when both are Manglik (level >= 1) or neither is. */
const val MANGLIK_PAIR_BALANCED = "manglik.pair.balanced"

/** Note id otherwise ("many traditions apply cancellations; a family pandit can advise"). */
const val MANGLIK_PAIR_CONSULT = "manglik.pair.consult_note"

private const val LEVEL_FULL = 2
private const val RULE_LAGNA = "MANGLIK_LAGNA"
private val RULES = setOf(RULE_LAGNA, "MANGLIK_MOON", "MANGLIK_VENUS")

/** Houses by sign from the reference in which Mars fires a MANGLIK_* rule of `rules/yogas.json`. */
private val MARS_HOUSES = setOf(1, 2, 4, 7, 8, 12)

private fun level(k: Int): Int = minOf(k, LEVEL_FULL)

/**
 * k = the number of MANGLIK_LAGNA / MANGLIK_MOON / MANGLIK_VENUS rules that fire (LAGNA skipped when the lagna
 * is unknown): 0 none, 1 partial (k = 1), 2 full (k >= 2). VERIFY: the partial/full definition. Same
 * conditions as `rules/yogas.json` (Mars in houses 1, 2, 4, 7, 8, 12 by sign); `ManglikLevelTest` asserts
 * parity with [app.parivaar.panchang.engine.jyotish.YogaRules].
 */
fun manglikLevel(facts: ChartFacts, lagnaKnown: Boolean): Int {
    val mars = facts.of(Graha.MARS).rashi
    val refs = buildList {
        if (lagnaKnown) add(facts.lagna)
        add(facts.of(Graha.MOON).rashi)
        add(facts.of(Graha.VENUS).rashi)
    }
    return level(refs.count { Zodiac.houseFrom(it, mars) in MARS_HOUSES })
}

/** [manglikLevel] from the findings of `YogaRules.evaluate` (the MANGLIK_* rules of `rules/yogas.json`). */
fun manglikLevel(findings: List<YogaFinding>, lagnaKnown: Boolean): Int =
    level(findings.count { it.present && it.ruleId in RULES && (lagnaKnown || it.ruleId != RULE_LAGNA) })

/** The pair note for two Manglik levels (0..2 each). */
fun manglikPairNoteId(a: Int, b: Int): String {
    require(a in 0..LEVEL_FULL && b in 0..LEVEL_FULL) { "manglik level out of range" }
    return if ((a >= 1) == (b >= 1)) MANGLIK_PAIR_BALANCED else MANGLIK_PAIR_CONSULT
}
