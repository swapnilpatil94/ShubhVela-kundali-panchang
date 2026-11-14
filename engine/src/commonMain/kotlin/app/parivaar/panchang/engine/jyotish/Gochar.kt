package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.astro.positionOnly
import kotlin.time.Instant

/** Sade Sati and Dhaiya phase of Saturn from the natal Moon sign (internal ids; display text is content). */
enum class SadeSatiPhase { NONE, RISING, PEAK, SETTING, DHAIYA_4TH, DHAIYA_8TH }

/**
 * One graha's transit position relative to a natal chart. Derived from birth data, so redacted.
 *
 * @property graha the transiting graha
 * @property rashi its current sign
 * @property houseFromLagna house 1..12 counted by sign from the natal lagna
 * @property houseFromMoon house 1..12 counted by sign from the natal Moon
 * @property retrograde its sidereal speed is negative
 */
data class TransitFact(
    val graha: Graha,
    val rashi: Rashi,
    val houseFromLagna: Int,
    val houseFromMoon: Int,
    val retrograde: Boolean,
) {
    override fun toString(): String = "TransitFact(redacted)"
}

/**
 * A sign change of a graha.
 *
 * @property graha the graha
 * @property into the sign it enters
 * @property at the moment of crossing, to within about a second
 * @property retrograde true when the crossing runs backwards (into the previous sign)
 */
data class Ingress(val graha: Graha, val into: Rashi, val at: Instant, val retrograde: Boolean)

/** Transit helpers (docs/specs/M02.md section 3.9). Not thread-safe when [eph] is not. */
class Gochar(private val eph: Ephemeris, private val tables: TimeTables = TimeTables.DEFAULT) {
    /** Where each graha of [transit] sits relative to the lagna and the Moon of [natal]. */
    fun snapshot(natal: ChartFacts, transit: ChartFacts): List<TransitFact> {
        val moon = natal.of(Graha.MOON).rashi
        return transit.grahas.map {
            TransitFact(
                graha = it.graha,
                rashi = it.rashi,
                houseFromLagna = Zodiac.houseFrom(natal.lagna, it.rashi),
                houseFromMoon = Zodiac.houseFrom(moon, it.rashi),
                retrograde = it.retrograde,
            )
        }
    }

    /** Saturn's phase from the house of its sign counted from the natal Moon sign (VERIFY with pandit). */
    fun sadeSati(natalMoon: Rashi, transitSaturn: Rashi): SadeSatiPhase =
        when (Zodiac.houseFrom(natalMoon, transitSaturn)) {
            HOUSE_12 -> SadeSatiPhase.RISING
            HOUSE_1 -> SadeSatiPhase.PEAK
            HOUSE_2 -> SadeSatiPhase.SETTING
            HOUSE_4 -> SadeSatiPhase.DHAIYA_4TH
            HOUSE_8 -> SadeSatiPhase.DHAIYA_8TH
            else -> SadeSatiPhase.NONE
        }

    /**
     * Sign changes of [graha] in [from]..[to] (UTC), in time order.
     *
     * Samples every 6 hours for the Moon and every day for the others (the last sample is [to] itself); a
     * double crossing inside one step is not reported (docs/ENGINE_DIFFS.md). Each crossing is bisected to a
     * bracket of at most 0.5 s and `at` is its midpoint, rounded to a millisecond.
     */
    fun ingresses(graha: Graha, from: Instant, to: Instant, settings: CalcSettings = CalcSettings()): List<Ingress> {
        require(from <= to) { "from must not be after to" }
        val body = bodyOf(graha, settings.node)
        val step = if (graha == Graha.MOON) MOON_STEP_MS else DAY_MS
        val startMs = from.toEpochMilliseconds()
        val endMs = to.toEpochMilliseconds()
        val out = ArrayList<Ingress>()
        var prevMs = startMs
        var prevLon = lonAt(body, prevMs, settings)
        var k = 1L
        while (prevMs < endMs) {
            val ms = minOf(startMs + k * step, endMs)
            val lon = lonAt(body, ms, settings)
            val a = Zodiac.rashiOf(prevLon).ordinal
            val b = Zodiac.rashiOf(lon).ordinal
            if (a != b) out += crossing(graha, body, settings, prevMs to prevLon, ms to a, b)
            prevMs = ms
            prevLon = lon
            k++
        }
        return out
    }

    private fun crossing(
        graha: Graha,
        body: Body,
        settings: CalcSettings,
        before: Pair<Long, Double>,
        afterMs: Pair<Long, Int>,
        b: Int,
    ): Ingress {
        val a = afterMs.second
        val forward = when ((b - a).mod(RASHI_COUNT)) {
            1 -> true
            RASHI_COUNT - 1 -> false
            else -> error("graha moved more than one sign in one sampling step")
        }
        val boundary = RASHI_DEG * (if (forward) b else a)
        var lo = before.first
        var hi = afterMs.first
        val loNegative = Zodiac.wrap180(before.second - boundary) < 0.0
        while (hi - lo > BRACKET_MS) {
            val mid = lo + (hi - lo) / 2
            if ((Zodiac.wrap180(lonAt(body, mid, settings) - boundary) < 0.0) == loNegative) lo = mid else hi = mid
        }
        val at = Instant.fromEpochMilliseconds((lo + hi + 1) / 2)
        return Ingress(graha, Rashi.entries[b], at, retrograde = !forward)
    }

    private fun lonAt(body: Body, epochMs: Long, settings: CalcSettings): Double {
        val tt: JulianDay = TimeScales.utcToTt(Instant.fromEpochMilliseconds(epochMs), tables)
        return Ayanamsa.siderealDeg(eph.positionOnly(body, tt).lonDeg, settings.ayanamsa, tt)
    }

    private companion object {
        const val HOUSE_1 = 1
        const val HOUSE_2 = 2
        const val HOUSE_4 = 4
        const val HOUSE_8 = 8
        const val HOUSE_12 = 12
        const val RASHI_COUNT = 12
        const val RASHI_DEG = 30.0
        const val DAY_MS = 86_400_000L
        const val MOON_STEP_MS = DAY_MS / 4
        const val BRACKET_MS = 500L
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
