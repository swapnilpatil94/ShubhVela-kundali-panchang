package app.parivaar.panchang.content.horoscope

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.jyotish.ChartCalculator
import app.parivaar.panchang.engine.jyotish.ChartFacts
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Ingress
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlin.time.Instant

/** The transit facts the rashifal rules read (docs/specs/M11.md section 2). Sidereal, per the caller's settings. */
interface TransitSource {
    /** The sidereal sign of [g] at [at]. */
    fun rashi(g: Graha, at: Instant): Rashi

    /** True when [g] moves backwards in sidereal longitude at [at]. */
    fun retrograde(g: Graha, at: Instant): Boolean

    /** The Moon's nakshatra at [at], 0..26 (0 = Ashwini). */
    fun nakshatra(at: Instant): Int

    /** Sign changes of [g] in [from]..[to], in time order. */
    fun ingresses(g: Graha, from: Instant, to: Instant): List<Ingress>
}

/**
 * [TransitSource] over the engine: positions from [calc] (`ChartCalculator.compute(at, place, settings)`), sign
 * changes from [gochar]. The last chart and the last ingress search are memoised, so the rules can ask several
 * questions about one instant cheaply. Not thread-safe (neither are the engine calculators).
 */
class EngineTransitSource(
    private val calc: ChartCalculator,
    private val gochar: Gochar,
    private val place: Place,
    private val settings: CalcSettings,
) : TransitSource {
    private var lastAt: Instant? = null
    private var lastChart: ChartFacts? = null
    private var lastIngressKey: Triple<Graha, Instant, Instant>? = null
    private var lastIngresses: List<Ingress> = emptyList()

    private fun chart(at: Instant): ChartFacts {
        val cached = lastChart
        if (cached != null && lastAt == at) return cached
        return calc.compute(at, place, settings).also {
            lastAt = at
            lastChart = it
        }
    }

    override fun rashi(g: Graha, at: Instant): Rashi = chart(at).of(g).rashi

    override fun retrograde(g: Graha, at: Instant): Boolean = chart(at).of(g).retrograde

    override fun nakshatra(at: Instant): Int = chart(at).of(Graha.MOON).nakshatra

    override fun ingresses(g: Graha, from: Instant, to: Instant): List<Ingress> {
        val key = Triple(g, from, to)
        if (key == lastIngressKey) return lastIngresses
        return gochar.ingresses(g, from, to, settings).also {
            lastIngressKey = key
            lastIngresses = it
        }
    }

    override fun toString(): String = "EngineTransitSource(redacted)"
}
