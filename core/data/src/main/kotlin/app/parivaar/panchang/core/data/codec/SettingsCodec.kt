package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.HighLatitudeRule
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.core.model.settings.ChartStyle
import app.parivaar.panchang.core.model.settings.PresetSource
import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.core.model.settings.ThemeMode
import app.parivaar.panchang.engine.SunriseConvention
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.DashaYear
import app.parivaar.panchang.engine.jyotish.DrekkanaVariant
import app.parivaar.panchang.engine.jyotish.HoraVariant
import app.parivaar.panchang.engine.jyotish.HouseSystem
import app.parivaar.panchang.engine.jyotish.NodeType
import app.parivaar.panchang.engine.panchang.TimingStyle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Codec for [CalcSettings] (all 12 fields; a missing field reads as its default) and [AppSettings]. */
internal object SettingsCodec {
    private val calcKeys =
        setOf(
            "sunrise", "highLat", "pressure", "temp", "ayanamsa", "node", "house",
            "dashaYear", "hora", "drekkana", "timing", "abhijitWed",
        )

    fun calcToJson(c: CalcSettings): JsonObject =
        obj(
            "sunrise" to enumName(c.sunrise),
            "highLat" to enumName(c.highLatitudeRule),
            "pressure" to num(c.pressureHPa),
            "temp" to num(c.temperatureC),
            "ayanamsa" to enumName(c.ayanamsa),
            "node" to enumName(c.node),
            "house" to enumName(c.houseSystem),
            "dashaYear" to enumName(c.dashaYear),
            "hora" to enumName(c.hora),
            "drekkana" to enumName(c.drekkana),
            "timing" to enumName(c.timingStyle),
            "abhijitWed" to bool(c.abhijitOnWednesday),
        )

    private fun <E : Enum<E>> Reader.enumOr(present: Set<String>, key: String, all: List<E>, default: E): E =
        if (key in present) enumValue(key, all) else default

    fun calcFromJson(o: JsonElement): CalcSettings =
        guarded {
            val d = CalcSettings()
            val present = (o as? JsonObject)?.keys ?: fail()
            read(o, optional = calcKeys) {
                CalcSettings(
                    sunrise = enumOr(present, "sunrise", SunriseConvention.entries, d.sunrise),
                    highLatitudeRule = enumOr(present, "highLat", HighLatitudeRule.entries, d.highLatitudeRule),
                    pressureHPa = if ("pressure" in present) double("pressure") else d.pressureHPa,
                    temperatureC = if ("temp" in present) double("temp") else d.temperatureC,
                    ayanamsa = enumOr(present, "ayanamsa", AyanamsaId.entries, d.ayanamsa),
                    node = enumOr(present, "node", NodeType.entries, d.node),
                    houseSystem = enumOr(present, "house", HouseSystem.entries, d.houseSystem),
                    dashaYear = enumOr(present, "dashaYear", DashaYear.entries, d.dashaYear),
                    hora = enumOr(present, "hora", HoraVariant.entries, d.hora),
                    drekkana = enumOr(present, "drekkana", DrekkanaVariant.entries, d.drekkana),
                    timingStyle = enumOr(present, "timing", TimingStyle.entries, d.timingStyle),
                    abhijitOnWednesday = if ("abhijitWed" in present) boolean("abhijitWed") else d.abhijitOnWednesday,
                )
            }
        }

