package app.parivaar.panchang.core.geo.internal

import app.parivaar.panchang.core.model.geo.ManualOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** One historical offset range for an overlay key. [offsetSeconds] null = no data (falls through to IANA). */
data class OverlayEntry(
    val id: String,
    val key: String,
    val offsetSeconds: Int?,
    val fromEpoch: Long,
    val untilEpoch: Long,
    val status: String,
    val source: String,
)

/** Parsed `rules/tz_overlay.json` (spec M05 section 4.7). Immutable. */
class TzOverlay private constructor(
    entries: List<OverlayEntry>,
    val verifyZones: Set<String>,
    val verifyUntilEpoch: Long,
) {
    val entries: List<OverlayEntry> = entries.toList()
    private val byKey: Map<String, List<OverlayEntry>> =
        entries.groupBy { it.key }.mapValues { (_, v) -> v.sortedBy { it.fromEpoch } }

    val keys: Set<String> get() = byKey.keys

    fun find(key: String?, epochSecond: Long): OverlayEntry? {
        if (key == null) return null
        return byKey[key]?.firstOrNull { epochSecond >= it.fromEpoch && epochSecond < it.untilEpoch }
    }

    companion object {
        /** Throws [IllegalArgumentException] (message without data values) on any schema violation. */
        fun parse(json: String): TzOverlay {
            val root = parseObject(json)
            val schema = root["schema"]?.jsonPrimitive?.int
            require(schema == 1) { "Unsupported overlay schema" }
            val verifyZones = requireNotNull(root["verifyZones"] as? JsonArray) { "verifyZones missing" }
                .map { it.jsonPrimitive.content }.toSet()
            val verifyUntil = instant(root["verifyUntilUtc"]?.jsonPrimitive?.content)
            val list = requireNotNull(root["entries"] as? JsonArray) { "entries missing" }.map { e ->
                val o = e.jsonObject
                val off = o["offsetSeconds"]
                val offset = if (off == null || off is JsonNull) null else off.jsonPrimitive.int
                OverlayEntry(
                    id = text(o, "id"),
                    key = text(o, "key"),
                    offsetSeconds = offset,
                    fromEpoch = instant(text(o, "fromUtc")),
                    untilEpoch = instant(text(o, "untilUtc")),
                    status = text(o, "status"),
                    source = text(o, "source"),
                )
            }
            validate(list)
            return TzOverlay(list, verifyZones, verifyUntil)
        }

        private fun parseObject(json: String): JsonObject =
            try {
                Json.parseToJsonElement(json).jsonObject
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Overlay is not valid JSON", e)
            }

        private fun text(o: JsonObject, name: String): String =
            requireNotNull(o[name]?.jsonPrimitive?.content) { "Overlay field missing" }

        private fun instant(s: String?): Long =
            try {
                Instant.parse(requireNotNull(s) { "Overlay instant missing" }).epochSecond
            } catch (e: java.time.DateTimeException) {
                throw IllegalArgumentException("Overlay instant invalid", e)
            }

        private fun validate(list: List<OverlayEntry>) {
            require(list.map { it.id }.toSet().size == list.size) { "Duplicate overlay id" }
            for (e in list) {
                require(e.fromEpoch < e.untilEpoch) { "Overlay range empty or reversed" }
                e.offsetSeconds?.let {
                    require(it in ManualOffset.MIN_SECONDS..ManualOffset.MAX_SECONDS) { "Overlay offset out of range" }
                }
            }
            for ((_, group) in list.groupBy { it.key }) {
                val sorted = group.sortedBy { it.fromEpoch }
                for (i in 1 until sorted.size) {
                    require(sorted[i].fromEpoch >= sorted[i - 1].untilEpoch) { "Overlay ranges overlap" }
                }
            }
        }
    }
}
