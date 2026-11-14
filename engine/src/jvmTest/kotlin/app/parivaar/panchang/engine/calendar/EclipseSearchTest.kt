package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.RiseSet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** docs/specs/M04.md section 4, `EclipseSearchTest` (Precision backend unless named). */
class EclipseSearchTest {
    private val search = EclipseSearch(GoldenLoader.openDe440())
    private val dallas = Place(32.7767, -96.7970)
    private val delhi = Place(28.6139, 77.2090)
    private val newYork = Place(40.7128, -74.0060)
    private val thiruvananthapuram = Place(8.5241, 76.9366)

    private fun at(text: String) = Instant.parse(text)

    private fun secondsBetween(a: Instant, b: Instant) = abs((a - b).inWholeMilliseconds) / 1_000.0

    private fun one(s: EclipseSearch, from: String, to: String, solar: Boolean): GlobalEclipse =
        s.global(at(from), at(to)).single { it.solar == solar }

    @Test
    fun dallas8April2024IsTotalWithOrderedContactsAndSutak() {
        val g = one(search, "2024-04-07T00:00:00Z", "2024-04-10T00:00:00Z", solar = true)
        assertTrue(g.contacts.isEmpty())
        assertNull(g.lunarKind)
        val l = assertNotNull(search.local(g, dallas))
        assertEquals(EclipseKind.SOLAR_TOTAL, l.kind)
        assertEquals(listOf(Contact.C1, Contact.C2, Contact.C3, Contact.C4), l.contacts.keys.toList())
        val c = l.contacts.values.toList()
        assertTrue(c.zipWithNext().all { (a, b) -> a < b })
        assertTrue(l.maxAt > c[1] && l.maxAt < c[2])
        assertTrue(l.magnitude > 1.0)
        // Totality in Dallas began at about 18:40:40 UTC and lasted close to 3 min 50 s.
        assertTrue(secondsBetween(c[1], at("2024-04-08T18:40:40Z")) <= 120.0, "second contact off")
        assertTrue(abs((c[2] - c[1]).inWholeSeconds - 230) <= 20, "totality length off")
        // Early afternoon in Dallas: the whole eclipse is above the horizon.
        assertEquals(TimeRange(c[0], c[3]), l.visible)
        assertEquals(TimeRange(c[0] - 12.hours, c[3]), l.sutak)
    }

    @Test
    fun baseBackendAlsoFindsTotalityInDallas() {
        val base = EclipseSearch(AstronomyEngineEphemeris())
        val g = one(base, "2024-04-07T00:00:00Z", "2024-04-10T00:00:00Z", solar = true)
        assertEquals(EclipseKind.SOLAR_TOTAL, assertNotNull(base.local(g, dallas)).kind)
    }

    @Test
    fun thiruvananthapuram15January2010IsAnnular() {
        val g = one(search, "2010-01-14T00:00:00Z", "2010-01-17T00:00:00Z", solar = true)
        val l = assertNotNull(search.local(g, thiruvananthapuram))
        assertEquals(EclipseKind.SOLAR_ANNULAR, l.kind)
        assertTrue(l.magnitude < 1.0)
        assertEquals(setOf(Contact.C1, Contact.C2, Contact.C3, Contact.C4), l.contacts.keys)
        assertNotNull(l.visible)
    }

    @Test
    fun march2025LunarTotalGlobal() {
        val g = one(search, "2025-03-13T00:00:00Z", "2025-03-16T00:00:00Z", solar = false)
        assertEquals(EclipseKind.LUNAR_TOTAL, g.lunarKind)
        assertTrue(secondsBetween(g.greatest, at("2025-03-14T06:58:45Z")) <= 120.0, "greatest off")
        val um = assertNotNull(g.umbralMag)
        assertTrue(um > 1.1 && um < 1.25, "umbral magnitude off")
        assertTrue(assertNotNull(g.penumbralMag) > um)
        val order = listOf(Contact.P1, Contact.U1, Contact.U2, Contact.U3, Contact.U4, Contact.P4)
        assertEquals(order, g.contacts.keys.toList())
        assertTrue(g.contacts.values.zipWithNext().all { (a, b) -> a < b })
        assertTrue(g.contacts.getValue(Contact.U2) < g.greatest && g.greatest < g.contacts.getValue(Contact.U3))
    }

