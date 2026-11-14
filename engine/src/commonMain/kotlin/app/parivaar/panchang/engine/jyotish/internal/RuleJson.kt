package app.parivaar.panchang.engine.jyotish.internal

import app.parivaar.panchang.engine.jyotish.RuleFormatException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Typed readers over the kotlinx-serialization tree API (docs/specs/M02.md sections 3.6 to 3.8). Every failure
 * is a [RuleFormatException] that names a code and a JSON path, never a value.
 */
@Suppress("TooManyFunctions") // one small reader per JSON type; splitting would scatter the call sites
internal object RuleJson {
    /** Longest rule file accepted, in characters. */
    const val MAX_CHARS = 262_144

    fun fail(code: String, path: String): Nothing = throw RuleFormatException(code, path)

    /** Parses [text] (at most [MAX_CHARS] characters) into a tree; the root path is `$`. */
    @Suppress("SwallowedException") // the parser message can quote input, so it is dropped on purpose
    fun parse(text: String): JsonElement {
        if (text.length > MAX_CHARS) fail("TOO_LARGE", "$")
        return try {
            Json.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) { // SerializationException is one
            fail("BAD_JSON", "$")
        }
    }

    fun obj(e: JsonElement, path: String): JsonObject = e as? JsonObject ?: fail("NOT_OBJECT", path)

    fun arr(e: JsonElement, path: String): JsonArray = e as? JsonArray ?: fail("NOT_ARRAY", path)

    private fun prim(e: JsonElement, path: String): JsonPrimitive = e as? JsonPrimitive ?: fail("BAD_TYPE", path)

    fun str(e: JsonElement, path: String): String {
        val p = prim(e, path)
        return if (p.isString) p.content else fail("BAD_TYPE", path)
    }

    fun nonBlank(e: JsonElement, path: String): String = str(e, path).also { if (it.isBlank()) fail("BLANK", path) }

    fun int(e: JsonElement, path: String): Int {
        val p = prim(e, path)
        return (if (p.isString) null else p.content.toIntOrNull()) ?: fail("BAD_TYPE", path)
    }

    fun double(e: JsonElement, path: String): Double {
        val p = prim(e, path)
        val v = (if (p.isString) null else p.content.toDoubleOrNull()) ?: fail("BAD_TYPE", path)
        return if (v.isFinite()) v else fail("BAD_TYPE", path)
    }

    fun intIn(e: JsonElement, path: String, range: IntRange): Int =
        int(e, path).also { if (it !in range) fail("OUT_OF_RANGE", path) }

    /** The value of the required key [key] of [o]; [path] is the path of [o]. */
    fun field(o: JsonObject, key: String, path: String): JsonElement = o[key] ?: fail("MISSING_FIELD", "$path.$key")

    /** Fails with UNKNOWN_KEY unless every key of [o] is in [allowed]. */
    fun onlyKeys(o: JsonObject, allowed: Set<String>, path: String) {
        o.keys.firstOrNull { it !in allowed }?.let { fail("UNKNOWN_KEY", "$path.$it") }
    }

    /** [value] must be one of [allowed]. */
    fun oneOf(value: String, allowed: Set<String>, path: String): String =
        value.also { if (it !in allowed) fail("BAD_VALUE", path) }
}
