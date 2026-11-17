package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.jyotish.RuleFormatException
import app.parivaar.panchang.engine.jyotish.internal.RuleJson
import kotlinx.serialization.json.JsonObject

/** Bond types of F3 (docs/specs/M12.md D6). */
enum class BondType { LOVE, CRUSH, BESTIE, SIBLING, PARENT, WORK, ROOMMATE }

/** The five bond axes (MASTER Part 8). */
enum class BondAxis { VIBE, TALK, TRUST, FIGHTS, GROWTH }

/**
 * A bond score: [percent] 0..100 over the included kootas, the band [labelKey] (a content key, never a
 * negative label), [axes] 0..10 each, and the kootas [excluded] for this type (BHAKOOT and NADI for friends).
 */
data class BondScore(val percent: Int, val labelKey: String, val axes: Map<BondAxis, Int>, val excluded: Set<Koota>)

/**
 * Maps an [AshtakootaResult] onto a percent, a label band and five axes (docs/specs/M12.md rules 10-11) with
 * the versioned weights of `content/bond_weights.json`. F3 bond types pass a `matchSymmetric` result (D6).
 *
 * Format (every type and every axis required; a koota left out of an axis has weight 0):
 * ```
 * {"version":1,
 *  "bands":[{"min":80,"labelKey":"bond.label.cosmic_twins"}, ..., {"min":0,"labelKey":"bond.label.growth_bond"}],
 *  "types":{"LOVE":{"exclude":[],"weights":{"VIBE":{"GANA":1,"YONI":1}, "TALK":{...}, "TRUST":{...},
 *           "FIGHTS":{...}, "GROWTH":{...}}}, "BESTIE":{"exclude":["BHAKOOT","NADI"], ...}, ...}}
 * ```
 * Validation (throws [RuleFormatException]): band mins strictly descending and the last is 0; weights are
 * integers >= 0; every axis has a positive total weight; an excluded koota has weight 0 on every axis; BESTIE
 * and SIBLING exclude BHAKOOT and NADI. Immutable.
 */
class BondAxisScorer(weightsJson: String) {
    private class Band(val min: Int, val labelKey: String)

    private class TypeWeights(val exclude: Set<Koota>, val weights: Map<BondAxis, Map<Koota, Int>>)

    private val bands: List<Band>
    private val types: Map<BondType, TypeWeights>

    init {
        val root = RuleJson.obj(RuleJson.parse(weightsJson), "$")
        RuleJson.onlyKeys(root, setOf("version", "bands", "types"), "$")
        RuleJson.intIn(RuleJson.field(root, "version", "$"), "$.version", 1..Int.MAX_VALUE)
        bands = RuleJson.arr(RuleJson.field(root, "bands", "$"), "$.bands").mapIndexed { i, e ->
            val p = "$.bands[$i]"
            val o = RuleJson.obj(e, p)
            RuleJson.onlyKeys(o, setOf("min", "labelKey"), p)
            val key = RuleJson.nonBlank(RuleJson.field(o, "labelKey", p), "$p.labelKey")
            if (!KEY.matches(key)) RuleJson.fail("BAD_VALUE", "$p.labelKey")
            Band(RuleJson.intIn(RuleJson.field(o, "min", p), "$p.min", 0..PERCENT), key)
        }
        if (bands.isEmpty() || bands.last().min != 0) RuleJson.fail("BAD_VALUE", "$.bands")
        if (bands.zipWithNext().any { (a, b) -> a.min <= b.min }) RuleJson.fail("NOT_DESCENDING", "$.bands")
        val t = RuleJson.obj(RuleJson.field(root, "types", "$"), "$.types")
        RuleJson.onlyKeys(t, BondType.entries.map { it.name }.toSet(), "$.types")
        types = BondType.entries.associateWith { type ->
            val p = "$.types.${type.name}"
            typeWeights(RuleJson.obj(RuleJson.field(t, type.name, "$.types"), p), p, type)
        }
    }

    /** Scores [r] for [type]: percent = (200 Q + max) div (2 max), axis = half-up(10 sum(w q) / sum(w maxQ)). */
    fun score(type: BondType, r: AshtakootaResult): BondScore {
        require(r.scores.map { it.koota } == Koota.entries) { "result must hold the eight kootas in order" }
        val tw = types.getValue(type)
        val included = Koota.entries.filter { it !in tw.exclude }
        val q = included.sumOf { r.of(it).q }
        val max = included.sumOf { it.maxQ }
        val percent = halfUp(PERCENT.toLong() * q, max.toLong())
        val axes = BondAxis.entries.associateWith { axis ->
            val w = tw.weights.getValue(axis)
            halfUp(AXIS_MAX.toLong() * w.entries.sumOf { (k, wi) -> wi.toLong() * r.of(k).q },
                w.entries.sumOf { (k, wi) -> wi.toLong() * k.maxQ })
        }
        return BondScore(percent, bands.first { percent >= it.min }.labelKey, axes, tw.exclude)
    }

    private companion object {
        const val PERCENT = 100
        const val AXIS_MAX = 10
        const val WEIGHT_MAX = 1000
        val KEY = Regex("^[a-z0-9_.]{1,64}$")
        val FRIENDS = setOf(BondType.BESTIE, BondType.SIBLING)
        val FRIEND_EXCLUDES = setOf(Koota.BHAKOOT, Koota.NADI)

        /** round-half-up of [num] / [den] for num >= 0, den > 0: (2 num + den) div (2 den). */
        fun halfUp(num: Long, den: Long): Int = ((2 * num + den) / (2 * den)).toInt()

        fun typeWeights(o: JsonObject, p: String, type: BondType): TypeWeights {
            RuleJson.onlyKeys(o, setOf("exclude", "weights"), p)
            val exclude = RuleJson.arr(RuleJson.field(o, "exclude", p), "$p.exclude").mapIndexed { i, e ->
                koota(RuleJson.str(e, "$p.exclude[$i]"), "$p.exclude[$i]")
            }.toSet()
            if (type in FRIENDS && !exclude.containsAll(FRIEND_EXCLUDES)) RuleJson.fail("FRIEND_EXCLUDES", "$p.exclude")
            val w = RuleJson.obj(RuleJson.field(o, "weights", p), "$p.weights")
            RuleJson.onlyKeys(w, BondAxis.entries.map { it.name }.toSet(), "$p.weights")
            val weights = BondAxis.entries.associateWith { axis ->
                val ap = "$p.weights.${axis.name}"
                val a = RuleJson.obj(RuleJson.field(w, axis.name, "$p.weights"), ap)
                val m = a.entries.associate { (k, v) ->
                    koota(k, "$ap.$k") to RuleJson.intIn(v, "$ap.$k", 0..WEIGHT_MAX)
                }
                if (m.entries.sumOf { (k, wi) -> wi * k.maxQ } == 0) RuleJson.fail("ZERO_WEIGHT", ap)
                if (m.any { (k, wi) -> wi > 0 && k in exclude }) RuleJson.fail("EXCLUDED_WEIGHT", ap)
                m
            }
            return TypeWeights(exclude, weights)
        }

        fun koota(name: String, p: String): Koota =
            Koota.entries.firstOrNull { it.name == name } ?: RuleJson.fail("BAD_VALUE", p)
    }
}
