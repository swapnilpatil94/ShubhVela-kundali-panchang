package app.parivaar.panchang.core.designsystem.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartGeometryTest {
    private fun model(lagna: Int, known: Boolean = true) =
        ChartModel((1..12).map { h -> HouseCell(h, (lagna + h - 1) % 12, emptyList(), h == 1) }, known)

    private fun centre(p: PlacedCell) = p.anchor

    @Test fun northHousesHoldLagnaPlusHMinusOne() {
        for (lagna in listOf(0, 11)) {
            val cells = ChartGeometry.layout(model(lagna), ChartStyle.NORTH)
            cells.forEachIndexed { i, pc ->
                assertEquals(i + 1, pc.cell.house)
                assertEquals((lagna + i) % 12, pc.cell.rashi)
                assertEquals(ChartGeometry.northRashi(i + 1, lagna), pc.cell.rashi)
            }
        }
        assertEquals(11, ChartGeometry.northRashi(1, 11)) // Pisces lagna: house 1 is Meena
        assertEquals(0, ChartGeometry.northRashi(2, 11))
    }

    @Test fun northHousePositionsRunCounterClockwise() {
        val c = ChartGeometry.layout(model(0), ChartStyle.NORTH).associate { it.cell.house to centre(it) }
        assertEquals(0.5f, c.getValue(1).x, 1e-4f); assertTrue(c.getValue(1).y < 0.3f) // top diamond
        assertTrue(c.getValue(2).x < 0.5f && c.getValue(2).y < 0.15f) // top-left upper triangle
        assertTrue(c.getValue(4).x < 0.3f && kotlin.math.abs(c.getValue(4).y - 0.5f) < 1e-4f) // left diamond
        assertTrue(c.getValue(7).y > 0.7f && kotlin.math.abs(c.getValue(7).x - 0.5f) < 1e-4f) // bottom diamond
        assertTrue(c.getValue(10).x > 0.7f) // right diamond
        assertTrue(c.getValue(12).x > 0.5f && c.getValue(12).y < 0.15f) // top-right upper triangle
    }

    @Test fun southSignsAreFixed() {
        // Top row Pi Ar Ta Ge, right column Cn Le, bottom row right-to-left Vi Li Sc Sg, left column bottom-up Cp Aq.
        val expected = mapOf(
            11 to (0 to 0), 0 to (0 to 1), 1 to (0 to 2), 2 to (0 to 3), 3 to (1 to 3), 4 to (2 to 3),
            5 to (3 to 3), 6 to (3 to 2), 7 to (3 to 1), 8 to (3 to 0), 9 to (2 to 0), 10 to (1 to 0),
        )
        for (lagna in listOf(0, 11)) {
            val cells = ChartGeometry.layout(model(lagna), ChartStyle.SOUTH)
            for (pc in cells) {
                val (row, col) = expected.getValue(pc.cell.rashi)
                assertEquals(col / 4f, pc.left, 1e-4f); assertEquals(row / 4f, pc.top, 1e-4f)
                assertEquals((pc.cell.rashi - lagna).mod(12) + 1, pc.cell.house)
            }
        }
    }

    @Test fun eastAriesTopCentreCounterClockwise() {
        val c = ChartGeometry.layout(model(11), ChartStyle.EAST).associate { it.cell.rashi to centre(it) }
        assertEquals(0.5f, c.getValue(0).x, 1e-4f); assertTrue(c.getValue(0).y < 1 / 3f) // Mesha top-centre
        assertTrue(c.getValue(3).x < 1 / 3f && kotlin.math.abs(c.getValue(3).y - 0.5f) < 1e-4f) // Karka left
        assertTrue(c.getValue(6).y > 2 / 3f && kotlin.math.abs(c.getValue(6).x - 0.5f) < 1e-4f) // Tula bottom
        assertTrue(c.getValue(9).x > 2 / 3f && kotlin.math.abs(c.getValue(9).y - 0.5f) < 1e-4f) // Makara right
        assertTrue(c.getValue(1).x < 1 / 3f && c.getValue(1).y < c.getValue(2).y) // Vrishabha above Mithuna
    }

    @Test fun noonChartDisablesNorth() {
        assertEquals(ChartStyle.SOUTH, ChartGeometry.effective(ChartStyle.NORTH, lagnaKnown = false))
        assertEquals(listOf(ChartStyle.SOUTH, ChartStyle.EAST), ChartGeometry.available(false))
        assertEquals(12, ChartGeometry.layout(model(0, known = false), ChartStyle.NORTH).size)
    }
}
