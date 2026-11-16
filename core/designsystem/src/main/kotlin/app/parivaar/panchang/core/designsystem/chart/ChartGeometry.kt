@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.chart

/** A point in the unit square (0,0 top-left .. 1,1 bottom-right). */
data class UPoint(val x: Float, val y: Float)

/** One drawn cell: its polygon, the anchor where labels sit, and the model cell it shows. */
data class PlacedCell(val cell: HouseCell, val polygon: List<UPoint>, val anchor: UPoint, val labelAt: UPoint) {
    val left: Float get() = polygon.minOf { it.x }
    val top: Float get() = polygon.minOf { it.y }
    val right: Float get() = polygon.maxOf { it.x }
    val bottom: Float get() = polygon.maxOf { it.y }
}

/**
 * Pure chart layout (M10 rule 3), unit square, no Compose types, so it is tested on the JVM.
 *
 * - NORTH: houses fixed, house 1 the top-centre diamond, counter-clockwise; cell h holds rashi (lagna + h - 1) mod 12.
 * - SOUTH: 4x4 ring, signs fixed, Meena top-left, Mesha next to it, clockwise.
 * - EAST: 3x3 grid, corners split diagonally, Mesha top-centre, signs counter-clockwise. VERIFY against the
 *   owner-supplied reference `docs/fixtures/east-chart.png` (TODO(owner)).
 */
object ChartGeometry {
    private val T = UPoint(.5f, 0f)
    private val R = UPoint(1f, .5f)
    private val B = UPoint(.5f, 1f)
    private val L = UPoint(0f, .5f)
    private val C = UPoint(.5f, .5f)
    private val TL = UPoint(0f, 0f)
    private val TR = UPoint(1f, 0f)
    private val BR = UPoint(1f, 1f)
    private val BL = UPoint(0f, 1f)
    private val PTL = UPoint(.25f, .25f)
    private val PTR = UPoint(.75f, .25f)
    private val PBR = UPoint(.75f, .75f)
    private val PBL = UPoint(.25f, .75f)

    /** NORTH polygons by house 1..12 (index 0 = house 1). */
    val northHouses: List<List<UPoint>> =
        listOf(
            listOf(T, PTL, C, PTR), // 1 top diamond
            listOf(TL, T, PTL), // 2 top-left upper triangle
            listOf(TL, PTL, L), // 3
            listOf(L, PTL, C, PBL), // 4 left diamond
            listOf(L, PBL, BL), // 5
            listOf(BL, PBL, B), // 6
            listOf(B, PBL, C, PBR), // 7 bottom diamond
            listOf(B, PBR, BR), // 8
            listOf(BR, PBR, R), // 9
            listOf(R, PBR, C, PTR), // 10 right diamond
            listOf(R, PTR, TR), // 11
            listOf(TR, PTR, T), // 12 top-right upper triangle
        )

    /** Rashi shown in NORTH house [house] (1..12) for lagna rashi [lagna] (0..11). */
    fun northRashi(house: Int, lagna: Int): Int = (lagna + house - 1).mod(HOUSES)

    /** SOUTH (row, col) of rashi 0..11 in the 4x4 grid. */
    val southCells: List<Pair<Int, Int>> =
        listOf(
            0 to 1, 0 to 2, 0 to 3, // Mesha, Vrishabha, Mithuna (top row after Meena)
            1 to 3, 2 to 3, // Karka, Simha (right column)
            3 to 3, 3 to 2, 3 to 1, 3 to 0, // Kanya, Tula, Vrishchika, Dhanu (bottom row right to left)
            2 to 0, 1 to 0, // Makara, Kumbha (left column bottom-up)
            0 to 0, // Meena (top-left)
        )

    private fun third(i: Int) = i / 3f

    private fun box(r: Int, c: Int, n: Float): List<UPoint> {
        val x0 = c / n
        val y0 = r / n
        val x1 = (c + 1) / n
        val y1 = (r + 1) / n
        return listOf(UPoint(x0, y0), UPoint(x1, y0), UPoint(x1, y1), UPoint(x0, y1))
    }

