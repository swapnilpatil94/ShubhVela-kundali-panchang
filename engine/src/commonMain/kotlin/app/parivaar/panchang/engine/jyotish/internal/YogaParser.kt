package app.parivaar.panchang.engine.jyotish.internal

import app.parivaar.panchang.engine.jyotish.Graha
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One parsed yoga or dosha rule. */
internal class ParsedRule(
    val id: String,
    val category: String,
    val status: String,
    val variant: String,
    val sourceId: String,
    val contentId: String,
    val cond: Cond,
)

private typealias CondReader = (JsonElement, String, Int) -> Cond

/** Reads `rules/yogas.json` into [ParsedRule]s (docs/specs/M02.md section 3.8). No free-text fields exist. */
@Suppress("TooManyFunctions") // one small reader per DSL node
internal object YogaParser {
    private const val MAX_DEPTH = 16
    private const val HOUSES = 12
    private const val PAIR = 2
    private val KENDRA = setOf(1, 4, 7, 10)
    private val TRIKONA = setOf(1, 5, 9)
    private val TOP_KEYS = setOf("version", "rules")
    private val RULE_KEYS = setOf("id", "category", "status", "variant", "sourceId", "contentId", "when")
    private val CATEGORIES = setOf("YOGA", "DOSHA")
    private val STATUSES = setOf("APPROVED", "VERIFY")
    private val GRAHAS = Graha.entries.associateBy { it.name }

    private val READERS: Map<String, CondReader> = mapOf(
        "all" to { e, p, d -> Cond.AllOf(list(e, p, d)) },
        "any" to { e, p, d -> Cond.AnyOf(list(e, p, d)) },
        "not" to { e, p, d -> Cond.Not(cond(e, p, d + 1)) },
        "in_house" to { e, p, _ -> inHouse(RuleJson.obj(e, p), p, null) },
        "in_kendra" to { e, p, _ -> inHouse(RuleJson.obj(e, p), p, KENDRA) },
        "in_trikona" to { e, p, _ -> inHouse(RuleJson.obj(e, p), p, TRIKONA) },
        "none_in_houses" to { e, p, _ -> noneIn(RuleJson.obj(e, p), p) },
        "conjunct" to { e, p, _ -> pair(e, p).let { (a, b) -> Cond.Conjunct(a, b) } },
        "exchange" to { e, p, _ -> pair(e, p).let { (a, b) -> Cond.Exchange(a, b) } },
        "aspect" to { e, p, _ -> aspect(RuleJson.obj(e, p), p) },
        "exalted" to { e, p, _ -> Cond.Exalted(term(e, p)) },
        "debilitated" to { e, p, _ -> Cond.Debilitated(term(e, p)) },
        "own_sign" to { e, p, _ -> Cond.OwnSign(term(e, p)) },
        "all_in_arc" to { e, p, _ -> arc(RuleJson.obj(e, p), p) },
    )

    fun parse(json: String): List<ParsedRule> {
        val root = RuleJson.obj(RuleJson.parse(json), "$")
        RuleJson.onlyKeys(root, TOP_KEYS, "$")
        RuleJson.intIn(RuleJson.field(root, "version", "$"), "$.version", 1..1)
        val seen = HashSet<String>()
        return RuleJson.arr(RuleJson.field(root, "rules", "$"), "$.rules").mapIndexed { i, e ->
            val rule = rule(RuleJson.obj(e, "$.rules[$i]"), "$.rules[$i]")
            if (!seen.add(rule.id)) RuleJson.fail("DUPLICATE", "$.rules[$i].id")
            rule
        }
    }

    private fun rule(o: JsonObject, p: String): ParsedRule {
        RuleJson.onlyKeys(o, RULE_KEYS, p)
        fun s(k: String) = RuleJson.nonBlank(RuleJson.field(o, k, p), "$p.$k")
        return ParsedRule(
            id = s("id"),
            category = RuleJson.oneOf(s("category"), CATEGORIES, "$p.category"),
            status = RuleJson.oneOf(s("status"), STATUSES, "$p.status"),
            variant = s("variant"),
            sourceId = s("sourceId"),
            contentId = s("contentId"),
            cond = cond(RuleJson.field(o, "when", p), "$p.when", 1),
        )
    }

