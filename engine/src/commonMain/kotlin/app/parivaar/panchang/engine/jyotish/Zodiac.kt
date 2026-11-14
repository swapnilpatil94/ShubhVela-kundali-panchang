package app.parivaar.panchang.engine.jyotish

import kotlin.math.floor
import kotlin.math.min

/** The nine grahas, in the order used by every 9-element list and array of this package. */
enum class Graha { SUN, MOON, MARS, MERCURY, JUPITER, VENUS, SATURN, RAHU, KETU }

/** The twelve sidereal signs; `ordinal` is the sign index s (0 = MESHA). */
enum class Rashi { MESHA, VRISHABHA, MITHUNA, KARKA, SIMHA, KANYA, TULA, VRISHCHIKA, DHANU, MAKARA, KUMBHA, MEENA }

private const val FULL_CIRCLE = 360.0
private const val HALF_CIRCLE = 180.0
private const val RASHI_DEG = 30.0
private const val RASHI_COUNT = 12
private const val NAKSHATRA_COUNT = 27
private const val PADAS_PER_NAKSHATRA = 4
private const val QUARTER_COUNT = NAKSHATRA_COUNT * PADAS_PER_NAKSHATRA

/**
 * Sign, nakshatra and house arithmetic (docs/specs/M02.md section 3.1). Longitudes are sidereal degrees.
 *
 * The functions that take a longitude accept any finite value and read it modulo 360 (for a value already in
 * [0, 360) that changes no bit); NaN and infinities throw [IllegalArgumentException]. Messages never carry the
 * value.
 */
object Zodiac {
    private const val H3 = 3
    private const val H4 = 4
    private const val H5 = 5
    private const val H7 = 7
    private const val H8 = 8
    private const val H9 = 9
    private const val H10 = 10

    private val RASHIS = Rashi.entries

    private val NAKSHATRA_LORDS = listOf(
        Graha.KETU, Graha.VENUS, Graha.SUN, Graha.MOON, Graha.MARS,
        Graha.RAHU, Graha.JUPITER, Graha.SATURN, Graha.MERCURY,
    )

    private val ASPECT_7 = setOf(H7)
    private val ASPECTS_MARS = setOf(H4, H7, H8)
    private val ASPECTS_JUPITER = setOf(H5, H7, H9)
    private val ASPECTS_SATURN = setOf(H3, H7, H10)

    /** [d] reduced to [0, 360). Never returns 360.0 or -0.0; NaN and infinities give NaN. */
    fun wrap360(d: Double): Double {
        val r = d % FULL_CIRCLE // exact, sign of d, |r| < 360
        val w = if (r < 0.0) r + FULL_CIRCLE else r + 0.0 // "+ 0.0" turns -0.0 into 0.0
        return if (w >= FULL_CIRCLE) 0.0 else w // a tiny negative r rounds up to 360.0
    }

    /** [d] reduced to (-180, 180]. Exact for |d| < 180 (small differences lose no bits). */
    fun wrap180(d: Double): Double {
        val r = d % FULL_CIRCLE
        return when {
            r > HALF_CIRCLE -> r - FULL_CIRCLE
            r <= -HALF_CIRCLE -> r + FULL_CIRCLE
            else -> r + 0.0
        }
    }

    /** The sign of [lon]: s = min(11, floor(lon / 30)). */
    fun rashiOf(lon: Double): Rashi = RASHIS[rashiIndex(inCircle(lon))]

    /** Degrees of [lon] inside its sign, in [0, 30): lon - 30 s. */
    fun degInRashi(lon: Double): Double {
        val w = inCircle(lon)
        return w - RASHI_DEG * rashiIndex(w)
    }

    /** Sign lord (the nodes own no sign). */
    fun lordOf(r: Rashi): Graha = when (r) {
        Rashi.MESHA, Rashi.VRISHCHIKA -> Graha.MARS
        Rashi.VRISHABHA, Rashi.TULA -> Graha.VENUS
        Rashi.MITHUNA, Rashi.KANYA -> Graha.MERCURY
        Rashi.KARKA -> Graha.MOON
        Rashi.SIMHA -> Graha.SUN
        Rashi.DHANU, Rashi.MEENA -> Graha.JUPITER
        Rashi.MAKARA, Rashi.KUMBHA -> Graha.SATURN
    }

    /**
     * Exaltation sign; the debilitation sign is the 7th from it. Null for RAHU and KETU.
     * VERIFY with pandit: node dignities are not defined in M02.
     */
    fun exaltationOf(g: Graha): Rashi? = when (g) {
        Graha.SUN -> Rashi.MESHA
        Graha.MOON -> Rashi.VRISHABHA
        Graha.MARS -> Rashi.MAKARA
        Graha.MERCURY -> Rashi.KANYA
        Graha.JUPITER -> Rashi.KARKA
        Graha.VENUS -> Rashi.MEENA
        Graha.SATURN -> Rashi.TULA
        Graha.RAHU, Graha.KETU -> null
    }

    /** Nakshatra index 0..26 (0 = Ashwini): q div 4 with q = min(107, floor(lon * 108 / 360)). */
    fun nakshatraOf(lon: Double): Int = quarterOf(lon) / PADAS_PER_NAKSHATRA

    /** Pada 1..4: q mod 4 + 1 with the q of [nakshatraOf]. */
    fun padaOf(lon: Double): Int = quarterOf(lon) % PADAS_PER_NAKSHATRA + 1

    /** Vimshottari lord of nakshatra [n] (0..26). */
    fun nakshatraLord(n: Int): Graha {
        require(n in 0 until NAKSHATRA_COUNT) { "nakshatra index out of range" }
        return NAKSHATRA_LORDS[n % NAKSHATRA_LORDS.size]
    }

    /** House 1..12 of [target] counted by sign from [ref] (1 = the same sign). */
    fun houseFrom(ref: Rashi, target: Rashi): Int = (target.ordinal - ref.ordinal).mod(RASHI_COUNT) + 1

    /**
     * Full sign aspects of [g], as houses counted from the graha's own sign. Empty for RAHU and KETU.
     * VERIFY with pandit: node aspects are not defined in M02.
     */
    fun aspectsOf(g: Graha): Set<Int> = when (g) {
        Graha.SUN, Graha.MOON, Graha.MERCURY, Graha.VENUS -> ASPECT_7
        Graha.MARS -> ASPECTS_MARS
        Graha.JUPITER -> ASPECTS_JUPITER
        Graha.SATURN -> ASPECTS_SATURN
        Graha.RAHU, Graha.KETU -> emptySet()
    }
}

/** [lon] in [0, 360); NaN and infinities are rejected (the message carries no value). */
private fun inCircle(lon: Double): Double {
    val w = Zodiac.wrap360(lon)
    require(!w.isNaN()) { "longitude must be finite" }
    return w
}

/** s = min(11, floor(w / 30)) for w in [0, 360). */
private fun rashiIndex(w: Double): Int = min(RASHI_COUNT - 1, floor(w / RASHI_DEG).toInt())

/** q = min(107, floor(lon * 108 / 360)), in this operation order. */
private fun quarterOf(lon: Double): Int =
    min(QUARTER_COUNT - 1, floor(inCircle(lon) * QUARTER_COUNT / FULL_CIRCLE).toInt())
