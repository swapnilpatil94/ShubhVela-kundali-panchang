@file:Suppress("MaxLineLength")
package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.designsystem.components.OrbTone
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.Synthetic.chog
import app.parivaar.panchang.feature.aaj.Synthetic.day
import app.parivaar.panchang.feature.aaj.Synthetic.t
import app.parivaar.panchang.feature.aaj.Synthetic.win
import app.parivaar.panchang.feature.aaj.ui.OrbTexts
import app.parivaar.panchang.feature.aaj.ui.orbUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class OrbLogicTest {
    private val texts = OrbTexts({ "T${it.epochSeconds}" }, { "good $it" }, { "wait $it" }, "neutral")

    private fun orb(now: Double, days: List<app.parivaar.panchang.engine.panchang.PanchangDay>, header: Header? = null) =
        t(now).let { n -> orbUi(header ?: HeaderRule.evaluate(n, days), days, n, texts) }

    // Day 0: sunrise 06:00, sunset 18:00; parts of 1.5 h in the day, 1.5 h at night (next sunrise 30:00).
    private val codes = listOf("UDVEG", "CHAR", "LABH", "AMRIT", "KAAL", "SHUBH", "ROG", "UDVEG")
    private val nightCodes = listOf("SHUBH", "AMRIT", "CHAR", "ROG", "KAAL", "LABH", "UDVEG", "SHUBH")
    private fun fullDay(extra: List<app.parivaar.panchang.engine.panchang.Window> = emptyList()) = day(
        0,
        codes.mapIndexed { i, c -> chog(c, 6 + 1.5 * i, 7.5 + 1.5 * i) } +
            nightCodes.mapIndexed { i, c -> win(WindowKind.CHOGHADIYA, 18 + 1.5 * i, 19.5 + 1.5 * i, c, night = true) } + extra,
    )

    @Test fun goodPartWithoutAvoidWindowIsGoodWithRemainingFraction() {
        val d = listOf(fullDay())
        val o = orb(8.0, d) // LABH 07:30-09:00? index 2 is 9:00-10:30; 8.0 is CHAR 7.5-9.0
        assertEquals(OrbTone.NEUTRAL, o.tone)
        val g = orb(9.75, d) // LABH 9.0-10.5
        assertEquals(OrbTone.GOOD, g.tone)
        assertEquals(((10.5 - 9.75) / 1.5).toFloat(), g.fractionLeft!!, 1e-6f)
        assertEquals("good T${t(12.0).epochSeconds}", g.a11y) // header merges LABH+AMRIT
    }

    @Test fun rahuKaalIsAvoidUntilItsEndWhateverTheChoghadiya() {
        val d = listOf(fullDay(listOf(win(WindowKind.RAHU_KAAL, 9.5, 11.0))))
        val o = orb(10.0, d)
        assertEquals(OrbTone.AVOID, o.tone)
        assertEquals("T${t(11.0).epochSeconds}", o.untilText)
        assertEquals("wait T${t(11.0).epochSeconds}", o.a11y)
        assertEquals(((10.5 - 10.0) / 1.5).toFloat(), o.fractionLeft!!, 1e-6f) // ring still shows the choghadiya
    }

    @Test fun avoidChoghadiyaWithoutAbhijitIsAvoidAndAbhijitInsideItIsGood() {
        val d = listOf(fullDay())
        assertEquals(OrbTone.AVOID, orb(12.0, d).tone) // KAAL 12.0-13.5
        val withAbhijit = listOf(fullDay(listOf(win(WindowKind.ABHIJIT, 11.9, 12.7))))
        assertEquals(OrbTone.GOOD, orb(12.0, withAbhijit).tone)
        assertEquals("good T${t(12.7).epochSeconds}", orb(12.0, withAbhijit).a11y)
    }

    @Test fun headerRuleWinsOverChoghadiya() {
        val d = listOf(fullDay())
        val o = orb(12.0, d, Header.GoodUntil(t(12.5)))
        assertEquals(OrbTone.GOOD, o.tone)
        val w = orb(10.0, d, Header.WaitUntil(WindowKind.VARJYAM, t(10.2), null))
        assertEquals(OrbTone.AVOID, w.tone)
    }

    @Test fun boundaryInstantBelongsToTheNextPart() {
        val d = listOf(fullDay())
        val atEnd = orb(10.5, d) // AMRIT starts exactly here, LABH ends
        assertEquals(1f, atEnd.fractionLeft!!, 1e-6f)
        assertEquals(OrbTone.GOOD, atEnd.tone)
    }

    @Test fun acrossMidnightNightPartsAreFoundAndFractionUsesRealDuration() {
        val d = listOf(fullDay(), day(1))
        val o = orb(24.5, d) // night part 7 (18+10.5=28.5..30)? parts: i=4 -> 24.0-25.5 KAAL
        assertEquals(OrbTone.AVOID, o.tone)
        assertEquals(((25.5 - 24.5) / 1.5).toFloat(), o.fractionLeft!!, 1e-6f)
        // A 25 hour civil day changes nothing: only instants count.
        val odd = day(0, listOf(chog("SHUBH", 6.0, 6.0 + 1.25)))
        assertEquals(0.5f, orb(6.625, listOf(odd)).fractionLeft!!, 1e-6f)
    }

    @Test fun noChoghadiyaIsNeutralWithNullFraction() {
        val d = listOf(day(0))
        val o = orb(9.0, d)
        assertEquals(OrbTone.NEUTRAL, o.tone)
        assertNull(o.fractionLeft)
        assertEquals("neutral", o.a11y)
        val none = orbUi(Header.Unavailable, emptyList(), Instant.fromEpochSeconds(0), texts)
        assertEquals(OrbTone.NEUTRAL, none.tone)
    }

    @Test fun orbNeverContradictsTheHeaderRuleOverTheDay() {
        val d = listOf(
            fullDay(listOf(win(WindowKind.RAHU_KAAL, 9.5, 11.0), win(WindowKind.ABHIJIT, 11.9, 12.7), win(WindowKind.VARJYAM, 20.0, 20.8))),
            day(1),
        )
        var h = 6.0
        while (h < 29.9) {
            val n = t(h)
            val header = HeaderRule.evaluate(n, d)
            val o = orbUi(header, d, n, texts)
            if (header is Header.GoodUntil) assertEquals(OrbTone.GOOD, o.tone, "h=$h")
            if (header is Header.WaitUntil) assertEquals(OrbTone.AVOID, o.tone, "h=$h")
            if (o.tone == OrbTone.GOOD) assertEquals(true, header is Header.GoodUntil, "h=$h")
            h += 5.0 / 60.0
        }
    }

    // ---- M07R acceptance 4: values from the engine run (Pune 2026-11-15 +05:30; Tromso 2026-12-15), never from the PNG.
    private val root = java.io.File(System.getProperty("pp.root.dir"))
    private val calc = app.parivaar.panchang.engine.panchang.PanchangCalculator(
        app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris(),
        app.parivaar.panchang.engine.panchang.PanchangRules.parse(
            java.io.File(root, "rules/durmuhurta.json").readText(), java.io.File(root, "rules/choghadiya.json").readText(),
        ),
    )
    private val puneDays by lazy {
        calc.days(kotlinx.datetime.LocalDate(2026, 11, 14), 3, app.parivaar.panchang.engine.Place(18.5204, 73.8567, 560.0), app.parivaar.panchang.engine.CalcSettings()) { 19_800 }
    }
    private val puneDay get() = puneDays.first { it.date == kotlinx.datetime.LocalDate(2026, 11, 15) }
    private fun minutes(w: app.parivaar.panchang.engine.panchang.Window) =
        generateSequence(w.start) { it + kotlin.time.Duration.parse("1m") }.takeWhile { it < w.end }
    private fun engineOrb(n: Instant) = orbUi(HeaderRule.evaluate(n, puneDays), puneDays, n, texts)
    private fun dayParts(vararg codes: String) =
        puneDay.windows.filter { it.kind == WindowKind.CHOGHADIYA && !it.night && it.code in codes }

    @Test fun puneEngineGoodPartWithoutIntervalIsGoodWithExactFraction() {
        val (part, n) = dayParts("SHUBH", "AMRIT", "LABH").asSequence()
            .flatMap { p -> minutes(p).map { p to it } }
            .first { (_, m) -> HeaderRule.evaluate(m, puneDays) is Header.GoodUntil }
        val o = engineOrb(n)
        assertEquals(OrbTone.GOOD, o.tone)
        assertEquals(((part.end - n) / (part.end - part.start)).toFloat(), o.fractionLeft!!, 1e-6f)
    }

    @Test fun puneEngineRahuKaalIsAvoidUntilRahuEnd() {
        val rahu = puneDay.windows.single { it.kind == WindowKind.RAHU_KAAL }
        for (n in minutes(rahu)) {
            val o = engineOrb(n)
            assertEquals(OrbTone.AVOID, o.tone, "at $n")
            assertEquals("T${rahu.end.epochSeconds}", o.untilText, "at $n")
        }
    }

    @Test fun puneEngineCharWithoutIntervalIsNeutralAndAvoidPartsWithoutAbhijitAreAvoid() {
        val charMinute = dayParts("CHAR").asSequence().flatMap { minutes(it) }
            .first { HeaderRule.evaluate(it, puneDays).let { h -> h !is Header.WaitUntil && h !is Header.GoodUntil } }
        assertEquals(OrbTone.NEUTRAL, engineOrb(charMinute).tone)
        val abhijit = puneDay.windows.firstOrNull { it.kind == WindowKind.ABHIJIT }
        val avoidMinute = dayParts("ROG", "KAAL", "UDVEG").asSequence().flatMap { minutes(it) }
            .first { m -> abhijit == null || m !in abhijit.start..<abhijit.end }
        assertEquals(OrbTone.AVOID, engineOrb(avoidMinute).tone)
    }

    @Test fun puneEngineBoundaryInstantIsTheNextPart() {
        val parts = puneDay.windows.filter { it.kind == WindowKind.CHOGHADIYA && !it.night }.sortedBy { it.start }
        val next = parts[1]
        assertEquals(next, app.parivaar.panchang.feature.aaj.ui.currentChoghadiya(puneDays, next.start))
        assertEquals(1f, engineOrb(next.start).fractionLeft!!, 1e-6f)
    }

    @Test fun puneEngineOrbAgreesWithAbhiStateEveryFiveMinutes() {
        var n = puneDay.sunrise
        while (n < puneDay.nextSunrise) {
            val h = HeaderRule.evaluate(n, puneDays)
            val o = orbUi(h, puneDays, n, texts)
            val abhi = app.parivaar.panchang.feature.aaj.ui.abhiStateOf(h)
            assertEquals(abhi == app.parivaar.panchang.feature.aaj.ui.AbhiState.GOOD, o.tone == OrbTone.GOOD, "at $n")
            if (abhi == app.parivaar.panchang.feature.aaj.ui.AbhiState.WAIT) assertEquals(OrbTone.AVOID, o.tone, "at $n")
            n += kotlin.time.Duration.parse("5m")
        }
    }

    @Test fun tromsoPolarNightEngineIsNeutralWithNullFraction() {
        val place = app.parivaar.panchang.engine.Place(69.65, 18.96, 0.0)
        val days = calc.days(kotlinx.datetime.LocalDate(2026, 12, 14), 3, place, app.parivaar.panchang.engine.CalcSettings()) { 3_600 }
        val n = Instant.parse("2026-12-15T11:00:00Z")
        val o = orbUi(HeaderRule.evaluate(n, days), days, n, texts)
        assertEquals(OrbTone.NEUTRAL, o.tone)
        assertNull(o.fractionLeft)
    }
}
