package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.panchang.internal.DayWindowSet
import app.parivaar.panchang.engine.panchang.internal.DayWindows
import app.parivaar.panchang.engine.panchang.internal.SunDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** docs/specs/M03.md section 4, `DayWindowsTest`: synthetic sun events, no ephemeris. */
class DayWindowsTest {
    private val sr = Instant.parse("2026-11-15T00:00:00Z")
    private fun t(hhmm: String): Instant = Instant.parse("2026-11-15T$hhmm:00Z")

    private val cycle = Choghadiya.entries

    /** Night choghadiya by the 3.4 rule (VERIFY): n0 = index of the lord of the weekday 4 days later. */
    private fun nightRule(v: Vaar): List<String> {
        val lord = Vaar.entries[(v.ordinal + 4) % 7].lord
        val n0 = cycle.indexOfFirst { it.lord == lord }
        return (1..8).map { p -> cycle[(n0 - 2 * (p - 1)).mod(7)].name }
    }

    private val durmuhurta = mapOf(
        Vaar.SUNDAY to listOf(DurmuhurtaSlot(false, 14)),
        Vaar.MONDAY to listOf(DurmuhurtaSlot(false, 9), DurmuhurtaSlot(false, 12)),
        Vaar.TUESDAY to listOf(DurmuhurtaSlot(false, 4), DurmuhurtaSlot(true, 7)),
        Vaar.WEDNESDAY to listOf(DurmuhurtaSlot(false, 8)),
        Vaar.THURSDAY to listOf(DurmuhurtaSlot(false, 6), DurmuhurtaSlot(false, 12)),
        Vaar.FRIDAY to listOf(DurmuhurtaSlot(false, 4), DurmuhurtaSlot(false, 9)),
        Vaar.SATURDAY to listOf(DurmuhurtaSlot(false, 1), DurmuhurtaSlot(false, 2)),
    )

    private fun tables(varjyam: VarjyamTable? = null) = PanchangTables(
        durmuhurta,
        EightPartTable(emptyMap(), Vaar.entries.associateWith { nightRule(it) }),
        gowri = null,
        varjyam = varjyam,
    )

    private fun windows(
        v: Vaar,
        s: CalcSettings = CalcSettings(),
        sun: SunDay = SunDay(sr - 5.hours, sr, sr + 12.hours, sr + 24.hours, emptySet()),
        limbs: Map<LimbKind, List<LimbSpan>> = emptyMap(),
        varjyam: VarjyamTable? = null,
        rashiAt: (Instant) -> LimbSpan = { error("not needed") },
    ): DayWindowSet = DayWindows(sun, v, s, tables(varjyam)).compute(limbs, rashiAt)

    private fun DayWindowSet.of(kind: WindowKind, night: Boolean? = null): List<Window> =
        windows.filter { it.kind == kind && (night == null || it.night == night) }

    @Test
    fun rahuKaalAbhijitBrahma() {
        val sun = windows(Vaar.SUNDAY)
        val rahu = sun.of(WindowKind.RAHU_KAAL).single()
        assertEquals(8, rahu.part)
        assertEquals(t("10:30") to t("12:00"), rahu.start to rahu.end)
        val monday = windows(Vaar.MONDAY).of(WindowKind.RAHU_KAAL).single()
        assertEquals(t("01:30") to t("03:00"), monday.start to monday.end)
        val abhijit = sun.of(WindowKind.ABHIJIT).single()
        assertEquals(t("05:36") to t("06:24"), abhijit.start to abhijit.end)
        val brahma = sun.of(WindowKind.BRAHMA_MUHURTA).single()
        assertEquals(t("22:24") to t("23:12"), brahma.start to brahma.end)
        assertTrue(brahma.night)
        val dur = sun.of(WindowKind.DURMUHURTA).single()
        assertEquals(14, dur.part)
        assertEquals(t("10:24") to t("11:12"), dur.start to dur.end)
        val tuesdayNight = windows(Vaar.TUESDAY).of(WindowKind.DURMUHURTA, night = true).single()
        assertEquals(sr + 12.hours + 48.minutes * 6, tuesdayNight.start)
    }

    @Test
    fun horaAndChoghadiya() {
        val sun = windows(Vaar.SUNDAY)
        val horas = sun.of(WindowKind.HORA)
        assertEquals(24, horas.size)
        assertEquals(Graha.SUN.name, horas.single { !it.night && it.part == 1 }.code)
        assertEquals(Graha.VENUS.name, horas.single { !it.night && it.part == 2 }.code)
        // Hora 13 = the first night hora (part 1 of the night).
        assertEquals(Graha.JUPITER.name, horas.single { it.night && it.part == 1 }.code)
        assertEquals(t("12:00"), horas.single { it.night && it.part == 1 }.start)
        val day = sun.of(WindowKind.CHOGHADIYA, night = false).map { it.code }
        assertEquals(listOf("UDVEG", "CHAR", "LABH", "AMRIT", "KAAL", "SHUBH", "ROG", "UDVEG"), day)
        val night = sun.of(WindowKind.CHOGHADIYA, night = true).map { it.code }
        assertEquals(listOf("SHUBH", "AMRIT", "CHAR", "ROG", "KAAL", "LABH", "UDVEG", "SHUBH"), night)
        assertEquals((1..8).toList(), sun.of(WindowKind.CHOGHADIYA, night = true).map { it.part })
        assertEquals(setOf(WindowKind.GOWRI, WindowKind.VARJYAM, WindowKind.AMRIT_KAAL), sun.unavailable)
    }

