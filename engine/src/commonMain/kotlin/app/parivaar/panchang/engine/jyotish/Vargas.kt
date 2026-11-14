package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.jyotish.internal.RuleJson
import kotlinx.serialization.json.JsonObject
import kotlin.math.floor
import kotlin.math.min

/**
 * Divisional charts D1 to D60.
 *
 * @property parts number of equal parts N of a sign (30 degrees)
 */
@Suppress("MagicNumber") // each value is the definition of its varga
enum class Varga(val parts: Int) {
    D1(1), D2(2), D3(3), D4(4), D7(7), D9(9), D10(10), D12(12), D16(16), D20(20), D24(24), D27(27), D30(30),
    D40(40), D45(45), D60(60),
}

/** A place in a varga: [rashi] and the 0-based [part] within the sign. */
data class VargaPos(val rashi: Rashi, val part: Int)

/** A divisional chart: the lagna and the nine grahas in [Graha] order. Derived from birth data, so redacted. */
data class VargaChart(val lagna: VargaPos, val grahas: List<VargaPos>) {
    override fun toString(): String = "VargaChart(redacted)"
}

/** The requested varga or variant has no rule (for example D2 KASHINATH, TODO(owner)). */
class VargaUnavailableException(val varga: Varga) : IllegalStateException("VARGA_UNAVAILABLE ${varga.name}")

/**
 * Divisional positions from `rules/vargas.json` (docs/specs/M02.md section 3.7). The Parashara rules are as
 * commonly stated; VERIFY with a hand-worked chart. Immutable.
 */
class Vargas private constructor(private val rules: Map<Pair<Varga, String>, Rule>) {
    private sealed interface Rule {
        val by: String
    }

    private class Cyclic(override val by: String, val starts: List<Start>, val step: Int) : Rule

    private class Table(override val by: String, val tables: List<List<Row>>) : Rule

    private class Start(val relative: Boolean, val value: Int)

    private class Row(val endDeg: Double, val rashi: Int)

    /** The varga position of the sidereal longitude [siderealLon]; D2 follows `settings.hora`, D3 `drekkana`. */
    fun position(v: Varga, siderealLon: Double, settings: CalcSettings = CalcSettings()): VargaPos {
        val variant = when (v) {
            Varga.D2 -> settings.hora.name
            Varga.D3 -> settings.drekkana.name
            else -> PARASHARA
        }
        val rule = rules[v to variant] ?: throw VargaUnavailableException(v)
        val s = Zodiac.rashiOf(siderealLon).ordinal
        val d = Zodiac.degInRashi(siderealLon)
        return when (rule) {
            is Table -> {
                val rows = rule.tables[classOf(rule.by, s)]
                val i = rows.indexOfFirst { d < it.endDeg }
                VargaPos(Rashi.entries[rows[i].rashi], i)
            }
            is Cyclic -> {
                val n = v.parts
                val k = min(n - 1, floor(d * n / SIGN_DEG).toInt())
                val st = rule.starts[classOf(rule.by, s)]
                val start = if (st.relative) s + st.value else st.value
                VargaPos(Rashi.entries[(start + k * rule.step).mod(SIGN_COUNT)], k)
            }
        }
    }

    /** The varga chart of [facts] (its lagna and nine graha longitudes). */
    fun chart(v: Varga, facts: ChartFacts, settings: CalcSettings = CalcSettings()): VargaChart =
        VargaChart(
            lagna = position(v, facts.ascDeg, settings),
            grahas = facts.grahas.map { position(v, it.lon, settings) },
        )

