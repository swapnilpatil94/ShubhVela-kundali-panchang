package app.parivaar.panchang.content.pack

import app.parivaar.panchang.content.crypto.Ed25519
import app.parivaar.panchang.content.text.TermKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Where a pack came from; decides which keys are trusted ([TrustedKeys.forSource]). */
enum class PackSource { BUNDLED, REMOTE }

/** The outcome of [PackLoader.load]. Failures carry no input bytes; [SchemaInvalid.reason] is a fixed phrase. */
sealed interface PackResult {
    data class Ok(val pack: ContentPack) : PackResult

    data object TooLarge : PackResult

    data object Malformed : PackResult

    data object UnsupportedVersion : PackResult

    data object UntrustedKey : PackResult

    data object SignatureInvalid : PackResult

    data class SchemaInvalid(val reason: String) : PackResult
}

/**
 * Verifies and decodes signed content packs (docs/specs/M11.md section 3 "Load order"). Nothing inside the payload
 * is parsed before the signature has been checked: size, envelope, key id, Ed25519, then JSON, then schema.
 */
class PackLoader internal constructor(
    private val keys: TrustedKeys,
    private val decode: (ByteArray) -> JsonElement,
) {
    constructor(keys: TrustedKeys) : this(keys, ::decodeJson)

    /** Loads [bytes] as the pack for [lang] from [source]. Never throws for any input. */
    @Suppress("ReturnCount") // guard clauses mirror the RFC/spec rejection steps
    fun load(bytes: ByteArray, lang: String, source: PackSource): PackResult {
        if (bytes.size > PackEnvelope.MAX_BYTES) return PackResult.TooLarge
        val env = PackEnvelope.parse(bytes).getOrElse {
            return when ((it as? PackEnvelope.Failure)?.kind) {
                PackEnvelope.Failure.Kind.UNSUPPORTED_VERSION -> PackResult.UnsupportedVersion
                else -> PackResult.Malformed
            }
        }
        val key = keys.forSource(source).firstOrNull {
            it.size == KEY_BYTES && Ed25519.constantTimeEquals(PackEnvelope.keyIdOf(it), env.keyId)
        } ?: return PackResult.UntrustedKey
        if (!Ed25519.verify(key, env.signed, env.signature)) return PackResult.SignatureInvalid
        val root = runCatching { decode(env.payload) }.getOrElse { return PackResult.SchemaInvalid(R_JSON) }
        return Schema.read(root, lang)
    }

    /** Schema 1 (docs/specs/M11.md section 3 "Payload JSON"); every failure is a fixed, input-free reason. */
    private object Schema {
        private val ID = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
        private val FIELDS = setOf("schema", "lang", "version", "entries", "terms")
        private const val MAX_TEXT = 4096

        @Suppress("ReturnCount") // guard clauses mirror the RFC/spec rejection steps
        fun read(root: JsonElement, lang: String): PackResult {
            val o = root as? JsonObject ?: return invalid("payload is not an object")
            if (!FIELDS.containsAll(o.keys)) return invalid("unknown field")
            if (int(o["schema"]) != 1) return invalid("schema is not 1")
            if (string(o["lang"]) != lang) return invalid("lang does not match")
            val version = int(o["version"])
            if (version == null || version < 1) return invalid("bad version")
            val entriesJson = o["entries"] as? JsonObject ?: return invalid("entries missing")
            val entries = readEntries(entriesJson) ?: return invalid("bad id or text")
            val terms = when (val t = o["terms"]) {
                null -> Terms.EMPTY
                is JsonObject -> readTerms(t) ?: return invalid("bad terms")
                else -> return invalid("bad terms")
            }
            return PackResult.Ok(ContentPack(lang, version, entries, terms))
        }

        private fun readEntries(o: JsonObject): Map<String, String>? {
            val entries = LinkedHashMap<String, String>(o.size)
            for ((id, v) in o) entries[id] = text(v)?.takeIf { ID.matches(id) } ?: return null
            return entries
        }

        @Suppress("ReturnCount") // guard clauses mirror the RFC/spec rejection steps
        private fun readTerms(t: JsonObject): Terms? {
            val byKind = LinkedHashMap<TermKind, List<String>>()
            for ((k, v) in t) {
                val kind = TermKind.ofKey(k) ?: return null
                val arr = v as? JsonArray ?: return null
                if (arr.size != kind.count) return null
                byKind[kind] = arr.map { text(it) ?: return null }
            }
            return Terms(byKind)
        }

        @Suppress("ReturnCount") // guard clauses mirror the RFC/spec rejection steps
        private fun text(e: JsonElement?): String? {
            val p = e as? JsonPrimitive ?: return null
            if (!p.isString || p.content.isEmpty() || p.content.length > MAX_TEXT) return null
            return p.content
        }

        private fun string(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun int(e: JsonElement?): Int? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

        private fun invalid(reason: String) = PackResult.SchemaInvalid(reason)
    }

    private companion object {
        const val KEY_BYTES = 32
        const val R_JSON = "payload is not valid JSON"

        /** Strict UTF-8 then the JSON tree API (no compiler plugin, M11 D2). */
        fun decodeJson(payload: ByteArray): JsonElement =
            Json.parseToJsonElement(payload.decodeToString(throwOnInvalidSequence = true))
    }
}
