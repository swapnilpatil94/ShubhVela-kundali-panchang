package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.HighLatitudeRule
import app.parivaar.panchang.engine.SunriseConvention
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.DashaYear
import app.parivaar.panchang.engine.jyotish.DrekkanaVariant
import app.parivaar.panchang.engine.jyotish.HoraVariant
import app.parivaar.panchang.engine.jyotish.HouseSystem
import app.parivaar.panchang.engine.jyotish.NodeType
import app.parivaar.panchang.engine.panchang.TimingStyle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
        )

    fun fromJson(o: JsonElement): AppSettings =
        guarded {
            read(o) {
                val locale = stringOrNull("locale")
                check(locale == null || locale.length in 1..DataLimits.LOCALE_MAX)
                AppSettings(
                    defaultCalc = calcFromJson(objectAt("defaultCalc")),
                    selfProfileId = stringOrNull("selfProfileId")?.let(::RecordId),
                    localeTag = locale,
                    privacyScreen = boolean("privacyScreen"),
                )
            }
        }
}
