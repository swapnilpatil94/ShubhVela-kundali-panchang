@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangCalculator
import app.parivaar.panchang.engine.panchang.PanchangRules
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.feature.aaj.Synthetic.day
import app.parivaar.panchang.feature.aaj.Synthetic.t
import app.parivaar.panchang.feature.aaj.ui.OrbTexts
import app.parivaar.panchang.feature.aaj.ui.TimeFmt
import app.parivaar.panchang.feature.aaj.ui.currentLimb
import app.parivaar.panchang.feature.aaj.ui.dockState
import app.parivaar.panchang.feature.aaj.ui.vaarAt
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** M2 (current limb before sunrise), M3 (vaar and gem of the sunrise-to-sunrise day), M4 (DST-aware times). */
class PlaceTimeTest {
    // ---- M2: at 01:00 the limb current is D-1's, ending 03:30; D's sunrise span has not started.
    private fun tithi(i: Int, from: Double, to: Double) = LimbSpan(LimbKind.TITHI, i, t(from), t(to))

    @Test fun `before sunrise the limb row shows the limb current now with its true end`() {
        val prev = day(-1).copy(limbs = mapOf(LimbKind.TITHI to listOf(tithi(5, -20.0, 3.5))))
        val today = day(0).copy(limbs = mapOf(LimbKind.TITHI to listOf(tithi(6, 3.5, 28.0))))
        val s = currentLimb(listOf(prev, today), today, LimbKind.TITHI, t(1.0))!!
        assertEquals(5, s.index)
        assertEquals(t(3.5), s.end)
        val after = currentLimb(listOf(prev, today), today, LimbKind.TITHI, t(4.0))!!
        assertEquals(6, after.index)
    }

    // ---- M3: Pune, Monday 2026-11-16 02:00 IST is still Sunday's vaar (sunrise to sunrise), so Manikya.
    private val root = File(System.getProperty("pp.root.dir"))
    private val tables = PanchangRules.parse(File(root, "rules/durmuhurta.json").readText(), File(root, "rules/choghadiya.json").readText())
    private val pune = Place(18.5204, 73.8567, 560.0)

    @Test fun `Pune Monday 02h00 shows Sunday and Manikya`() {
        val calc = PanchangCalculator(AstronomyEngineEphemeris(), tables)
        val monday = LocalDate(2026, 11, 16)
        val days = calc.days(LocalDate(2026, 11, 15), 3, pune, CalcSettings()) { 19_800 }
        val now = Instant.parse("2026-11-15T20:30:00Z") // 02:00 IST on Monday
        val c = AajUi.Content(
            monday, days, null, HeaderRule.evaluate(now, days), null, null, null, emptySet(), emptySet(),
            ProofInfo("", "", "", null), false, "Asia/Kolkata",
        )
        assertEquals(Vaar.SUNDAY, vaarAt(c, now))
        assertEquals(Gem.MANIKYA, dockState(c, now, OrbTexts({ "" }, { it }, { it }, "")).gem)
        val afterSunrise = Instant.parse("2026-11-16T03:00:00Z") // 08:30 IST
        assertEquals(Vaar.MONDAY, vaarAt(c, afterSunrise))
    }

    // ---- M4: each instant in the place's real zone.
    private fun fmt(zone: String, dayOffset: Int, shown: LocalDate) =
        TimeFmt(PlaceClock(zone, dayOffset, shown), shown, "HH:mm", Locale.US)

    @Test fun `New York around the 2026-11-01 fall back`() {
        val shown = LocalDate(2026, 11, 1)
        val f = fmt("America/New_York", -14_400, shown)
        assertEquals("01:30", f.time(Instant.parse("2026-11-01T05:30:00Z"))) // EDT, before the change
        assertEquals("07:00", f.time(Instant.parse("2026-11-01T12:00:00Z"))) // EST, after it
        assertEquals("06:00", f.time(Instant.parse("2026-11-02T11:00:00Z"))) // D+1 sunrise-ish, EST
        assertEquals("20:00", f.time(Instant.parse("2026-11-01T00:00:00Z"))) // D-1 evening, EDT
        assertEquals(DayRef.Previous, f.refOf(Instant.parse("2026-11-01T03:59:00Z"))) // 23:59 EDT on Oct 31
        assertEquals(DayRef.Next, f.refOf(Instant.parse("2026-11-02T05:00:00Z"))) // 00:00 EST on Nov 2
        assertEquals(DayRef.Same, f.refOf(Instant.parse("2026-11-02T04:30:00Z"))) // 23:30 EST on Nov 1
    }

    @Test fun `London around the 2026-03-29 spring forward`() {
        val shown = LocalDate(2026, 3, 29)
        val f = fmt("Europe/London", 0, shown)
        assertEquals("00:30", f.time(Instant.parse("2026-03-29T00:30:00Z"))) // GMT
        assertEquals("13:00", f.time(Instant.parse("2026-03-29T12:00:00Z"))) // BST
        assertEquals("06:00", f.time(Instant.parse("2026-03-30T05:00:00Z"))) // D+1, BST
        assertEquals(DayRef.Next, f.refOf(Instant.parse("2026-03-29T23:30:00Z"))) // 00:30 BST on Mar 30
    }

    @Test fun `Kolkata unchanged`() {
        val shown = LocalDate(2026, 11, 15)
        val f = fmt("Asia/Kolkata", 19_800, shown)
        assertEquals("10:00", f.time(Instant.parse("2026-11-15T04:30:00Z")))
        assertEquals("05:29", f.time(Instant.parse("2026-11-14T23:59:00Z")))
        assertEquals(19_800, f.offsetAt(Instant.parse("2026-11-15T04:30:00Z")))
    }

    @Test fun `an offset the zone rules never give on that day (historical overlay) stays fixed`() {
        val shown = LocalDate(1950, 6, 1)
        val c = PlaceClock("Asia/Kolkata", 17_460, shown) // Bombay time overlay
        assertEquals(17_460, c.offsetAt(Instant.parse("1950-06-01T06:00:00Z")))
        assertEquals(-3_600, PlaceClock("Not/AZone", -3_600, shown).offsetAt(Instant.parse("1950-06-01T06:00:00Z")))
    }
}
