package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.BackendId
import app.parivaar.panchang.engine.astro.JulianDay

/**
 * One graha in a chart. Derived from birth data: `toString` is redacted.
 *
 * @property lon sidereal longitude, degrees in [0, 360)
 * @property lat ecliptic latitude, degrees
 * @property speedDegPerDay sidereal longitude rate
 * @property retrograde speed < 0
 * @property degInRashi degrees inside [rashi], in [0, 30)
 * @property nakshatra 0..26 (0 = Ashwini)
 * @property pada 1..4
 * @property house 1..12 in the chart's house system
 */
data class GrahaFact(
    val graha: Graha,
    val lon: Double,
    val lat: Double,
    val speedDegPerDay: Double,
    val retrograde: Boolean,
    val rashi: Rashi,
    val degInRashi: Double,
    val nakshatra: Int,
    val pada: Int,
    val house: Int,
) {
    override fun toString(): String = "GrahaFact(redacted)"
}

/**
 * The computed facts of one chart (docs/specs/M02.md section 3.5): codes and numbers only, never prose.
 *
 * This is personal data. It stays in memory on the device and must not be logged; `toString` is redacted.
 *
 * @property tt the instant as a TT Julian Day
 * @property ayanamsaDeg the true ayanamsa that was subtracted, degrees
 * @property backend which ephemeris backend served [tt]; null when the ephemeris does not report one
 * @property ascDeg sidereal ascendant, degrees in [0, 360)
 * @property mcDeg sidereal midheaven, degrees in [0, 360)
 * @property houseSystem the system actually used (WHOLE_SIGN when [houseFallback] is true)
 * @property houseFallback true when the requested system was unavailable and WHOLE_SIGN replaced it
 * @property cuspsDeg 12 sidereal cusps, indexed by house - 1
 * @property startsDeg 12 sidereal house starts, indexed by house - 1
 * @property grahas the 9 grahas in [Graha] order
 */
data class ChartFacts(
    val tt: JulianDay,
    val ayanamsa: AyanamsaId,
    val ayanamsaDeg: Double,
    val node: NodeType,
    val backend: BackendId?,
    val ascDeg: Double,
    val mcDeg: Double,
    val houseSystem: HouseSystem,
    val houseFallback: Boolean,
    val cuspsDeg: List<Double>,
    val startsDeg: List<Double>,
    val grahas: List<GrahaFact>,
) {
    init {
        require(cuspsDeg.size == HOUSE_COUNT && startsDeg.size == HOUSE_COUNT) { "a chart has 12 houses" }
        require(grahas.size == GRAHAS.size && grahas.indices.all { grahas[it].graha == GRAHAS[it] }) {
            "a chart has the 9 grahas in Graha order"
        }
    }

    /** The sign of the ascendant. */
    val lagna: Rashi get() = Zodiac.rashiOf(ascDeg)

    /** The fact for [g]. */
    fun of(g: Graha): GrahaFact = grahas[g.ordinal]

    override fun toString(): String = "ChartFacts(redacted)"

    companion object {
        private const val HOUSE_COUNT = 12
        private val GRAHAS = Graha.entries

        /**
         * Builds a chart from sidereal longitudes and fills every derived field (sign, degree in sign,
         * nakshatra, pada, house, retrograde). Also the way tests build synthetic charts.
         *
         * [lon], [lat] and [speed] hold 9 values in [Graha] order; longitudes are read modulo 360.
         * `ayanamsa` and `node` come from [settings]; the house system is the one of [houses], which differs
         * from `settings.houseSystem` when [fallback] is true. House numbers: WHOLE_SIGN counts signs from the
         * lagna, every other system uses [HouseResult.Cusps.houseOf].
         */
        @Suppress("LongParameterList") // the signature is the M02 contract (docs/specs/M02.md section 2)
        fun fromLongitudes(
            tt: JulianDay,
            settings: CalcSettings,
            ayanamsaDeg: Double,
            backend: BackendId?,
            houses: HouseResult.Cusps,
            fallback: Boolean,
            lon: DoubleArray,
            lat: DoubleArray,
            speed: DoubleArray,
        ): ChartFacts {
            require(lon.size == GRAHAS.size && lat.size == GRAHAS.size && speed.size == GRAHAS.size) {
                "a chart needs 9 longitudes, latitudes and speeds"
            }
            val lagna = Zodiac.rashiOf(houses.ascDeg)
            val wholeSign = houses.system == HouseSystem.WHOLE_SIGN
            val grahas = GRAHAS.map { g ->
                val i = g.ordinal
                val l = Zodiac.wrap360(lon[i])
                val rashi = Zodiac.rashiOf(l) // throws on NaN and infinities
                GrahaFact(
                    graha = g,
                    lon = l,
                    lat = lat[i],
                    speedDegPerDay = speed[i],
                    retrograde = speed[i] < 0.0,
                    rashi = rashi,
                    degInRashi = Zodiac.degInRashi(l),
                    nakshatra = Zodiac.nakshatraOf(l),
                    pada = Zodiac.padaOf(l),
                    house = if (wholeSign) Zodiac.houseFrom(lagna, rashi) else houses.houseOf(l),
                )
            }
            return ChartFacts(
                tt = tt,
                ayanamsa = settings.ayanamsa,
                ayanamsaDeg = ayanamsaDeg,
                node = settings.node,
                backend = backend,
                ascDeg = houses.ascDeg,
                mcDeg = houses.mcDeg,
                houseSystem = houses.system,
                houseFallback = fallback,
                cuspsDeg = houses.cuspsDeg,
                startsDeg = houses.startsDeg,
                grahas = grahas,
            )
        }
    }
}
