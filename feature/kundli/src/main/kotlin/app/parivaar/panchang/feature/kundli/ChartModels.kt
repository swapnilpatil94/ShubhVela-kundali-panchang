package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.designsystem.chart.ChartModel
import app.parivaar.panchang.core.designsystem.chart.GrahaMark
import app.parivaar.panchang.core.designsystem.chart.HouseCell
import app.parivaar.panchang.engine.jyotish.ChartFacts
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.VargaChart
import kotlin.math.floor

/** Pure mapping from engine charts to the drawable [ChartModel] (rule 3; houses WHOLE_SIGN for display, §5). */
object ChartModels {
    private const val SIGNS = 12
    private const val MINUTES = 60

    /** Fallback glyphs for tests; the screen passes the two-letter codes from resources. */
    val DEFAULT_GLYPHS = listOf("Su", "Mo", "Ma", "Me", "Ju", "Ve", "Sa", "Ra", "Ke")

    /** Whole degrees in the sign, for example "12°" (never rounded up into the next degree). */
    fun degText(degInRashi: Double): String = "${floor(degInRashi).toInt()}°"

    /** D°MM′ in the sign (rule 4), truncated to the minute. */
    fun dms(degInRashi: Double): String {
        val totalMin = floor(degInRashi * MINUTES).toInt()
        return "${totalMin / MINUTES}°${(totalMin % MINUTES).toString().padStart(2, '0')}′"
    }

    /** Where one graha sits: sign 0..11, degree text (or null) and retrograde flag. */
    private class Pos(val sign: Int, val deg: String?, val retro: Boolean)

    private fun cells(glyphs: (Int) -> String, lagna: Int, noon: Boolean, pos: (Graha) -> Pos): ChartModel {
        // Noon chart (rule 2): no lagna, so cells are keyed by sign (house numbers are hidden by lagnaKnown = false).
        val first = if (noon) 0 else lagna
        val all = Graha.entries.associateWith(pos)
        val cells = (1..SIGNS).map { h ->
            val rashi = (first + h - 1) % SIGNS
            val marks = all.filterValues { it.sign == rashi }
                .map { (g, p) -> GrahaMark(g.ordinal, glyphs(g.ordinal), p.deg, p.retro) }
            HouseCell(h, rashi, marks, lagna = !noon && h == 1)
        }
        return ChartModel(cells, lagnaKnown = !noon)
    }

    fun d1(f: ChartFacts, noon: Boolean, glyphs: (Int) -> String = DEFAULT_GLYPHS::get): ChartModel =
        cells(glyphs, f.lagna.ordinal, noon) { g -> f.of(g).let { Pos(
            it.rashi.ordinal, degText(it.degInRashi), it.retrograde,
        ) } }

    fun d9(
        v: VargaChart,
        noon: Boolean,
        glyphs: (Int) -> String = DEFAULT_GLYPHS::get,
        retro: (Graha) -> Boolean = { false },
    ): ChartModel = cells(glyphs, v.lagna.rashi.ordinal, noon) { g -> Pos(
        v.grahas[g.ordinal].rashi.ordinal, null, retro(g),
    ) }
}
