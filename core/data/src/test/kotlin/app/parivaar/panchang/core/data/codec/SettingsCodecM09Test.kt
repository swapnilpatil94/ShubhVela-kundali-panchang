@file:Suppress("MaxLineLength")

package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.core.model.settings.CalendarView
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsCodecM09Test {
    private val full = AppSettings(calendarView = CalendarView.GUJARATI, localeTag = "gu")

    private fun mutate(f: (MutableMap<String, JsonElement>) -> Unit): JsonObject {
        val m = SettingsCodec.toJson(full).toMutableMap()
        f(m)
        return JsonObject(m)
    }

    @Test
    fun defaultIsStandard() = assertEquals(CalendarView.STANDARD, AppSettings().calendarView)

    @Test
    fun roundTripEveryView() {
        for (v in CalendarView.entries) {
            val s = AppSettings(calendarView = v)
            assertEquals(s, SettingsCodec.fromJson(SettingsCodec.toJson(s)))
        }
    }

    @Test
    fun missingKeyDefaults() {
        assertEquals(CalendarView.STANDARD, SettingsCodec.fromJson(mutate { it.remove("calendarView") }).calendarView)
    }

    @Test
    fun unknownKeyAndBadValueRejected() {
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["extra"] = JsonPrimitive(true) }) }
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["calendarView"] = JsonPrimitive("TAMIL") }) }
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["calendarView"] = JsonPrimitive(1) }) }
    }
}