    companion object {
        private const val SIGN_DEG = 30.0
        private const val SIGN_COUNT = 12
        private const val PARASHARA = "PARASHARA"
        private const val MODALITIES = 3
        private const val ELEMENTS = 4
        private val CLASS_SIZE = mapOf("NONE" to 1, "PARITY" to 2, "MODALITY" to 3, "ELEMENT" to 4)
        private val VARIANTS = setOf("PARASHARA", "JAGANNATH", "KASHINATH")
        private val TOP_KEYS = setOf("version", "vargas")
        private val COMMON_KEYS = setOf("id", "variant", "kind", "by")
        private val CYCLIC_KEYS = COMMON_KEYS + setOf("start", "step")
        private val TABLE_KEYS = COMMON_KEYS + setOf("tables")

        /** Class of sign [s] for a `by` rule: NONE 0, PARITY (odd sign 0), MODALITY s mod 3, ELEMENT s mod 4. */
        private fun classOf(by: String, s: Int): Int = when (by) {
            "PARITY" -> s % 2
            "MODALITY" -> s % MODALITIES
            "ELEMENT" -> s % ELEMENTS
            else -> 0
        }

        /** Parses and validates `rules/vargas.json`; throws [RuleFormatException] on any defect. */
        fun parse(json: String): Vargas {
            val root = RuleJson.obj(RuleJson.parse(json), "$")
            RuleJson.onlyKeys(root, TOP_KEYS, "$")
            RuleJson.intIn(RuleJson.field(root, "version", "$"), "$.version", 1..1)
            val list = RuleJson.arr(RuleJson.field(root, "vargas", "$"), "$.vargas")
            val out = LinkedHashMap<Pair<Varga, String>, Rule>()
            list.forEachIndexed { i, e ->
                val p = "$.vargas[$i]"
                val o = RuleJson.obj(e, p)
                val id = RuleJson.str(RuleJson.field(o, "id", p), "$p.id")
                val v = Varga.entries.firstOrNull { it.name == id } ?: RuleJson.fail("BAD_VALUE", "$p.id")
                val variant = RuleJson.oneOf(
                    RuleJson.str(RuleJson.field(o, "variant", p), "$p.variant"),
                    VARIANTS,
                    "$p.variant",
                )
                if (out.put(v to variant, rule(o, p)) != null) RuleJson.fail("DUPLICATE", p)
            }
            return Vargas(out)
        }

        private fun rule(o: JsonObject, p: String): Rule {
            val by = RuleJson.oneOf(
                RuleJson.str(RuleJson.field(o, "by", p), "$p.by"),
                CLASS_SIZE.keys,
                "$p.by",
            )
            return when (RuleJson.str(RuleJson.field(o, "kind", p), "$p.kind")) {
                "CYCLIC" -> cyclic(o, p, by)
                "TABLE" -> table(o, p, by)
                else -> RuleJson.fail("BAD_VALUE", "$p.kind")
            }
        }

        private fun cyclic(o: JsonObject, p: String, by: String): Rule {
            RuleJson.onlyKeys(o, CYCLIC_KEYS, p)
            val starts = RuleJson.arr(RuleJson.field(o, "start", p), "$p.start").mapIndexed { i, e ->
                val sp = "$p.start[$i]"
                val so = RuleJson.obj(e, sp)
                if (so.size != 1) RuleJson.fail("BAD_COUNT", sp)
                val (key, value) = so.entries.first()
                if (key != "rel" && key != "abs") RuleJson.fail("UNKNOWN_KEY", "$sp.$key")
                Start(key == "rel", RuleJson.intIn(value, "$sp.$key", 0 until SIGN_COUNT))
            }
            if (starts.size != CLASS_SIZE.getValue(by)) RuleJson.fail("BAD_COUNT", "$p.start")
            val step = RuleJson.intIn(RuleJson.field(o, "step", p), "$p.step", 1 until SIGN_COUNT)
            return Cyclic(by, starts, step)
        }

        private fun table(o: JsonObject, p: String, by: String): Rule {
            RuleJson.onlyKeys(o, TABLE_KEYS, p)
            val tables = RuleJson.arr(RuleJson.field(o, "tables", p), "$p.tables").mapIndexed { i, e ->
                val tp = "$p.tables[$i]"
                val rows = RuleJson.arr(e, tp).mapIndexed { j, r ->
                    val rp = "$tp[$j]"
                    val pair = RuleJson.arr(r, rp)
                    if (pair.size != 2) RuleJson.fail("BAD_COUNT", rp)
                    Row(
                        RuleJson.double(pair[0], "$rp[0]"),
                        RuleJson.intIn(pair[1], "$rp[1]", 0 until SIGN_COUNT),
                    )
                }
                val ascending = rows.zipWithNext().all { (a, b) -> a.endDeg < b.endDeg }
                if (rows.isEmpty() || !ascending || rows.last().endDeg != SIGN_DEG) RuleJson.fail("BAD_TABLE", tp)
                rows
            }
            if (tables.size != CLASS_SIZE.getValue(by)) RuleJson.fail("BAD_COUNT", "$p.tables")
            return Table(by, tables)
        }
    }
}
