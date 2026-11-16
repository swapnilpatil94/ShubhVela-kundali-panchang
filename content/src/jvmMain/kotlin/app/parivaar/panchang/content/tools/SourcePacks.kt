package app.parivaar.panchang.content.tools

import app.parivaar.panchang.content.pack.ContentPack
import app.parivaar.panchang.content.pack.Terms
import app.parivaar.panchang.content.text.TermKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * One source pack exactly as written in `content-src` (build/CI only). Text is kept raw (not normalised) so lint L7
 * can check it; term arrays are kept even when of the wrong size so lint L8 can report them.
 */
data class SourcePack(
    val lang: String,
    val schema: Int,
    val version: Int,
    val entries: Map<String, String>,
    val terms: Map<String, List<String>>,
) {
    /** The pack as the app sees it; term arrays of the wrong size or unknown kind are dropped. */
    fun toContentPack(): ContentPack {
        val byKind = terms.mapNotNull { (k, v) -> TermKind.ofKey(k)?.takeIf { v.size == it.count }?.let { it to v } }
        return ContentPack(lang, version, entries, Terms(byKind.toMap()))
    }
}

/** Reads and writes the content-src layout: `<lang>/pack.json`, `terms/<lang>.json`, `lint/banned_<lang>.txt`. */
object SourcePacks {
    /** Bundled languages (M11: en, hi with text; mr, gu empty and falling back to English). */
    val BUNDLED: List<String> = listOf("en", "hi", "mr", "gu")

    fun read(root: File): Map<String, SourcePack> =
        BUNDLED.filter { File(root, "$it/pack.json").isFile }.associateWith { lang ->
            val obj = Json.parseToJsonElement(File(root, "$lang/pack.json").readText()).jsonObject
            val termsFile = File(root, "terms/$lang.json")
            val terms = termsFile.takeIf { it.isFile }
                ?.let { parseTerms(Json.parseToJsonElement(it.readText()).jsonObject) }.orEmpty()
            SourcePack(
                lang = obj["lang"]?.jsonPrimitive?.content ?: "",
                schema = obj["schema"]?.jsonPrimitive?.int ?: 0,
                version = obj["version"]?.jsonPrimitive?.int ?: 0,
                entries = (obj["entries"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.content },
                terms = terms,
            )
        }

    private fun parseTerms(o: JsonObject): Map<String, List<String>> =
        o.mapValues { (_, v) -> (v as? JsonArray).orEmpty().map { it.jsonPrimitive.content } }

    /** Banned terms per language (comment lines start with `#`). */
    fun banned(root: File): Map<String, List<String>> =
        BUNDLED.associateWith { lang ->
            val f = File(root, "lint/banned_$lang.txt")
            if (!f.isFile) {
                emptyList()
            } else {
                f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            }
        }

    /** The payload JSON of docs/specs/M11.md section 3 (schema 1). Term arrays are omitted when absent. */
    fun payloadJson(p: SourcePack): String =
        buildJsonObject {
            put("schema", p.schema)
            put("lang", p.lang)
            put("version", p.version)
            put("entries", JsonObject(p.entries.mapValues { JsonPrimitive(it.value) }))
            if (p.terms.isNotEmpty()) {
                put("terms", JsonObject(p.terms.mapValues { (_, v) -> JsonArray(v.map(::JsonPrimitive)) }))
            }
        }.toString()
}
