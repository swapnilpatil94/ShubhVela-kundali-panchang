@file:Suppress("MaxLineLength")

package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsCodecM08Test {
    private val keys = setOf("showCityOnWidgets", "travelMode")
    private val full = AppSettings(showCityOnWidgets = true, travelMode = true, localeTag = "hi")

    private fun mutate(f: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): JsonObject {
        val m = SettingsCodec.toJson(full).toMutableMap()
        f(m)
        return JsonObject(m)
    }

    @Test
    fun defaultsAreOff() {
        assertFalse(AppSettings().showCityOnWidgets)
        assertFalse(AppSettings().travelMode)
    }

    @Test
    fun roundTrip() {
        assertEquals(full, SettingsCodec.fromJson(SettingsCodec.toJson(full)))
        assertEquals(AppSettings(), SettingsCodec.fromJson(SettingsCodec.toJson(AppSettings())))
    }

    @Test
    fun missingKeysDefault() {
        val s = SettingsCodec.fromJson(mutate { it.keys.removeAll(keys) })
        assertFalse(s.showCityOnWidgets)
        assertFalse(s.travelMode)
        for (k in keys) {
            val one = SettingsCodec.fromJson(mutate { it.remove(k) })
            assertTrue(one != full, k)
        }
    }

    @Test
    fun unknownRejectedAndBadTypeRejected() {
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["extra"] = JsonPrimitive(true) }) }
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["travelMode"] = JsonPrimitive("yes") }) }
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["showCityOnWidgets"] = JsonPrimitive(1) }) }
    }
}
