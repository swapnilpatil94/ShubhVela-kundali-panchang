package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.JulianDay
import java.io.File
import kotlin.math.floor

/** Test helpers for the rule-driven jyotish tests (synthetic charts only; no real birth data). */
internal object RuleTestSupport {
    /** Text of `rules/<name>`; the directory comes from the `pp.rulesDir` system property set by Gradle. */
    fun rule(name: String): String =
        File(checkNotNull(System.getProperty("pp.rulesDir")) { "pp.rulesDir is not set" }, name).readText()

    /** A WHOLE_SIGN chart from the ascendant [asc] and nine sidereal longitudes in [Graha] order. */
    fun chart(asc: Double, lons: DoubleArray, settings: CalcSettings = CalcSettings()): ChartFacts {
        val first = 30.0 * floor(asc / 30.0)
        val starts = List(12) { (first + 30.0 * it) % 360.0 }
        val houses = HouseResult.Cusps(HouseSystem.WHOLE_SIGN, asc, 0.0, starts, starts)
        return ChartFacts.fromLongitudes(
            tt = JulianDay(2451545.0),
            settings = settings,
            ayanamsaDeg = 24.0,
            backend = null,
            houses = houses,
            fallback = false,
            lon = lons,
            lat = DoubleArray(9),
            speed = DoubleArray(9) { 1.0 },
        )
    }
}