    private fun cond(e: JsonElement, p: String, depth: Int): Cond {
        if (depth > MAX_DEPTH) RuleJson.fail("TOO_DEEP", p)
        val o = RuleJson.obj(e, p)
        if (o.size != 1) RuleJson.fail("BAD_COUNT", p)
        val (key, value) = o.entries.first()
        val reader = READERS[key] ?: RuleJson.fail("UNKNOWN_KEY", "$p.$key")
        return reader(value, "$p.$key", depth)
    }

    private fun list(e: JsonElement, p: String, depth: Int): List<Cond> {
        val items = RuleJson.arr(e, p)
        if (items.isEmpty()) RuleJson.fail("EMPTY", p)
        return items.mapIndexed { i, c -> cond(c, "$p[$i]", depth + 1) }
    }

    private fun term(e: JsonElement, p: String): Term = when (e) {
        is JsonPrimitive -> Term.Fixed(GRAHAS[RuleJson.str(e, p)] ?: RuleJson.fail("BAD_VALUE", p))
        is JsonObject -> {
            RuleJson.onlyKeys(e, setOf("lord_of"), p)
            Term.LordOf(RuleJson.intIn(RuleJson.field(e, "lord_of", p), "$p.lord_of", 1..HOUSES))
        }
        else -> RuleJson.fail("BAD_TYPE", p)
    }

    /** A `from`: the string LAGNA (null) or a term. */
    private fun ref(e: JsonElement, p: String): Term? =
        if (e is JsonPrimitive && e.isString && e.content == "LAGNA") null else term(e, p)

    private fun terms(e: JsonElement, p: String): List<Term> {
        val items = RuleJson.arr(e, p)
        if (items.isEmpty()) RuleJson.fail("EMPTY", p)
        return items.mapIndexed { i, t -> term(t, "$p[$i]") }
    }

    private fun houses(e: JsonElement, p: String): Set<Int> {
        val items = RuleJson.arr(e, p)
        if (items.isEmpty()) RuleJson.fail("EMPTY", p)
        return items.mapIndexed { i, h -> RuleJson.intIn(h, "$p[$i]", 1..HOUSES) }.toSet()
    }

    private fun pair(e: JsonElement, p: String): Pair<Term, Term> {
        val items = RuleJson.arr(e, p)
        if (items.size != PAIR) RuleJson.fail("BAD_COUNT", p)
        return term(items[0], "$p[0]") to term(items[1], "$p[1]")
    }

    private fun inHouse(o: JsonObject, p: String, fixed: Set<Int>?): Cond {
        RuleJson.onlyKeys(o, if (fixed == null) setOf("graha", "from", "houses") else setOf("graha", "from"), p)
        return Cond.InHouse(
            graha = term(RuleJson.field(o, "graha", p), "$p.graha"),
            from = ref(RuleJson.field(o, "from", p), "$p.from"),
            houses = fixed ?: houses(RuleJson.field(o, "houses", p), "$p.houses"),
        )
    }

    private fun noneIn(o: JsonObject, p: String): Cond {
        RuleJson.onlyKeys(o, setOf("grahas", "from", "houses"), p)
        return Cond.NoneInHouses(
            grahas = terms(RuleJson.field(o, "grahas", p), "$p.grahas"),
            from = ref(RuleJson.field(o, "from", p), "$p.from"),
            houses = houses(RuleJson.field(o, "houses", p), "$p.houses"),
        )
    }

    private fun aspect(o: JsonObject, p: String): Cond {
        RuleJson.onlyKeys(o, setOf("by", "on"), p)
        return Cond.Aspect(term(RuleJson.field(o, "by", p), "$p.by"), term(RuleJson.field(o, "on", p), "$p.on"))
    }

    private fun arc(o: JsonObject, p: String): Cond {
        RuleJson.onlyKeys(o, setOf("grahas", "from", "to"), p)
        return Cond.AllInArc(
            grahas = terms(RuleJson.field(o, "grahas", p), "$p.grahas"),
            from = term(RuleJson.field(o, "from", p), "$p.from"),
            to = term(RuleJson.field(o, "to", p), "$p.to"),
        )
    }
}
