package app.parivaar.panchang.core.data.codec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/** Any undecodable or invalid payload. The message is static and never carries data. */
class CodecException : Exception("Invalid data")

/** Size limits shared by the codecs and the repositories (M06 spec section 5). */
internal object DataLimits {
    const val NAME_MAX = 60
    const val NOTES_MAX = 2000
    const val MAX_PROFILES = 500
    const val MAX_PLACES = 2000
    const val MAX_BONDS = 2000
    const val LOCALE_MAX = 35

    // Stored and imported places (Fable review item 2): anything outside these bounds is a bad row.
    const val PLACE_NAME_MAX = 200
    const val ADMIN1_MAX = 200
    const val OVERLAY_KEY_MAX = 64
    const val POPULATION_MAX = 2_000_000_000L
    const val ELEVATION_MIN_M = -1_000.0
    const val ELEVATION_MAX_M = 9_000.0
    val COUNTRY_CODE = Regex("[A-Z]{2}")

    // Birth dates the app accepts (MASTER Part 3 precision range 1900-2100, with margin for elders and planning).
    const val BIRTH_YEAR_MIN = 1800
    const val BIRTH_YEAR_MAX = 2200
}

internal val PpJson = Json

/** Strict UTF-8 parse of a JSON document; any malformed input becomes [CodecException]. */
internal fun parseJson(bytes: ByteArray): JsonElement =
    try {
        PpJson.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true))
    } catch (_: kotlinx.serialization.SerializationException) {
        throw CodecException()
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw CodecException()
    }

internal fun fail(): Nothing = throw CodecException()

internal fun check(ok: Boolean) {
    if (!ok) fail()
}

/** Runs [block], mapping model validation failures to [CodecException]. */
internal inline fun <T> guarded(block: () -> T): T =
    try {
        block()
    } catch (_: CodecException) {
        fail()
    } catch (_: IllegalArgumentException) {
        fail()
    } catch (_: ClassCastException) {
        fail()
    }

internal fun obj(vararg fields: Pair<String, JsonElement>): JsonObject = JsonObject(linkedMapOf(*fields))

internal fun str(v: String?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)

internal fun num(v: Number?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)

internal fun bool(v: Boolean): JsonElement = JsonPrimitive(v)

internal fun instant(v: Instant?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v.toString())

internal fun <E : Enum<E>> enumName(v: E): JsonElement = JsonPrimitive(v.name)

/** Strict object reader: every key must be read exactly once and no unknown key may remain. */
@Suppress("TooManyFunctions")
internal class Reader(o: JsonElement, private val optionalKeys: Set<String> = emptySet()) {
    private val node: JsonObject = o as? JsonObject ?: fail()
    private val seen = HashSet<String>()

    private fun raw(key: String): JsonElement? {
        seen += key
        val v = node[key]
        if (v == null && key !in optionalKeys) fail()
        return v
    }

    private fun prim(key: String): JsonPrimitive? {
        val v = raw(key)
        return if (v == null || v is JsonNull) null else v as? JsonPrimitive ?: fail()
    }

    fun string(key: String): String {
        val p = prim(key) ?: fail()
        check(p.isString)
        return p.content
    }

    fun stringOrNull(key: String): String? {
        val p = prim(key) ?: return null
        check(p.isString)
        return p.content
    }

    fun int(key: String): Int = prim(key)?.takeIf { !it.isString }?.intOrNull ?: fail()

    fun intOrNull(key: String): Int? {
        val p = prim(key) ?: return null
        return p.takeIf { !it.isString }?.intOrNull ?: fail()
    }

    fun long(key: String): Long = prim(key)?.takeIf { !it.isString }?.longOrNull ?: fail()

    fun double(key: String): Double {
        val d = prim(key)?.takeIf { !it.isString }?.doubleOrNull ?: fail()
        check(d.isFinite())
        return d
    }

    fun doubleOrNull(key: String): Double? {
        val p = prim(key) ?: return null
        val d = p.takeIf { !it.isString }?.doubleOrNull ?: fail()
        check(d.isFinite())
        return d
    }

    fun boolean(key: String): Boolean = prim(key)?.takeIf { !it.isString }?.booleanOrNull ?: fail()

    fun instant(key: String): Instant = guarded { Instant.parse(string(key)) }

    fun instantOrNull(key: String): Instant? = stringOrNull(key)?.let { s -> guarded { Instant.parse(s) } }

    fun objectAt(key: String): JsonObject = raw(key) as? JsonObject ?: fail()

    fun objectOrNull(key: String): JsonObject? {
        val v = raw(key)
        return if (v == null || v is JsonNull) null else v as? JsonObject ?: fail()
    }

    fun array(key: String): JsonArray = raw(key) as? JsonArray ?: fail()

    fun <E : Enum<E>> enumValue(key: String, all: List<E>): E {
        val s = string(key)
        return all.firstOrNull { it.name == s } ?: fail()
    }

    /** Throws if the object holds any key that was never read. */
    fun done() {
        check(node.keys.all { it in seen })
    }
}

internal inline fun <T> read(o: JsonElement, optional: Set<String> = emptySet(), block: Reader.() -> T): T {
    val r = Reader(o, optional)
    val v = r.block()
    r.done()
    return v
}
