package app.parivaar.panchang.engine

import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.DashaYear
import app.parivaar.panchang.engine.jyotish.DrekkanaVariant
import app.parivaar.panchang.engine.jyotish.HoraVariant
import app.parivaar.panchang.engine.jyotish.HouseSystem
import app.parivaar.panchang.engine.jyotish.NodeType
import app.parivaar.panchang.engine.panchang.TimingStyle

/** What "sunrise" means: which point of the disc touches which horizon (docs/specs/M01.md section 3.8). */
enum class SunriseConvention {
    /** Upper limb on the refracted horizon: centre at -(R + semidiameter). The usual almanac definition. */
    UPPER_LIMB_REFRACTED,

    /** Disc centre on the refracted horizon: centre at -R. */
    CENTER_REFRACTED,

    /** Disc centre on the geometric horizon: centre at 0, no refraction. */
    CENTER_GEOMETRIC,
}

/**
 * What to report for the Sun when a day has no sunrise or no sunset (polar day and night).
 * VERIFY with pandit: both definitions and the default; the UI flag stays off until sign-off.
 */
enum class HighLatitudeRule {
    /** The events of the nearest day that has both, moved into the requested day. */
    NEAREST_DAY,

    /** The instant of the Sun's greatest altitude minus and plus 6 hours. */
    LOCAL_NOON_PM_6H,
}

/**
 * Calculation conventions.
 *
 * @property sunrise rise/set definition
 * @property highLatitudeRule fallback for days without sunrise or sunset (VERIFY: the default)
 * @property pressureHPa air pressure for refraction, hPa; R = 34' (P / 1010) (283 / (273 + T))
 * @property temperatureC air temperature for refraction, degrees Celsius (non-default scaling is VERIFY)
 * @property ayanamsa sidereal zero point (docs/specs/M02.md section 3.2)
 * @property node true or mean lunar node for Rahu and Ketu
 * @property houseSystem house division (docs/specs/M02.md section 3.3)
 * @property dashaYear length of the dasha year (docs/specs/M02.md section 3.4)
 * @property hora D2 rule set
 * @property drekkana D3 rule set
 * @property timingStyle day used for Rahu kaal, Yamaganda and Gulika (docs/specs/M03.md section 3.3)
 * @property abhijitOnWednesday report Abhijit muhurta on Wednesdays too (VERIFY: the default)
 */
data class CalcSettings(
    val sunrise: SunriseConvention = SunriseConvention.UPPER_LIMB_REFRACTED,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.LOCAL_NOON_PM_6H,
    val pressureHPa: Double = 1010.0,
    val temperatureC: Double = 10.0,
    val ayanamsa: AyanamsaId = AyanamsaId.LAHIRI,
    val node: NodeType = NodeType.TRUE,
    val houseSystem: HouseSystem = HouseSystem.WHOLE_SIGN,
    val dashaYear: DashaYear = DashaYear.JULIAN_365_25,
    val hora: HoraVariant = HoraVariant.PARASHARA,
    val drekkana: DrekkanaVariant = DrekkanaVariant.PARASHARA,
    val timingStyle: TimingStyle = TimingStyle.ACTUAL_SUNRISE,
    val abhijitOnWednesday: Boolean = false,
) {
    init {
        require(pressureHPa >= 0.0 && pressureHPa.isFinite()) { "pressure must be a finite value >= 0 hPa" }
        require(temperatureC > MIN_TEMPERATURE_C && temperatureC.isFinite()) { "temperature must be above -273 C" }
    }

    private companion object {
        const val MIN_TEMPERATURE_C = -273.0
    }
}
