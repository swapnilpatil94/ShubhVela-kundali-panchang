package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.feature.calendar.grahan.GrahanItems
import app.parivaar.panchang.feature.calendar.ics.IcsEvents
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.feature.calendar.transits.TransitRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

class GuideModelsTest {
    private fun occ(id: String, d: LocalDate) =
        FestivalOccurrence(id, d, null, null, null, null, null, null, emptySet())

    @Test fun `ics events are sorted all-day and named by id lookup only`() {
        val list = listOf(occ("b", LocalDate(2026, 12, 1)), occ("a", LocalDate(2026, 11, 8)))
        val ev = IcsEvents.of(list) { it.uppercase() }
        assertEquals(listOf("A", "B"), ev.map { it.summary })
        assertEquals(LocalDate(2026, 11, 8), ev.first().date)
    }

    @Test fun `empty eclipse list maps to empty`() {
        assertEquals(emptyList(), GrahanItems.of(emptyList()))
    }

    @Test fun `transits sort by date then graha`() {
        val a = TransitEvent.Station(Graha.VENUS, true, LocalDate(2026, 11, 20))
        val b = TransitEvent.Station(Graha.MARS, false, LocalDate(2026, 11, 20))
        val at = Instant.parse("2026-11-16T10:00:00Z")
        val c = TransitEvent.SignChange(Graha.SUN, Rashi.MESHA, at, false)
        val out = TransitRows.sorted(listOf(a, b, c)) { LocalDate(2026, 11, 16) }
        assertEquals(listOf(c, b, a), out)
    }
}
