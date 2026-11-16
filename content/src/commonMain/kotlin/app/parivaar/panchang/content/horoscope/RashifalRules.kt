package app.parivaar.panchang.content.horoscope

import app.parivaar.panchang.content.ContentIds
import app.parivaar.panchang.content.text.TemplateArg
import app.parivaar.panchang.content.text.TermKind
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Ingress
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.SadeSatiPhase
import app.parivaar.panchang.engine.jyotish.Zodiac
import app.parivaar.panchang.engine.panchang.Bala
import kotlin.time.Instant

/** The span a reading covers. */
enum class Period { DAY, WEEK, MONTH, YEAR }

/**
 * One reading window [start, end), built by the caller (docs/specs/M11.md section 3 "H1 periods").
 *
 * @property index DAY: civil epochDay; WEEK: floorDiv(epochDay of its Monday, 7); MONTH: year * 12 + month - 1;
 *   YEAR: the year. It seeds the variant and planet rotation, so equal windows give equal readings.
 */
data class PeriodWindow(val start: Instant, val end: Instant, val index: Long) {
    init {
        require(start < end) { "window start must be before its end" }
    }
}

/** One line of a reading: a content id and its template arguments. Redacted: ids encode houses of a person. */
data class Line(val id: String, val args: Map<String, TemplateArg>) {
    override fun toString(): String = "Line(redacted)"
}

/** A reading as content ids only; text comes from the packs. */
data class Reading(val period: Period, val lines: List<Line>) {
    override fun toString(): String = "Reading(redacted)"
}

/**
 * The birth-derived inputs of the personal reading. Personal data: in memory only, `toString` redacted.
 *
 * @property moonNakshatra 0..26
 * @property dasha the running mahadasha lord, then the antardasha lord (either may be absent)
 */
data class NatalInputs(val lagna: Rashi, val moon: Rashi, val moonNakshatra: Int, val dasha: List<Graha>) {
    init {
        require(moonNakshatra in 0 until NAKSHATRAS) { "nakshatra out of range" }
    }

    override fun toString(): String = "NatalInputs(redacted)"

    private companion object {
        const val NAKSHATRAS = 27
    }
}

/**
 * Deterministic rashifal (H1) and personal daily guidance (H2) per docs/specs/M11.md section 3. A pure function of
 * the sign or natal inputs, the window, [src] and [variants]: no randomness, no clock reads.
 *
 * [variants] returns how many contiguous variants `<prefix>.v1..vN` the user-language pack holds (the English pack
 * when the line falls back); 0 is treated as 1.
 *
 * The planet sets per period and the secondary-planet rotation are a product convention, not tradition (VERIFY V7).
 */
class RashifalRules(private val src: TransitSource, private val variants: (String) -> Int) {
    /** The rashifal of [sign] for [period] over [window]. */
    fun h1(sign: Rashi, period: Period, window: PeriodWindow): Reading =
        Reading(period, periodLines(sign, period, window))

    /** The personal daily reading (H2 daily); houses from the natal Moon unless stated. */
    fun h2(natal: NatalInputs, window: PeriodWindow): Reading {
        val at = window.start
        val sign = natal.moon
        val lines = ArrayList<Line>()
        lines += Line(ContentIds.tara(Bala.tara(src.nakshatra(at), natal.moonNakshatra)), emptyMap())
        val moonHouse = Zodiac.houseFrom(natal.moon, src.rashi(Graha.MOON, at))
        lines += gochar(Graha.MOON, moonHouse, sign, window)
        lines += dashaLines(natal, window)
        if (moonHouse == CHANDRASHTAMA_HOUSE) lines += Line(ContentIds.CHANDRASHTAMA, emptyMap())
        val phase = sadeSati(natal.moon, src.rashi(Graha.SATURN, at))
        if (phase != SadeSatiPhase.NONE) lines += Line(ContentIds.SADESATI[phase.ordinal - 1], emptyMap())
        return Reading(Period.DAY, lines)
    }

    /** The personal weekly reading (H2 weekly): H1 WEEK for the natal lagna, then the dasha-lord lines. */
    fun h2Week(natal: NatalInputs, window: PeriodWindow): Reading =
        Reading(Period.WEEK, periodLines(natal.lagna, Period.WEEK, window) + dashaLines(natal, window))

