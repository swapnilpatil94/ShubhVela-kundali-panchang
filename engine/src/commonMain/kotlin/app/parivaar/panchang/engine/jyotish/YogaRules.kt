package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.jyotish.internal.ParsedRule
import app.parivaar.panchang.engine.jyotish.internal.YogaParser

/** Review state of a rule. Everything shipped in M02 is [VERIFY] until a pandit signs it off. */
enum class RuleStatus { APPROVED, VERIFY }

/**
 * Whether one rule holds in a chart. Ids only; display text belongs to content packs.
 *
 * @property ruleId rule id, e.g. `GAJAKESARI`
 * @property present the rule's condition holds
 * @property category `YOGA` or `DOSHA` (internal ids, not display words)
 * @property status review state of the rule
 * @property variant rule variant id, e.g. `BASE_NO_CANCELLATION`
 * @property sourceId id of the source record
 * @property contentId id of the content-pack entry
 */
data class YogaFinding(
    val ruleId: String,
    val present: Boolean,
    val category: String,
    val status: RuleStatus,
    val variant: String,
    val sourceId: String,
    val contentId: String,
)

/**
 * Yoga and dosha detectors driven by `rules/yogas.json` (docs/specs/M02.md section 3.8). Houses are counted by
 * sign for every house system. Every shipped rule is VERIFY. Immutable.
 */
class YogaRules private constructor(private val rules: List<ParsedRule>) {
    /** Rule ids in file order. */
    val ids: List<String> = rules.map { it.id }

    /** One finding per rule, in file order. */
    fun evaluate(facts: ChartFacts): List<YogaFinding> = rules.map {
        YogaFinding(
            ruleId = it.id,
            present = it.cond.eval(facts),
            category = it.category,
            status = RuleStatus.valueOf(it.status),
            variant = it.variant,
            sourceId = it.sourceId,
            contentId = it.contentId,
        )
    }

    companion object {
        /** Parses and validates `rules/yogas.json`; throws [RuleFormatException] on any defect. */
        fun parse(json: String): YogaRules = YogaRules(YogaParser.parse(json))
    }
}
