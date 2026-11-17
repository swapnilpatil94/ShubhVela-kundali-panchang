package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.model.profile.RecordId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Bond rows. M12 keys (`vs`, `profileRef`, `pv`) are optional so rows stored before M12 still load (null or 1). */
internal object BondCodec {
    private val M12_TRAIT_KEYS = setOf("vs")
    private val M12_BOND_KEYS = setOf("profileRef", "pv")

    /** Omits M12 keys at their pre-M12 value, so a pre-M12 bond encodes byte-identically to before (export vectors). */
    private fun objSkipping(vararg fields: Pair<String, JsonElement?>): JsonObject =
        JsonObject(linkedMapOf(*fields.mapNotNull { (k, v) -> v?.let { k to it } }.toTypedArray()))

    private fun traitsToJson(t: BondTraits): JsonObject =
        objSkipping(
            "name" to str(t.name),
            "mn" to num(t.moonNakshatra),
            "mp" to num(t.moonPada),
            "mr" to num(t.moonRashi),
            "lg" to num(t.lagna),
            "mars" to num(t.manglik),
            "ven" to num(t.venusSign),
            "sun" to num(t.sunSign),
            "gana" to num(t.gana),
            "yoni" to num(t.yoni),
            "nadi" to num(t.nadi),
            "vs" to t.vashya?.let { num(it) },
        )

    private fun traitsFromJson(o: JsonElement): BondTraits =
        read(o, optional = M12_TRAIT_KEYS) {
            BondTraits(
                name = string("name"),
                moonNakshatra = int("mn"),
                moonPada = int("mp"),
                moonRashi = int("mr"),
                lagna = intOrNull("lg"),
                manglik = int("mars"),
                venusSign = int("ven"),
                sunSign = int("sun"),
                gana = intOrNull("gana"),
                yoni = intOrNull("yoni"),
                nadi = intOrNull("nadi"),
                vashya = intOrNull("vs"),
            )
        }

    fun toJson(b: Bond): JsonObject =
        objSkipping(
            "id" to str(b.id.hex),
            "traits" to traitsToJson(b.traits),
            "kind" to str(b.kindKey),
            "createdAt" to instant(b.createdAt),
            "profileRef" to b.profileRef?.let { str(it.hex) },
            "pv" to b.payloadVersion.takeIf { it != 1 }?.let { num(it) },
        )

    fun fromJson(o: JsonElement): Bond =
        guarded {
            read(o, optional = M12_BOND_KEYS) {
                Bond(
                    id = RecordId(string("id")),
                    traits = traitsFromJson(objectAt("traits")),
                    kindKey = string("kind"),
                    createdAt = instant("createdAt"),
                    profileRef = stringOrNull("profileRef")?.let { RecordId(it) },
                    payloadVersion = intOrNull("pv") ?: 1,
                )
            }
        }
}
