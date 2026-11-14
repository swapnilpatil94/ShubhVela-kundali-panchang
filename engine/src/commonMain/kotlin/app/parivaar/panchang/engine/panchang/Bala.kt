package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.Zodiac

/** Tarabala and chandrabala numbers (MASTER Part 6). Natal inputs are used by the caller only, never stored. */
object Bala {
    private const val NAKSHATRAS = 27
    private const val TARAS = 9

    /**
     * The tara 1..9 of [todayNakshatra] counted from [natalNakshatra] (both 0..26):
     * ((today - natal) mod 27) mod 9 + 1. Which taras are favourable is not defined yet (TODO(owner)).
     */
    fun tara(todayNakshatra: Int, natalNakshatra: Int): Int {
        require(todayNakshatra in 0 until NAKSHATRAS && natalNakshatra in 0 until NAKSHATRAS) {
            "nakshatra index out of range"
        }
        return (todayNakshatra - natalNakshatra).mod(NAKSHATRAS) % TARAS + 1
    }

    /** The house 1..12 of [transitMoon] counted from [natalMoon] (1 = the same sign). */
    fun chandra(transitMoon: Rashi, natalMoon: Rashi): Int = Zodiac.houseFrom(natalMoon, transitMoon)
}
