@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.widget

import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.panchang.Direction
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Shool
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class WidgetContentTest {
    private val date = LocalDate(2026, 11, 15)
    private val sr = Instant.parse("2026-11-15T01:00:00Z")
    private val rahu = Window(WindowKind.RAHU_KAAL, 0, null, false, sr + 9.hours, sr + 10.hours)
    private val day =
        PanchangDay(
            date, 19800, Vaar.of(date), sr, sr + 12.hours, sr + 24.hours, emptySet(), null,
            mapOf(
                LimbKind.TITHI to listOf(LimbSpan(LimbKind.TITHI, 5, sr - 2.hours, sr + 20.hours)),
                LimbKind.NAKSHATRA to listOf(LimbSpan(LimbKind.NAKSHATRA, 3, sr - 2.hours, sr + 20.hours)),
            ),
            listOf(rahu), emptySet(), Shool(Direction.EAST, "x"),
        )

    @Test
    fun todayOmitsCityUnlessOptedIn() {
        val off = WidgetContent.today(sr + 1.hours, listOf(day), false, "Pune")
        assertNull(off.city)
        assertEquals("Pune", WidgetContent.today(sr + 1.hours, listOf(day), true, "Pune").city)
        assertNull(WidgetContent.today(sr + 1.hours, listOf(day), true, " ").city)
        assertIs<WidgetModel.Today>(off)
        assertEquals(rahu, (off as WidgetModel.Today).next)
    }

    @Test
    fun beforeFirstSunriseIsUnavailable() {
        assertIs<WidgetModel.Unavailable>(WidgetContent.today(sr - 1.hours, listOf(day), false, null))
        assertIs<WidgetModel.Unavailable>(WidgetContent.rahu(sr, emptyList(), false, null))
    }

    @Test
    fun rahuActiveAtStartAndNextAfterEnd() {
        val a = WidgetContent.rahu(rahu.start, listOf(day), false, "Pune") as WidgetModel.Rahu
        assertTrue(a.active)
        assertNull(a.city)
        assertIs<WidgetModel.Unavailable>(WidgetContent.rahu(rahu.end, listOf(day), false, null))
        val n = WidgetContent.rahu(sr, listOf(day), false, null) as WidgetModel.Rahu
        assertEquals(false, n.active)
    }

    private fun occ(id: String, d: LocalDate) = FestivalOccurrence(id, d, null, null, null, null, null, null, emptySet())

    @Test
    fun festivalWithinSixtyDays() {
        val f = WidgetContent.festival(date, listOf(occ("b", LocalDate(2026, 12, 1)), occ("a", LocalDate(2026, 11, 20))), true, "Pune")
        assertEquals(WidgetModel.Festival("a", LocalDate(2026, 11, 20), 5, "Pune"), f)
        assertIs<WidgetModel.Unavailable>(WidgetContent.festival(date, listOf(occ("z", LocalDate(2027, 2, 1))), false, null))
        assertEquals(60, (WidgetContent.festival(date, listOf(occ("z", LocalDate(2027, 1, 14))), false, null) as WidgetModel.Festival).inDays)
    }

    @Test
    fun modelsHaveNoProfileFields() {
        val fields = listOf(WidgetModel.Today::class, WidgetModel.Rahu::class, WidgetModel.Festival::class, WidgetModel.Unavailable::class)
            .flatMap { c -> c.java.declaredFields.map { it.name.lowercase() } }
        for (bad in listOf("name", "profile", "birth", "lat", "lon", "place")) assertTrue(fields.none { bad in it }, bad)
        assertEquals(4, fields.count { it == "city" })
    }
}