    fun toJson(s: AppSettings): JsonObject =
        obj(
            "defaultCalc" to calcToJson(s.defaultCalc),
            "selfProfileId" to str(s.selfProfileId?.hex),
            "locale" to str(s.localeTag),
            "privacyScreen" to bool(s.privacyScreen),
            "homePlaceId" to str(s.homePlaceId?.hex),
            "monthSystem" to enumName(s.monthSystem),
            "presetSource" to enumName(s.presetSource),
            "ekadashi" to enumName(s.ekadashi),
            "chartStyle" to enumName(s.chartStyle),
            "themeMode" to enumName(s.themeMode),
            "dynamicColor" to bool(s.dynamicColor),
            "badaText" to bool(s.badaText),
            "reduceMotion" to bool(s.reduceMotion),
            "reduceTransparency" to bool(s.reduceTransparency),
            "reminders" to JsonArray(s.reminders.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }),
            "onboardingDone" to bool(s.onboardingDone),
            "showCityOnWidgets" to bool(s.showCityOnWidgets),
            "travelMode" to bool(s.travelMode),
            "calendarView" to enumName(s.calendarView),
        )

    /** Keys added after payload v1: a missing key reads as its default; unknown keys are still rejected. */
    private val newKeys =
        setOf(
            "homePlaceId", "monthSystem", "presetSource", "ekadashi", "chartStyle", "themeMode",
            "dynamicColor", "badaText", "reduceMotion", "reduceTransparency", "reminders", "onboardingDone",
            "showCityOnWidgets", "travelMode", "calendarView",
        )

    private fun Reader.remindersOr(present: Set<String>): Set<ReminderKind> {
        if ("reminders" !in present) return emptySet()
        val names = array("reminders").map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: fail() }
        val kinds = names.map { n -> ReminderKind.entries.firstOrNull { it.name == n } ?: fail() }
        check(kinds.toSet().size == kinds.size)
        return kinds.toSet()
    }

    /**
     * The app-wide default must stay calculable: an ayanamsa the engine rejects (KP, Raman, Yukteshwar) reads as
     * Lahiri. Profile calc is left as stored (frozen fixtures decode unchanged).
     */
    private fun enabledAyanamsaOnly(c: CalcSettings): CalcSettings =
        if (Ayanamsa.isEnabled(c.ayanamsa)) c else c.copy(ayanamsa = AyanamsaId.LAHIRI)

    fun fromJson(o: JsonElement): AppSettings =
        guarded {
            val present = (o as? JsonObject)?.keys ?: fail()
            val d = AppSettings()
            read(o, optional = newKeys) {
                val locale = stringOrNull("locale")
                check(locale == null || locale.length in 1..DataLimits.LOCALE_MAX)
                AppSettings(
                    defaultCalc = enabledAyanamsaOnly(calcFromJson(objectAt("defaultCalc"))),
                    selfProfileId = stringOrNull("selfProfileId")?.let(::RecordId),
                    localeTag = locale,
                    privacyScreen = boolean("privacyScreen"),
                    homePlaceId = if ("homePlaceId" in present) stringOrNull("homePlaceId")?.let(::RecordId) else null,
                    monthSystem = enumOr(present, "monthSystem", MonthSystem.entries, d.monthSystem),
                    presetSource = enumOr(present, "presetSource", PresetSource.entries, d.presetSource),
                    ekadashi = enumOr(present, "ekadashi", Tradition.entries, d.ekadashi),
                    chartStyle = enumOr(present, "chartStyle", ChartStyle.entries, d.chartStyle),
                    themeMode = enumOr(present, "themeMode", ThemeMode.entries, d.themeMode),
                    dynamicColor = if ("dynamicColor" in present) boolean("dynamicColor") else d.dynamicColor,
                    badaText = if ("badaText" in present) boolean("badaText") else d.badaText,
                    reduceMotion = if ("reduceMotion" in present) boolean("reduceMotion") else d.reduceMotion,
                    reduceTransparency =
                        if ("reduceTransparency" in present) boolean("reduceTransparency") else d.reduceTransparency,
                    reminders = remindersOr(present),
                    onboardingDone = if ("onboardingDone" in present) boolean("onboardingDone") else d.onboardingDone,
                    showCityOnWidgets =
                        if ("showCityOnWidgets" in present) boolean("showCityOnWidgets") else d.showCityOnWidgets,
                    travelMode = if ("travelMode" in present) boolean("travelMode") else d.travelMode,
                    calendarView = enumOr(present, "calendarView", CalendarView.entries, d.calendarView),
                )
            }
        }
}
