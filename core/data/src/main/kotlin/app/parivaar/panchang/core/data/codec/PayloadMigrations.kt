package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.profile.FamilyPrefs
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Upgrades stored payloads written under an older payload version. [steps] maps (table, fromVersion) to a step that
 * produces fromVersion + 1. A version above [current], below 1 or with a missing step is rejected.
 */
internal class PayloadMigrations(
    private val steps: Map<Pair<Int, Int>, (JsonObject) -> JsonObject> = V1_TO_V2,
    val current: Int = CURRENT_VERSION,
) {
    fun migrate(tbl: Int, fromVersion: Int, payload: JsonObject): JsonObject {
        check(fromVersion in 0..current)
        var v = fromVersion
        var o = payload
        while (v < current) {
            val step = steps[tbl to v] ?: fail()
            o = step(o)
            v++
        }
        return o
    }

    companion object {
        const val CURRENT_VERSION = 2

        /**
         * M10: v1 -> v2. Profiles gain `passing = null` and default `familyPrefs`; the other tables are unchanged
         * (their payload format did not change, only the shared version number).
         */
        val V1_TO_V2: Map<Pair<Int, Int>, (JsonObject) -> JsonObject> =
            mapOf(
                (Tbl.PROFILE to 1) to { o ->
                    check("passing" !in o && "familyPrefs" !in o)
                    JsonObject(
                        o + ("passing" to JsonNull) + ("familyPrefs" to ProfileCodec.prefsToJson(FamilyPrefs())),
                    )
                },
                (Tbl.PLACE to 1) to { o -> o },
                (Tbl.BOND to 1) to { o -> o },
                (Tbl.SETTINGS to 1) to { o -> o },
            )

        val DEFAULT = PayloadMigrations()
    }
}

/** Row payload (de)serialisation: UTF-8 JSON, migrated to the current version on read. */
internal object PayloadBytes {
    fun encode(o: JsonObject): ByteArray = o.toString().encodeToByteArray()

    fun decode(
        bytes: ByteArray,
        tbl: Int,
        ver: Int,
        migrations: PayloadMigrations = PayloadMigrations.DEFAULT,
    ): JsonObject =
        guarded {
            val parsed = parseJson(bytes)
            migrations.migrate(tbl, ver, parsed as? JsonObject ?: fail())
        }
}
