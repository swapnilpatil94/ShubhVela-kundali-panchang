package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.profile.PlaceSnapshot
import app.parivaar.panchang.core.model.profile.RecordId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Codec for [SavedPlace] and the geo parts shared with profiles. Instances hold no state. */
internal object PlaceCodec {
    private const val MAX_LAT = 90.0
    private const val MAX_LON = 180.0

    fun geoToJson(p: GeoPlace): JsonObject =
        obj(
            "id" to num(p.id),
            "name" to str(p.name),
            "admin1" to str(p.admin1),
            "cc" to str(p.countryCode),
            "lat" to num(p.latDeg),
            "lon" to num(p.lonDeg),
            "elev" to num(p.elevationM),
            "pop" to num(p.population),
            "zone" to str(p.zoneId),
            "overlay" to str(p.overlayKey),
        )

    fun geoFromJson(o: JsonElement): GeoPlace =
        guarded {
            read(o) {
                val lat = double("lat")
                val lon = double("lon")
                check(lat in -MAX_LAT..MAX_LAT && lon in -MAX_LON..MAX_LON)
                GeoPlace(
                    id = long("id"),
                    name = string("name"),
                    admin1 = stringOrNull("admin1"),
                    countryCode = string("cc"),
                    latDeg = lat,
                    lonDeg = lon,
                    elevationM = doubleOrNull("elev"),
                    population = long("pop"),
                    zoneId = string("zone"),
                    overlayKey = stringOrNull("overlay"),
                )
            }
        }

    private fun offsetToJson(o: ResolvedOffset): JsonObject =
        obj(
            "sec" to num(o.offsetSeconds),
            "src" to enumName(o.source),
            "flags" to JsonArray(o.flags.map { it.name }.sorted().map(::JsonPrimitive)),
            "overlayId" to str(o.overlayId),
        )

    private fun offsetFromJson(o: JsonElement): ResolvedOffset =
        read(o) {
            val names = array("flags").map { e -> (e as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail() }
            val flags = names.map { n -> OffsetFlag.entries.firstOrNull { it.name == n } ?: fail() }
            check(flags.toSet().size == flags.size)
            ResolvedOffset(
                offsetSeconds = int("sec"),
                source = enumValue("src", OffsetSource.entries),
                flags = flags.toSet(),
                overlayId = stringOrNull("overlayId"),
            )
        }

    fun snapshotToJson(s: PlaceSnapshot): JsonObject =
        obj(
            "place" to geoToJson(s.place),
            "offset" to offsetToJson(s.offset),
            "manual" to num(s.manualOffset?.seconds),
            "ambiguity" to enumName(s.ambiguity),
            "birthInstant" to instant(s.birthInstant),
        )

    fun snapshotFromJson(o: JsonElement): PlaceSnapshot =
        guarded {
            read(o) {
                PlaceSnapshot(
                    place = geoFromJson(objectAt("place")),
                    offset = offsetFromJson(objectAt("offset")),
                    manualOffset = intOrNull("manual")?.let(::ManualOffset),
                    ambiguity = enumValue("ambiguity", AmbiguityChoice.entries),
                    birthInstant = instantOrNull("birthInstant"),
                )
            }
        }

    fun toJson(p: SavedPlace): JsonObject =
        obj(
            "id" to str(p.id.hex),
            "place" to geoToJson(p.place),
            "pinned" to bool(p.pinned),
            "lastUsedAt" to instant(p.lastUsedAt),
        )

    fun fromJson(o: JsonElement): SavedPlace =
        guarded {
            read(o) {
                SavedPlace(
                    id = RecordId(string("id")),
                    place = geoFromJson(objectAt("place")),
                    pinned = boolean("pinned"),
                    lastUsedAt = instant("lastUsedAt"),
                )
            }
        }
}