    @Test
    fun lunarSutakIsNineHoursBeforeU1AndNoneWhenInvisible() {
        val g = one(search, "2025-03-13T00:00:00Z", "2025-03-16T00:00:00Z", solar = false)
        val ny = search.local(g, newYork)!!
        assertEquals(EclipseKind.LUNAR_TOTAL, ny.kind)
        assertEquals(g.umbralMag, ny.magnitude)
        assertEquals(g.contacts, ny.contacts)
        assertEquals(g.greatest, ny.maxAt)
        assertNotNull(ny.visible)
        assertEquals(TimeRange(g.contacts.getValue(Contact.U1) - 9.hours, g.contacts.getValue(Contact.U4)), ny.sutak)
        // Midday in Delhi: the Moon is below the horizon for the whole umbral phase.
        val d = search.local(g, delhi)!!
        assertNull(d.visible)
        assertNull(d.sutak)
    }

    @Test
    fun penumbralLunarHasNoSutakEvenWhenVisible() {
        val g = one(search, "2024-03-24T00:00:00Z", "2024-03-27T00:00:00Z", solar = false)
        assertEquals(EclipseKind.LUNAR_PENUMBRAL, g.lunarKind)
        assertFalse(Contact.U1 in g.contacts)
        val l = search.local(g, newYork)!!
        assertEquals(g.penumbralMag, l.magnitude)
        val v = assertNotNull(l.visible)
        assertTrue(v.start >= g.contacts.getValue(Contact.P1) && v.end <= g.contacts.getValue(Contact.P4))
        assertNull(l.sutak)
    }

    @Test
    fun solarEclipseOutOfReachIsNullAndBelowTheHorizonHasNoSutak() {
        // 4 December 2021 (Antarctic totality) does not reach Delhi even through the Earth.
        val far = one(search, "2021-12-03T00:00:00Z", "2021-12-06T00:00:00Z", solar = true)
        assertNull(search.local(far, delhi))
        // 8 April 2024 overlaps from Delhi geometrically, at night: partial, invisible, no sutak.
        val g = one(search, "2024-04-07T00:00:00Z", "2024-04-10T00:00:00Z", solar = true)
        val l = assertNotNull(search.local(g, delhi))
        assertEquals(EclipseKind.SOLAR_PARTIAL, l.kind)
        assertEquals(setOf(Contact.C1, Contact.C4), l.contacts.keys)
        assertNull(l.visible)
        assertNull(l.sutak)
    }

    @Test
    fun globalRangeIsHalfOpenAndIndependentOfTheWindow() {
        val year = search.global(at("2024-01-01T00:00:00Z"), at("2025-01-01T00:00:00Z"))
        assertEquals(year.sortedWith(compareBy<GlobalEclipse> { it.greatest }.thenBy { it.solar }), year)
        assertTrue(year.count { it.solar } >= 2)
        val first = year.first()
        val again = search.global(first.greatest, first.greatest + 1.hours).single()
        assertEquals(first, again)
        assertTrue(search.global(first.greatest - 1.hours, first.greatest).isEmpty())
        assertTrue(search.global(first.greatest, first.greatest).isEmpty())
        val e = assertFailsWith<IllegalArgumentException> { search.global(first.greatest, first.greatest - 1.hours) }
        assertFalse(e.message.orEmpty().any(Char::isDigit))
    }

    @Test
    fun topocentricIsAUnitVectorWithParallax() {
        val rs = RiseSet(GoldenLoader.openDe440())
        val t = at("2024-04-08T18:42:00Z")
        val moon = rs.topocentric(Body.MOON, dallas, t)
        val sun = rs.topocentric(Body.SUN, dallas, t)
        assertTrue(abs(moon.unit.norm() - 1.0) < 1e-12 && abs(sun.unit.norm() - 1.0) < 1e-12)
        assertTrue(moon.distanceKm in 350_000.0..410_000.0)
        assertTrue(sun.distanceKm in 1.47e8..1.53e8)
        assertFalse(moon.toString().any(Char::isDigit))
    }
}