    @Test
    fun abhijitOnWednesdayOnlyWithTheSetting() {
        assertTrue(windows(Vaar.WEDNESDAY).of(WindowKind.ABHIJIT).isEmpty())
        assertEquals(1, windows(Vaar.WEDNESDAY, CalcSettings(abhijitOnWednesday = true)).of(WindowKind.ABHIJIT).size)
    }

    @Test
    fun printedSixAm() {
        // Civil date 2026-11-15 at +05:30: local midnight 2026-11-14T18:30Z.
        val midnight = Instant.parse("2026-11-14T18:30:00Z")
        val sun = SunDay(midnight, sr, sr + 12.hours, sr + 24.hours, emptySet())
        val printed = windows(Vaar.SUNDAY, CalcSettings(timingStyle = TimingStyle.PRINTED_6AM), sun)
        val rahu = printed.of(WindowKind.RAHU_KAAL).single()
        assertEquals(t("11:00") to t("12:30"), rahu.start to rahu.end)
        // Only the three weekday parts move; the rest stays on the actual day.
        assertEquals(windows(Vaar.SUNDAY).of(WindowKind.HORA), printed.of(WindowKind.HORA))
    }

    @Test
    fun partsTileBitEqualAndAreExact() {
        val odd = SunDay(sr - 5.hours, sr, sr + 13.hours, sr + 23.hours + 17.minutes + 1.milliseconds / 3, emptySet())
        val set = windows(Vaar.FRIDAY, sun = odd)
        for (night in listOf(false, true)) {
            for (kind in listOf(WindowKind.CHOGHADIYA, WindowKind.HORA)) {
                val parts = set.of(kind, night)
                assertEquals(if (night) odd.sunset else odd.sunrise, parts.first().start)
                assertEquals(if (night) odd.nextSunrise else odd.sunset, parts.last().end)
                for (i in 1 until parts.size) assertEquals(parts[i - 1].end, parts[i].start, "$kind tiles")
            }
        }
        for (w in set.of(WindowKind.CHOGHADIYA, night = false)) assertEquals(97.5.minutes, w.end - w.start)
        assertEquals(set.windows.sortedWith(compareBy({ it.start }, { it.kind.ordinal }, { it.part })), set.windows)
    }

    @Test
    fun varjyamOffsets() {
        val rows = List(27) { listOf(10.0) }
        val nak = LimbSpan(LimbKind.NAKSHATRA, 5, sr, sr + 24.hours)
        val limbs = mapOf(LimbKind.NAKSHATRA to listOf(nak))
        val set = windows(Vaar.SUNDAY, limbs = limbs, varjyam = VarjyamTable(rows, rows))
        val v = set.of(WindowKind.VARJYAM).single()
        assertEquals(sr + 4.hours to sr + 5.hours + 36.minutes, v.start to v.end)
        assertEquals(5, v.part)
        assertEquals(1, set.of(WindowKind.AMRIT_KAAL).size)
        assertEquals(setOf(WindowKind.GOWRI), set.unavailable)
    }

    @Test
    fun bhadraAndPanchakFromSpans() {
        val vishti = LimbSpan(LimbKind.KARANA, 8, sr - 3.hours, sr + 3.hours)
        val bava = LimbSpan(LimbKind.KARANA, 9, sr + 3.hours, sr + 30.hours)
        val meena = LimbSpan(LimbKind.MOON_RASHI, 11, sr - 10.hours, sr + 40.hours)
        val kumbha = LimbSpan(LimbKind.MOON_RASHI, 10, sr - 60.hours, sr - 10.hours)
        val limbs = mapOf(LimbKind.KARANA to listOf(vishti, bava), LimbKind.MOON_RASHI to listOf(meena))
        val set = windows(Vaar.SUNDAY, limbs = limbs, rashiAt = { at ->
            assertEquals(meena.start - 60_000.milliseconds, at)
            kumbha
        })
        val bhadra = set.of(WindowKind.BHADRA).single()
        assertEquals(Triple(8, vishti.start, vishti.end), Triple(bhadra.part, bhadra.start, bhadra.end))
        val panchak = set.of(WindowKind.PANCHAK).single()
        assertEquals(Triple(0, kumbha.start, meena.end), Triple(panchak.part, panchak.start, panchak.end))
    }
}
