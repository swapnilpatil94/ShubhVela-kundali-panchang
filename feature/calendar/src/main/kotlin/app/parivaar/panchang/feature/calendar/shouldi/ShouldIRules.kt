package app.parivaar.panchang.feature.calendar.shouldi

import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * One "Should I...?" intent: ids and codes only. [prefer] are choghadiya codes; empty tithi/vaar/nakshatra sets
 * mean "no check".
 */
data class IntentRule(
    val id: String,
    val avoid: Set<WindowKind>,
    val prefer: Set<String>,
    val tithiAvoid: Set<Int>,
    val vaarPrefer: Set<Vaar>,
    val nakshatraPrefer: Set<Int>,
    val status: RuleStatus,
    val sourceId: String,
)

/** Loads `rules/should_i.json` through the JSON tree API (the M02-M04 pattern). */
object ShouldIRules {
    private val TOP_KEYS = setOf("version", "intents")
    private val KEYS = setOf(
        "id", "avoid", "prefer", "tithiAvoid", "vaarPrefer", "nakshatraPrefer", "status", "sourceId",
    )
    private const val TITHIS = 30
    private const val NAKSHATRAS = 27

    /** Throws [IllegalArgumentException] on an unknown key, a missing id/status/sourceId, or a bad value. */
    fun parse(json: String): List<IntentRule> {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: kotlinx.serialization.SerializationException) {
            fail("should_i.json is not valid JSON", e)
        }
        val top = root as? JsonObject ?: fail("root must be an object")
        require(top.keys.all { it in TOP_KEYS }) { "unknown top-level key" }
        val intents = top["intents"] as? JsonArray ?: fail("intents must be an array")
        val rules = intents.map { rule(it) }
        require(rules.map { it.id }.toSet().size == rules.size) { "duplicate intent id" }
        return rules
    }

    private fun rule(e: JsonElement): IntentRule {
        val o = e as? JsonObject ?: fail("intent must be an object")
        require(o.keys.all { it in KEYS }) { "unknown intent key" }
        val status = str(o, "status")
        require(status in RuleStatus.entries.map { it.name }) { "bad status" }
        return IntentRule(
            id = str(o, "id"),
            avoid = list(o, "avoid").map { n ->
                WindowKind.entries.firstOrNull { it.name == n } ?: fail("unknown window kind")
            }.toSet(),
            prefer = list(o, "prefer").toSet(),
            tithiAvoid = ints(o, "tithiAvoid", 1..TITHIS),
            vaarPrefer = list(o, "vaarPrefer").map { n ->
                Vaar.entries.firstOrNull { it.name == n } ?: fail("unknown vaar")
            }.toSet(),
            nakshatraPrefer = ints(o, "nakshatraPrefer", 0 until NAKSHATRAS),
            status = RuleStatus.valueOf(status),
            sourceId = str(o, "sourceId"),
        )
    }

    private fun fail(message: String, cause: Throwable? = null): Nothing =
        throw IllegalArgumentException(message, cause)

    private fun str(o: JsonObject, k: String): String {
        val p = o[k] as? JsonPrimitive
        require(p != null && p.isString && p.content.isNotBlank()) { "missing or empty $k" }
        return p.content
    }

    private fun items(o: JsonObject, k: String): List<JsonElement> = when (val v = o[k]) {
        null -> emptyList()
        is JsonArray -> v
        else -> fail("$k must be an array")
    }

    private fun list(o: JsonObject, k: String): List<String> = items(o, k).map {
        val p = it as? JsonPrimitive
        require(p != null && p.isString) { "$k must hold strings" }
        p.content
    }

    private fun ints(o: JsonObject, k: String, range: IntRange): Set<Int> = items(o, k).map {
        val n = (it as? JsonPrimitive)?.intOrNull
        require(n != null && n in range) { "$k must hold integers in range" }
        n
    }.toSet()
}