    private fun periodLines(sign: Rashi, period: Period, window: PeriodWindow): List<Line> {
        val planets = planets(sign, period, window.index)
        val movers = if (period == Period.DAY) listOf(Graha.MOON) else planets.filter { it != Graha.MOON }
        val shifts = movers.filter { it in planets }
            .flatMap { src.ingresses(it, window.start, window.end) }
            .filter { it.at >= window.start && it.at < window.end }
            .sortedBy { it.at }
            .take(MAX_INGRESSES)
        val lines = ArrayList<Line>()
        for (g in planets) {
            lines += gochar(g, Zodiac.houseFrom(sign, src.rashi(g, window.start)), sign, window)
            if (src.retrograde(g, window.start) && g != Graha.RAHU && g != Graha.KETU) {
                lines += Line(ContentIds.RETRO, mapOf(ARG_GRAHA to graha(g)))
            }
            for (ing in shifts) {
                if (ing.graha != g) continue
                lines += ingressLine(ing, moonShift = period == Period.DAY && g == Graha.MOON)
                lines += gochar(g, Zodiac.houseFrom(sign, ing.into), sign, window)
            }
        }
        return lines
    }

    private fun ingressLine(ing: Ingress, moonShift: Boolean): Line = if (moonShift) {
        Line(ContentIds.MOON_SHIFT, mapOf(ARG_TIME to TemplateArg.Time(ing.at), ARG_RASHI to rashi(ing.into)))
    } else {
        Line(
            ContentIds.INGRESS,
            mapOf(ARG_GRAHA to graha(ing.graha), ARG_RASHI to rashi(ing.into), ARG_TIME to TemplateArg.Time(ing.at)),
        )
    }

    /** Step (3): the dasha lord (MD, or AD when MD is the Moon) and its house from the lagna. */
    private fun dashaLines(natal: NatalInputs, window: PeriodWindow): List<Line> {
        val md = natal.dasha.getOrNull(0)
        val lord = if (md == Graha.MOON) natal.dasha.getOrNull(1) else md
        if (lord == null || lord == Graha.MOON) return emptyList()
        val house = Zodiac.houseFrom(natal.lagna, src.rashi(lord, window.start))
        return listOf(
            Line(ContentIds.DASHA_LORD, mapOf(ARG_GRAHA to graha(lord))),
            gochar(lord, house, natal.moon, window),
        )
    }

    /** `gochar.<g>.h<house>.v<k>`, k = floorMod(index + sign + graha, n) + 1. */
    private fun gochar(g: Graha, house: Int, sign: Rashi, window: PeriodWindow): Line {
        val n = maxOf(1, variants(ContentIds.gocharPrefix(g, house)))
        val k = (window.index + sign.ordinal + g.ordinal).mod(n.toLong()) + 1
        return Line(ContentIds.gochar(g, house, k.toInt()), emptyMap())
    }

    private companion object {
        const val MAX_INGRESSES = 2
        const val CHANDRASHTAMA_HOUSE = 8
        const val ARG_GRAHA = "graha"
        const val ARG_RASHI = "rashi"
        const val ARG_TIME = "time"
        const val ROTATION = 3

        /** DAY secondary planets, rotated by floorMod(index + sign, 8). */
        val SEC = listOf(
            Graha.SUN, Graha.MARS, Graha.MERCURY, Graha.JUPITER, Graha.VENUS, Graha.SATURN, Graha.RAHU, Graha.KETU,
        )
        val WEEK_INNER = listOf(Graha.MARS, Graha.MERCURY, Graha.VENUS)
        val WEEK_OUTER = listOf(Graha.JUPITER, Graha.SATURN, Graha.RAHU)
        val MONTH = listOf(Graha.SUN, Graha.MARS, Graha.VENUS, Graha.JUPITER)
        val YEAR = listOf(Graha.JUPITER, Graha.SATURN, Graha.RAHU, Graha.KETU)

        fun planets(sign: Rashi, period: Period, index: Long): List<Graha> = when (period) {
            Period.DAY -> listOf(Graha.MOON, SEC[(index + sign.ordinal).mod(SEC.size.toLong()).toInt()])
            Period.WEEK -> {
                val r = index.mod(ROTATION.toLong()).toInt()
                listOf(Graha.SUN, WEEK_INNER[r], WEEK_OUTER[r])
            }
            Period.MONTH -> MONTH
            Period.YEAR -> YEAR
        }

        fun graha(g: Graha): TemplateArg = TemplateArg.Term(TermKind.GRAHA, g.ordinal + 1)

        fun rashi(r: Rashi): TemplateArg = TemplateArg.Term(TermKind.RASHI, r.ordinal + 1)

        /**
         * The same mapping as `Gochar.sadeSati` (an instance method that needs an ephemeris); kept equal by
         * RashifalRulesTest over all 144 sign pairs.
         */
        @Suppress("MagicNumber") // house numbers 1..12
        fun sadeSati(natalMoon: Rashi, saturn: Rashi): SadeSatiPhase = when (Zodiac.houseFrom(natalMoon, saturn)) {
            12 -> SadeSatiPhase.RISING
            1 -> SadeSatiPhase.PEAK
            2 -> SadeSatiPhase.SETTING
            4 -> SadeSatiPhase.DHAIYA_4TH
            8 -> SadeSatiPhase.DHAIYA_8TH
            else -> SadeSatiPhase.NONE
        }
    }
}
