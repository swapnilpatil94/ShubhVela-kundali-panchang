package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The whole family as one document (snapshot and export plaintext). Format `{"v":1,...}`. */
internal data class FamilyDocument(
    val profiles: List<Profile>,
    val places: List<SavedPlace>,
    val bonds: List<Bond>,
    val settings: AppSettings,
) {
    override fun toString(): String = "FamilyDocument(redacted)"

    fun toJson(): JsonObject =
        obj(
            "v" to num(VERSION),
            "profiles" to JsonArray(profiles.map(ProfileCodec::toJson)),
            "places" to JsonArray(places.map(PlaceCodec::toJson)),
            "bonds" to JsonArray(bonds.map(BondCodec::toJson)),
            "settings" to SettingsCodec.toJson(settings),
        )

    fun toBytes(): ByteArray = PayloadBytes.encode(toJson())

    companion object {
        const val VERSION = 1

        fun fromBytes(bytes: ByteArray): FamilyDocument =
            fromJson(parseJson(bytes))

        fun fromJson(o: kotlinx.serialization.json.JsonElement): FamilyDocument =
            guarded {
                read(o) {
                    check(int("v") == VERSION)
                    val profiles = array("profiles")
                    val places = array("places")
                    val bonds = array("bonds")
                    check(profiles.size <= DataLimits.MAX_PROFILES)
                    check(places.size <= DataLimits.MAX_PLACES)
                    check(bonds.size <= DataLimits.MAX_BONDS)
                    val doc =
                        FamilyDocument(
                            profiles.map(ProfileCodec::fromJson),
                            places.map(PlaceCodec::fromJson),
                            bonds.map(BondCodec::fromJson),
                            SettingsCodec.fromJson(objectAt("settings")),
                        )
                    check(doc.profiles.count { it.relation == Relation.SELF } <= 1)
                    check(doc.profiles.map { it.id }.toSet().size == doc.profiles.size)
                    check(doc.places.map { it.id }.toSet().size == doc.places.size)
                    check(doc.bonds.map { it.id }.toSet().size == doc.bonds.size)
                    doc
                }
            }
    }
}
