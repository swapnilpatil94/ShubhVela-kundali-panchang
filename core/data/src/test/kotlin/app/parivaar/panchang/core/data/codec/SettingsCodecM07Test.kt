package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.core.model.settings.ChartStyle
import app.parivaar.panchang.core.model.settings.PresetSource
import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.core.model.settings.ThemeMode
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsCodecM07Test {
    private val newKeys =
        setOf(
            "homePlaceId", "monthSystem", "presetSource", "ekadashi", "chartStyle", "themeMode",
            "dynamicColor", "badaText", "reduceMotion", "reminders", "onboardingDone",
        )
    private val full =
        AppSettings(
            selfProfileId = FamilyFixture.id(1),
            localeTag = "hi",
            privacyScreen = true,
            homePlaceId = FamilyFixture.id(2),
            monthSystem = MonthSystem.PURNIMANTA,
            presetSource = PresetSource.NOT_SURE_DEFAULT,
            ekadashi = Tradition.VAISHNAVA,
            chartStyle = ChartStyle.EAST,
            themeMode = ThemeMode.DARK,
            dynamicColor = true,
            badaText = true,
            reduceMotion = true,
            reminders = setOf(ReminderKind.SUNRISE_BRAHMA, ReminderKind.VRAT_EVE),
            onboardingDone = true,
        )

    private fun mutate(f: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): JsonObject {
        val m = SettingsCodec.toJson(full).toMutableMap()
        f(m)
        return JsonObject(m)
    }

    @Test
    fun fullRoundTrip() {
        assertEquals(full, SettingsCodec.fromJson(SettingsCodec.toJson(full)))
        assertEquals(AppSettings(), SettingsCodec.fromJson(SettingsCodec.toJson(AppSettings())))
    }

    @Test
    fun missingNewKeysReadAsDefaults() {
        val old = mutate { m -> m.keys.removeAll(newKeys) }
        val s = SettingsCodec.fromJson(old)
        assertEquals(AppSettings(localeTag = "hi", privacyScreen = true, selfProfileId = full.selfProfileId), s)
    }

    @Test
    fun eachNewKeyMayBeMissingAlone() {
        for (k in newKeys) {
            val s = SettingsCodec.fromJson(mutate { it.remove(k) })
            assert(s != full) { k }
        }
    }

    @Test
    fun unknownKeyRejected() {
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["extra"] = JsonPrimitive(1) }) }
    }

    @Test
    fun invalidValuesRejected() {
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["themeMode"] = JsonPrimitive("PINK") }) }
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["badaText"] = JsonPrimitive("yes") }) }
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it["reminders"] = JsonPrimitive("GRAHAN") }) }
        assertFailsWith<CodecException> {
            SettingsCodec.fromJson(mutate { it["reminders"] = JsonArray(listOf(JsonPrimitive("NOPE"))) })
        }
        assertFailsWith<CodecException> {
            SettingsCodec.fromJson(
                mutate { it["reminders"] = JsonArray(listOf(JsonPrimitive("GRAHAN"), JsonPrimitive("GRAHAN"))) },
            )
        }
    }

    @Test
    fun missingRequiredOldKeyStillRejected() {
        assertFailsWith<CodecException> { SettingsCodec.fromJson(mutate { it.remove("privacyScreen") }) }
    }

    @Test
    fun reminderOrderIsCanonical() {
        val a = SettingsCodec.toJson(full.copy(reminders = setOf(ReminderKind.GRAHAN, ReminderKind.VRAT_EVE)))
        val b = SettingsCodec.toJson(full.copy(reminders = setOf(ReminderKind.VRAT_EVE, ReminderKind.GRAHAN)))
        assertEquals(a, b)
    }

    @Test
    fun storedDisabledAyanamsaFallsBackToLahiri() {
        for (id in listOf("KP_NEW", "RAMAN", "YUKTESHWAR")) {
            val j = SettingsCodec.toJson(full)
            val calc = JsonObject((j["defaultCalc"] as JsonObject) + ("ayanamsa" to JsonPrimitive(id)))
            val s = SettingsCodec.fromJson(JsonObject(j + ("defaultCalc" to calc)))
            assertEquals(app.parivaar.panchang.engine.jyotish.AyanamsaId.LAHIRI, s.defaultCalc.ayanamsa, id)
        }
    }
}
