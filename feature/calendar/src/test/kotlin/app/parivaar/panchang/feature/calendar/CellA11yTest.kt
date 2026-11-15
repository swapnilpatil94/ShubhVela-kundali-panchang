package app.parivaar.panchang.feature.calendar

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlinx.datetime.LocalDate

class CellA11yTest {
    private val names = object : CalendarNames {
        override fun fullDate(date: LocalDate) = "Sunday 15 November 2026"
        override fun tithiName(tithi: Int) = "Tithi$tithi"
        override fun festivalName(id: String) = "Fest-$id"
        override fun markerName(marker: Marker) = "M-${marker.name}"
        override fun todayWord() = "today"
        override fun kshayaWord() = "kshaya"
        override fun vriddhiWord() = "vriddhi"
    }

    private fun cell(mark: TithiMark, ids: List<String> = emptyList(), markers: Set<Marker> = emptySet()) = DayCell(
        LocalDate(2026, 11, 15), true, false, 7, mark, 3, markers, ids, Fx.day(LocalDate(2026, 11, 15), 7).amanta, null,
    )

    @Test fun `contains date tithi first festival and marks`() {
        val s = CellA11y.describe(cell(TithiMark.KSHAYA_NEXT, listOf("a", "b"), setOf(Marker.PURNIMA)), names)
        assertContains(s, "Sunday 15 November 2026")
        assertContains(s, "Tithi7")
        assertContains(s, "Fest-a")
        assertFalse("Fest-b" in s)
        assertContains(s, "kshaya")
        assertContains(s, "M-PURNIMA")
        assertContains(CellA11y.describe(cell(TithiMark.VRIDDHI), names), "vriddhi")
        assertFalse("kshaya" in CellA11y.describe(cell(TithiMark.NONE), names))
    }
}
