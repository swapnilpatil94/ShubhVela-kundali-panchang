package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.RuleFormatException
import app.parivaar.panchang.engine.jyotish.internal.RuleJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A graha's natural view of another graha (docs/specs/M12.md rule 5). */
internal enum class Relation { FRIEND, NEUTRAL, ENEMY }

/**
 * The Ashtakoota tables read from `rules/koota.json` (docs/specs/M12.md rules 1-8). Every value is stored in
 * quarter-points (Q = 4 x points), so all arithmetic is exact integer arithmetic. Every shipped table is
 * VERIFY until a pandit signs it off. Immutable.
 */
@Suppress("LongParameterList") // one field per koota table; the constructor is private
class KootaTables private constructor(
    internal val status: Map<Koota, String>,
    internal val varnaRank: Map<String, Int>,
    private val vashyaQ: List<List<Int>>,
    internal val taraBad: Set<Int>,
    internal val taraQ: List<Int>,
    internal val yoniIndex: Map<String, Int>,
    private val yoniQ: List<List<Int>>,
    private val relations: Map<Graha, Map<Graha, Relation>>,
    internal val gmSameQ: Int,
    private val gmPairQ: Map<Set<Relation>, Int>,
    internal val ganaIndex: Map<String, Int>,
    private val ganaQ: List<List<Int>>,
    internal val bhakootDosha: Set<Set<Int>>,
) {
    /** `APPROVED` when every table is approved, else `VERIFY`. */
    val tableStatus: String = if (status.values.all { it == APPROVED }) APPROVED else VERIFY

    internal fun vashyaQ(groom: VashyaClass, bride: VashyaClass): Int = vashyaQ[groom.ordinal][bride.ordinal]

    internal fun yoniQ(groom: Int, bride: Int): Int = yoniQ[groom][bride]

    internal fun ganaQ(groom: Int, bride: Int): Int = ganaQ[groom][bride]

    internal fun relation(of: Graha, other: Graha): Relation = relations.getValue(of).getValue(other)

    /** Graha Maitri Q for the sign lords [a] and [b] (order free: the pair of views is unordered). */
    internal fun grahaMaitriQ(a: Graha, b: Graha): Int =
        if (a == b) gmSameQ else gmPairQ.getValue(setOf(relation(a, b), relation(b, a)))

    companion object {
        internal const val VERIFY = "VERIFY"
        internal const val APPROVED = "APPROVED"
        private val STATUSES = setOf(VERIFY, APPROVED)
        private const val Q_PER_POINT = 4
        private const val TARA_MAX = 9
        private const val SIGNS = 12
        private val LORDS = listOf(
            Graha.SUN, Graha.MOON, Graha.MARS, Graha.MERCURY, Graha.JUPITER, Graha.VENUS, Graha.SATURN,
        )
        private val RELATION_KEYS =
            listOf("friends" to Relation.FRIEND, "neutral" to Relation.NEUTRAL, "enemies" to Relation.ENEMY)
        private val LORD_NAMES = LORDS.map { it.name }.toSet()
        private val TABLE_KEYS = mapOf(
            Koota.VARNA to "varna", Koota.VASHYA to "vashya", Koota.TARA to "tara", Koota.YONI to "yoni",
            Koota.GRAHA_MAITRI to "grahaMaitri", Koota.GANA to "gana", Koota.BHAKOOT to "bhakoot",
            Koota.NADI to "nadi",
        )
        private val GM_PAIRS = mapOf(
            "FF" to setOf(Relation.FRIEND),
            "FN" to setOf(Relation.FRIEND, Relation.NEUTRAL),
            "NN" to setOf(Relation.NEUTRAL),
            "FE" to setOf(Relation.FRIEND, Relation.ENEMY),
            "NE" to setOf(Relation.NEUTRAL, Relation.ENEMY),
            "EE" to setOf(Relation.ENEMY),
        )

        /** Parses and validates `rules/koota.json`; throws [RuleFormatException] on any defect. */
        @Suppress("LongMethod") // a flat sequence of table reads, one block per koota
        fun parse(json: String): KootaTables {
            val root = RuleJson.obj(RuleJson.parse(json), "$")
            RuleJson.onlyKeys(root, TABLE_KEYS.values.toSet() + "version", "$")
            RuleJson.intIn(RuleJson.field(root, "version", "$"), "$.version", 1..1)
            val t = TABLE_KEYS.mapValues { (_, k) -> RuleJson.obj(RuleJson.field(root, k, "$"), "$.$k") }
            val status = t.mapValues { (k, o) ->
                val p = "$.${TABLE_KEYS.getValue(k)}.status"
                RuleJson.oneOf(RuleJson.str(RuleJson.field(o, "status", p.removeSuffix(".status")), p), STATUSES, p)
            }

            val varna = t.getValue(Koota.VARNA)
            RuleJson.onlyKeys(varna, setOf("status", "rank"), "$.varna")
            val rankObj = RuleJson.obj(RuleJson.field(varna, "rank", "$.varna"), "$.varna.rank")
            if (rankObj.isEmpty()) RuleJson.fail("BAD_COUNT", "$.varna.rank")
            val varnaRank = rankObj.mapValues { (k, v) -> RuleJson.intIn(v, "$.varna.rank.$k", 1..rankObj.size) }

            val vashya = t.getValue(Koota.VASHYA)
            RuleJson.onlyKeys(vashya, setOf("status", "order", "points"), "$.vashya")
            val vOrder = names(vashya, "$.vashya", VashyaClass.entries.size)
            if (vOrder.toSet() != VashyaClass.entries.map { it.name }.toSet()) {
                RuleJson.fail("BAD_VALUE", "$.vashya.order")
            }
            val vRaw = matrix(vashya, "$.vashya", vOrder.size, Koota.VASHYA.maxQ)
            val vashyaQ = VashyaClass.entries.map { g ->
                VashyaClass.entries.map { b -> vRaw[vOrder.indexOf(g.name)][vOrder.indexOf(b.name)] }
            }
            evenSums(vashyaQ, "$.vashya.points")

            val tara = t.getValue(Koota.TARA)
            RuleJson.onlyKeys(tara, setOf("status", "bad", "bothGood", "oneGood", "noneGood"), "$.tara")
            val bad = RuleJson.arr(RuleJson.field(tara, "bad", "$.tara"), "$.tara.bad")
                .mapIndexed { i, e -> RuleJson.intIn(e, "$.tara.bad[$i]", 1..TARA_MAX) }
            if (bad.toSet().size != bad.size) RuleJson.fail("DUPLICATE", "$.tara.bad")
            val taraQ = listOf("noneGood", "oneGood", "bothGood").map {
                quarter(RuleJson.field(tara, it, "$.tara"), "$.tara.$it", Koota.TARA.maxQ)
            }

            val yoni = t.getValue(Koota.YONI)
            RuleJson.onlyKeys(yoni, setOf("status", "order", "points"), "$.yoni")
            val yOrder = names(yoni, "$.yoni", null)
            val yoniQ = matrix(yoni, "$.yoni", yOrder.size, Koota.YONI.maxQ)
            for (i in yoniQ.indices) for (j in yoniQ.indices) {
                if (yoniQ[i][j] != yoniQ[j][i]) RuleJson.fail("NOT_SYMMETRIC", "$.yoni.points[$i][$j]")
            }

            val gm = t.getValue(Koota.GRAHA_MAITRI)
            RuleJson.onlyKeys(gm, setOf("status", "relations", "points"), "$.grahaMaitri")
            val relObj = RuleJson.field(gm, "relations", "$.grahaMaitri")
            val relations = relations(RuleJson.obj(relObj, "$.grahaMaitri.relations"))
            val pts = RuleJson.obj(RuleJson.field(gm, "points", "$.grahaMaitri"), "$.grahaMaitri.points")
            RuleJson.onlyKeys(pts, GM_PAIRS.keys + "SAME", "$.grahaMaitri.points")
            val gp = "$.grahaMaitri.points"
            fun gmQ(k: String) = quarter(RuleJson.field(pts, k, gp), "$gp.$k", Koota.GRAHA_MAITRI.maxQ)
            val gmPairQ = GM_PAIRS.entries.associate { (k, v) -> v to gmQ(k) }

            val gana = t.getValue(Koota.GANA)
            RuleJson.onlyKeys(gana, setOf("status", "order", "points"), "$.gana")
            val gOrder = names(gana, "$.gana", null)
            val ganaQ = matrix(gana, "$.gana", gOrder.size, Koota.GANA.maxQ)
            evenSums(ganaQ, "$.gana.points")

            val bhakoot = t.getValue(Koota.BHAKOOT)
            RuleJson.onlyKeys(bhakoot, setOf("status", "doshaPairs"), "$.bhakoot")
            val dosha = RuleJson.arr(RuleJson.field(bhakoot, "doshaPairs", "$.bhakoot"), "$.bhakoot.doshaPairs")
                .mapIndexed { i, e -> doshaPair(e, "$.bhakoot.doshaPairs[$i]") }
            RuleJson.onlyKeys(t.getValue(Koota.NADI), setOf("status"), "$.nadi")

            return KootaTables(
                status = status,
                varnaRank = varnaRank,
                vashyaQ = vashyaQ,
                taraBad = bad.toSet(),
                taraQ = taraQ,
                yoniIndex = yOrder.withIndex().associate { (i, s) -> s to i },
                yoniQ = yoniQ,
                relations = relations,
                gmSameQ = gmQ("SAME"),
                gmPairQ = gmPairQ,
                ganaIndex = gOrder.withIndex().associate { (i, s) -> s to i },
                ganaQ = ganaQ,
                bhakootDosha = dosha.toSet(),
            )
        }

        private fun names(o: JsonObject, p: String, count: Int?): List<String> {
            val a = RuleJson.arr(RuleJson.field(o, "order", p), "$p.order")
                .mapIndexed { i, e -> RuleJson.nonBlank(e, "$p.order[$i]") }
            if (a.isEmpty() || (count != null && a.size != count)) RuleJson.fail("BAD_COUNT", "$p.order")
            if (a.toSet().size != a.size) RuleJson.fail("DUPLICATE", "$p.order")
            return a
        }

        /** A square [size] x [size] matrix of points, converted to Q in 0..[maxQ]. */
        private fun matrix(o: JsonObject, p: String, size: Int, maxQ: Int): List<List<Int>> {
            val rows = RuleJson.arr(RuleJson.field(o, "points", p), "$p.points")
            if (rows.size != size) RuleJson.fail("BAD_COUNT", "$p.points")
            return rows.mapIndexed { i, r ->
                val row = RuleJson.arr(r, "$p.points[$i]")
                if (row.size != size) RuleJson.fail("BAD_COUNT", "$p.points[$i]")
                row.mapIndexed { j, e -> quarter(e, "$p.points[$i][$j]", maxQ) }
            }
        }

        /** Points [e] as Q; fails unless it is a whole number of quarter-points in 0..[maxQ]. */
        private fun quarter(e: JsonElement, p: String, maxQ: Int): Int {
            val q = RuleJson.double(e, p) * Q_PER_POINT
            if (q < 0.0 || q > maxQ.toDouble()) RuleJson.fail("OUT_OF_RANGE", p)
            val i = q.toInt()
            if (i.toDouble() != q) RuleJson.fail("NOT_QUARTER", p)
            return i
        }

        /** matchSymmetric halves q(a,b) + q(b,a); an odd sum would not be exact, so it is rejected. */
        private fun evenSums(m: List<List<Int>>, p: String) {
            for (i in m.indices) for (j in m.indices) {
                if ((m[i][j] + m[j][i]) % 2 != 0) RuleJson.fail("ODD_SYMMETRIC_SUM", "$p[$i][$j]")
            }
        }

        private fun relations(o: JsonObject): Map<Graha, Map<Graha, Relation>> {
            val p = "$.grahaMaitri.relations"
            RuleJson.onlyKeys(o, LORDS.map { it.name }.toSet(), p)
            return LORDS.associateWith { g ->
                val r = RuleJson.obj(RuleJson.field(o, g.name, p), "$p.${g.name}")
                RuleJson.onlyKeys(r, setOf("friends", "neutral", "enemies"), "$p.${g.name}")
                val m = mutableMapOf<Graha, Relation>()
                for ((k, rel) in RELATION_KEYS) {
                    val path = "$p.${g.name}.$k"
                    RuleJson.arr(RuleJson.field(r, k, "$p.${g.name}"), path).forEachIndexed { i, e ->
                        val name = RuleJson.oneOf(RuleJson.str(e, "$path[$i]"), LORD_NAMES, "$path[$i]")
                        val other = Graha.valueOf(name)
                        if (other == g || m.put(other, rel) != null) RuleJson.fail("DUPLICATE", "$path[$i]")
                    }
                }
                if (m.size != LORDS.size - 1) RuleJson.fail("BAD_COUNT", "$p.${g.name}")
                m
            }
        }

        private fun doshaPair(e: JsonElement, p: String): Set<Int> {
            val a = RuleJson.arr(e, p) as JsonArray
            if (a.size != 2) RuleJson.fail("BAD_COUNT", p)
            val d1 = RuleJson.intIn(a[0], "$p[0]", 1..SIGNS)
            val d2 = RuleJson.intIn(a[1], "$p[1]", 1..SIGNS)
            // d2 = ((-(d1 - 1)) mod 12) + 1, i.e. the count back from the other sign
            if ((d1 - 1 + d2 - 1) % SIGNS != 0) RuleJson.fail("BAD_VALUE", p)
            return setOf(d1, d2)
        }
    }
}
