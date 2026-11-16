package app.parivaar.panchang.core.designsystem.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChartA11yTest {
    private val names = object : ChartNames {
        private val rashis = listOf(
            "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
            "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces",
        )
        private val grahas = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
        override fun rashi(index: Int) = rashis[index]
        override fun graha(code: Int) = grahas[code]
        override fun house(number: Int) = "House $number"
        override val lagna = "lagna"
        override val retrograde = "retrograde"
        override val noPlanets = "no planets"
    }

    @Test fun houseOnePiscesLagnaWithPlanets() {
        val marks = listOf(GrahaMark(0, "Su", "12°", false), GrahaMark(3, "Me", null, true))
        val c = HouseCell(1, 11, marks, lagna = true)
        assertEquals("House 1, Pisces, lagna. Sun 12°, Mercury retrograde", ChartA11y.describe(c, names))
    }

    @Test fun emptyHouseSaysNoPlanets() {
        assertEquals("House 5, Leo. no planets", ChartA11y.describe(HouseCell(5, 4, emptyList(), false), names))
    }

    @Test fun noonChartHasNoHouseNumbersOrLagna() {
        val text = ChartA11y.describe(HouseCell(1, 11, emptyList(), lagna = true), names, lagnaKnown = false)
        assertFalse(text.contains("House")); assertFalse(text.contains("lagna"))
        assertEquals("Pisces. no planets", text)
    }

    @Test fun oneDescriptionPerHouse() {
        val m = ChartModel((1..12).map { HouseCell(it, it - 1, emptyList(), it == 1) }, true)
        val texts = ChartGeometry.layout(m, ChartStyle.NORTH).map { ChartA11y.describe(it.cell, names) }
        assertEquals(12, texts.toSet().size)
    }
}
