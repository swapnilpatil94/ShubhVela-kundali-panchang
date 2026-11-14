package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.SelectingEphemeris
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import kotlin.time.Instant

/**
 * Assembles a sidereal chart (docs/specs/M02.md section 3.5): the nine grahas from [eph], minus the true
 * ayanamsa, with houses from [Houses.sidereal].
 *
 * Not thread-safe when [eph] is not (the engine's backends hold small caches). The result is personal data:
 * see [ChartFacts].
 */
class ChartCalculator(private val eph: Ephemeris, private val tables: TimeTables = TimeTables.DEFAULT) {
    /**
     * The chart for [at] and [place].
     *
     * - Longitude = apparent tropical longitude minus the true ayanamsa; speed = tropical speed minus the rate
     *   of the true ayanamsa; RAHU and KETU follow `settings.node`.
     * - If the requested house system is unavailable (PLACIDUS at high latitude), WHOLE_SIGN is used and
     *   `houseFallback` is true.
     *
     * Throws [AyanamsaUnavailableException] for a disabled ayanamsa and whatever [eph] throws outside its range.
     */
    fun compute(at: Instant, place: Place, settings: CalcSettings = CalcSettings()): ChartFacts {
        val tt = TimeScales.utcToTt(at, tables)
        val ayanamsa = Ayanamsa.trueDeg(settings.ayanamsa, tt)
        val ayanamsaRate = Ayanamsa.trueRateDegPerDay(settings.ayanamsa, tt)
        val grahas = Graha.entries
        val lon = DoubleArray(grahas.size)
        val lat = DoubleArray(grahas.size)
        val speed = DoubleArray(grahas.size)
        for (g in grahas) {
            val pos = eph.geoApparent(bodyOf(g, settings.node), tt)
            lon[g.ordinal] = Zodiac.wrap360(pos.lonDeg - ayanamsa) // = Ayanamsa.siderealDeg, ayanamsa computed once
            lat[g.ordinal] = pos.latDeg
            speed[g.ordinal] = pos.speedDegPerDay - ayanamsaRate
        }
        val requested = Houses.sidereal(at, place, settings, tables)
        val fallback = requested !is HouseResult.Cusps
        val houses = if (fallback) {
            Houses.sidereal(at, place, settings.copy(houseSystem = HouseSystem.WHOLE_SIGN), tables)
        } else {
            requested
        }
        check(houses is HouseResult.Cusps) { "whole-sign houses are always available" }
        val backend = (eph as? SelectingEphemeris)?.backendFor(tt)
        return ChartFacts.fromLongitudes(tt, settings, ayanamsa, backend, houses, fallback, lon, lat, speed)
    }
}

private fun bodyOf(g: Graha, node: NodeType): Body = when (g) {
    Graha.SUN -> Body.SUN
    Graha.MOON -> Body.MOON
    Graha.MARS -> Body.MARS
    Graha.MERCURY -> Body.MERCURY
    Graha.JUPITER -> Body.JUPITER
    Graha.VENUS -> Body.VENUS
    Graha.SATURN -> Body.SATURN
    Graha.RAHU -> if (node == NodeType.TRUE) Body.RAHU_TRUE else Body.RAHU_MEAN
    Graha.KETU -> if (node == NodeType.TRUE) Body.KETU_TRUE else Body.KETU_MEAN
}
