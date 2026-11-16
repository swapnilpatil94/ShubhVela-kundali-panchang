package app.parivaar.panchang.shell

import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.feature.horoscope.NatalSnapshot
import app.parivaar.panchang.feature.kundli.KundliBundle

/**
 * The active profile's rashifal inputs from its Kundli bundle (memory only). A noon chart (no birth time) has no
 * lagna, so "Your day" asks for a birth time instead of guessing (M11 app decision).
 */
internal fun natalSnapshot(b: KundliBundle?, noon: Boolean): NatalSnapshot? {
    val f = b?.facts ?: return null
    val moon = f.of(Graha.MOON)
    return NatalSnapshot(
        moon = moon.rashi,
        sun = f.of(Graha.SUN).rashi,
        lagna = if (noon) null else f.lagna,
        moonNakshatra = moon.nakshatra,
        dasha = b.currentDasha.take(2).map { it.lords.last() },
    )
}
