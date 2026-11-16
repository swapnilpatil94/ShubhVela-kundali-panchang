package app.parivaar.panchang.core.designsystem.chart

import kotlinx.datetime.LocalDate

/** Chart drawing styles (M10 rule 3). NORTH is house-based; SOUTH and EAST have fixed sign cells. */
enum class ChartStyle { NORTH, SOUTH, EAST }

/** One graha in a cell: [code] is the engine `Graha` ordinal (SUN..KETU), [glyph] its two-letter code. */
data class GrahaMark(val code: Int, val glyph: String, val degText: String?, val retro: Boolean)

/** House [house] (1..12) holds rashi [rashi] (0 = Mesha .. 11 = Meena). */
data class HouseCell(val house: Int, val rashi: Int, val grahas: List<GrahaMark>, val lagna: Boolean) {
    init {
        require(house in 1..HOUSES && rashi in 0 until HOUSES) { "cell out of range" }
    }

    override fun toString(): String = "HouseCell(redacted)"
}

/** Twelve cells in house order. [lagnaKnown] false is the noon chart: no lagna, houses or house numbers. */
data class ChartModel(val cells: List<HouseCell>, val lagnaKnown: Boolean) {
    init {
        require(cells.size == HOUSES) { "a chart has 12 cells" }
    }

    fun cellOfRashi(rashi: Int): HouseCell = cells.first { it.rashi == rashi }

    fun cellOfHouse(house: Int): HouseCell = cells[house - 1]

    override fun toString(): String = "ChartModel(redacted)"
}

/** One dasha row: [level] 1 maha .. 4 sookshma, [lords] the path of `Graha` ordinals from the maha lord down. */
data class DashaRow(
    val level: Int,
    val lords: List<Int>,
    val start: LocalDate,
    val end: LocalDate,
    val current: Boolean,
    val expandable: Boolean,
    val expanded: Boolean = false,
) {
    override fun toString(): String = "DashaRow(redacted)"
}

/** Texts the chart reads aloud, from string resources (no engine types here). */
interface ChartNames {
    fun rashi(index: Int): String

    fun graha(code: Int): String

    /** For example "House 1". */
    fun house(number: Int): String

    val lagna: String
    val retrograde: String
    val noPlanets: String
}

/** TalkBack text of one cell (M10 acceptance 1): house (when known), rashi, lagna, then the grahas or "no planets". */
object ChartA11y {
    fun describe(c: HouseCell, n: ChartNames, lagnaKnown: Boolean = true): String {
        val head = buildList {
            if (lagnaKnown) add(n.house(c.house))
            add(n.rashi(c.rashi))
            if (lagnaKnown && c.lagna) add(n.lagna)
        }.joinToString(", ")
        val planets =
            if (c.grahas.isEmpty()) {
                n.noPlanets
            } else {
                c.grahas.joinToString(", ") { g ->
                    listOfNotNull(n.graha(g.code), g.degText, n.retrograde.takeIf { g.retro }).joinToString(" ")
                }
            }
        return "$head. $planets"
    }
}

internal const val HOUSES = 12