    /** EAST polygons by rashi 0..11 (VERIFY order, see the class doc). */
    val eastRashis: List<List<UPoint>> =
        run {
            val a = third(1)
            val b = third(2)
            listOf(
                box(0, 1, 3f), // Mesha top-centre
                listOf(UPoint(0f, 0f), UPoint(a, 0f), UPoint(a, a)), // Vrishabha: top-left, upper triangle
                listOf(UPoint(0f, 0f), UPoint(a, a), UPoint(0f, a)), // Mithuna: top-left, lower triangle
                box(1, 0, 3f), // Karka left-middle
                listOf(UPoint(0f, b), UPoint(a, b), UPoint(0f, 1f)), // Simha: bottom-left, upper triangle
                listOf(UPoint(a, b), UPoint(a, 1f), UPoint(0f, 1f)), // Kanya: bottom-left, lower triangle
                box(2, 1, 3f), // Tula bottom-centre
                listOf(UPoint(b, b), UPoint(1f, 1f), UPoint(b, 1f)), // Vrishchika: bottom-right, lower triangle
                listOf(UPoint(b, b), UPoint(1f, b), UPoint(1f, 1f)), // Dhanu: bottom-right, upper triangle
                box(1, 2, 3f), // Makara right-middle
                listOf(UPoint(b, a), UPoint(1f, 0f), UPoint(1f, a)), // Kumbha: top-right, lower triangle
                listOf(UPoint(b, 0f), UPoint(1f, 0f), UPoint(b, a)), // Meena: top-right, upper triangle
            )
        }

    private fun centroid(p: List<UPoint>) =
        UPoint(p.sumOf { it.x.toDouble() }.toFloat() / p.size, p.sumOf { it.y.toDouble() }.toFloat() / p.size)

    /** Where the rashi number sits in a NORTH house: near the inner vertex, as in PNG 13. */
    private fun northLabel(house: Int): UPoint {
        val poly = northHouses[house - 1]
        val c = centroid(poly)
        // Diamonds: label below/next to the centre; triangles: toward the inner point.
        val inner = poly.minBy { (it.x - .5f) * (it.x - .5f) + (it.y - .5f) * (it.y - .5f) }
        return UPoint(c.x + (inner.x - c.x) * .55f, c.y + (inner.y - c.y) * .55f)
    }

    /** The 12 placed cells of [model] in [style]. NORTH needs a known lagna; without it SOUTH is used. */
    fun layout(model: ChartModel, style: ChartStyle): List<PlacedCell> =
        when (effective(style, model.lagnaKnown)) {
            ChartStyle.NORTH -> (1..HOUSES).map { h ->
                val poly = northHouses[h - 1]
                PlacedCell(model.cellOfHouse(h), poly, centroid(poly), northLabel(h))
            }
            ChartStyle.SOUTH -> (0 until HOUSES).map { r ->
                val (row, col) = southCells[r]
                val poly = box(row, col, 4f)
                val c = centroid(poly)
                PlacedCell(model.cellOfRashi(r), poly, c, UPoint(poly[0].x + .03f, poly[0].y + .035f))
            }
            ChartStyle.EAST -> (0 until HOUSES).map { r ->
                val poly = eastRashis[r]
                val c = centroid(poly)
                PlacedCell(model.cellOfRashi(r), poly, c, c)
            }
        }

    /** NORTH is house-based, so the noon chart (unknown lagna) falls back to SOUTH (rule 2). */
    fun effective(style: ChartStyle, lagnaKnown: Boolean): ChartStyle =
        if (style == ChartStyle.NORTH && !lagnaKnown) ChartStyle.SOUTH else style

    /** Styles offered for a chart (rule 2). */
    fun available(lagnaKnown: Boolean): List<ChartStyle> =
        if (lagnaKnown) ChartStyle.entries.toList() else listOf(ChartStyle.SOUTH, ChartStyle.EAST)

    /** Frame lines (pairs of points) for [style]. */
    fun lines(style: ChartStyle): List<Pair<UPoint, UPoint>> =
        when (style) {
            ChartStyle.NORTH -> listOf(TL to BR, TR to BL, T to R, R to B, B to L, L to T)
            ChartStyle.SOUTH -> buildList {
                for (i in 1..3) {
                    val f = i / 4f
                    // Inner lines stop at the hollow centre (2x2 cells).
                    if (i == 2) {
                        add(UPoint(f, 0f) to UPoint(f, .25f)); add(UPoint(f, .75f) to UPoint(f, 1f))
                        add(UPoint(0f, f) to UPoint(.25f, f)); add(UPoint(.75f, f) to UPoint(1f, f))
                    } else {
                        add(UPoint(f, 0f) to UPoint(f, 1f)); add(UPoint(0f, f) to UPoint(1f, f))
                    }
                }
            }
            ChartStyle.EAST -> {
                val a = third(1)
                val b = third(2)
                listOf(
                    UPoint(a, 0f) to UPoint(a, 1f), UPoint(b, 0f) to UPoint(b, 1f),
                    UPoint(0f, a) to UPoint(1f, a), UPoint(0f, b) to UPoint(1f, b),
                    UPoint(0f, 0f) to UPoint(a, a), UPoint(1f, 0f) to UPoint(b, a),
                    UPoint(0f, 1f) to UPoint(a, b), UPoint(1f, 1f) to UPoint(b, b),
                )
            }
        }
}
