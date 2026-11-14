package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.model.profile.RecordId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal object BondCodec {
    private fun traitsToJson(t: BondTraits): JsonObject =
        obj(
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
        )

    private fun traitsFromJson(o: JsonElement): BondTraits =
        read(o) {
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
            )
        }

    fun toJson(b: Bond): JsonObject =
        obj(
            "id" to str(b.id.hex),
            "traits" to traitsToJson(b.traits),
            "kind" to str(b.kindKey),
            "createdAt" to instant(b.createdAt),
        )

    fun fromJson(o: JsonElement): Bond =
        guarded {
            read(o) {
                Bond(
                    id = RecordId(string("id")),
                    traits = traitsFromJson(objectAt("traits")),
                    kindKey = string("kind"),
                    createdAt = instant("createdAt"),
                )
            }
        }
}
