package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.calendar.internal.MasaNaming
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.panchang.LimbKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/** docs/specs/M04.md section 4, `LunarCalendarTest` (Precision backend). */
class LunarCalendarTest {
    private val cal = LunarCalendar(GoldenLoader.openDe440())
    private val y2000 = Instant.parse("2000-01-01T00:00:00Z")
    private val y2030 = Instant.parse("2030-01-01T00:00:00Z")

    private fun at(text: String) = Instant.parse(text)

    @Test
    fun newMoonOf8April2024MatchesPublishedMinute() {
        val nm = cal.newMoons(at("2024-04-07T00:00:00Z"), at("2024-04-10T00:00:00Z")).single()
        val err = abs((nm - at("2024-04-08T18:21:00Z")).inWholeMilliseconds) / 1_000.0
        assertTrue(err <= 120.0, "new moon off the published minute")
        val fm = cal.fullMoons(at("2024-04-20T00:00:00Z"), at("2024-04-26T00:00:00Z")).single()
        assertEquals(cal.lunationAt(nm + 1.days).fullMoon, fm)
    }

    @Test
    fun adhikaJyeshtha2026() {
        val june1 = at("2026-06-01T00:00:00Z")
        val l = cal.lunationAt(june1)
        assertTrue(l.adhika)
        assertEquals(Masa.JYESHTHA, l.masa)
        assertTrue(l.sankrantis.isEmpty())
        val next = cal.lunationAt(l.nextNewMoon)
        assertFalse(next.adhika)
        assertEquals(Masa.JYESHTHA, next.masa)
        assertEquals(Rashi.MITHUNA, next.sankrantis.single().into)
        // Purnimanta day labels: Delhi sunrise is near 00:00 UTC.
        val may10 = at("2026-05-10T00:30:00Z")
        val lMay = cal.lunationAt(may10)
        val tMay = cal.limbs.spanAt(LimbKind.TITHI, may10).index
        assertTrue(tMay >= 16)
        assertEquals(Masa.VAISHAKHA, cal.label(lMay, tMay, MonthSystem.AMANTA).masa)
        assertEquals(LunarLabel(Masa.JYESHTHA, false, null), cal.label(lMay, tMay, MonthSystem.PURNIMANTA))
        val tJune = cal.limbs.spanAt(LimbKind.TITHI, june1).index
        assertEquals(LunarLabel(Masa.JYESHTHA, true, null), cal.label(l, tJune, MonthSystem.PURNIMANTA))
        assertEquals(LunarLabel(Masa.JYESHTHA, true, null), cal.label(l, 3, MonthSystem.PURNIMANTA))
        assertEquals(LunarLabel(Masa.JYESHTHA, true, null), cal.label(l, 20, MonthSystem.PURNIMANTA))
        assertEquals(LunarLabel(Masa.JYESHTHA, false, null), cal.label(next, 3, MonthSystem.PURNIMANTA))
        assertEquals(LunarLabel(Masa.ASHADHA, false, null), cal.label(next, 20, MonthSystem.PURNIMANTA))
        assertFailsWith<IllegalArgumentException> { cal.label(l, 0, MonthSystem.AMANTA) }
    }

    @Test
    fun lunations2000To2030TileAndAgree() {
        val all = cal.lunations(y2000, y2030)
        assertTrue(all.size in 370..373)
        for ((a, b) in all.zipWithNext()) assertEquals(a.nextNewMoon, b.newMoon)
        assertEquals(all.map { it.newMoon }, cal.newMoons(y2000, y2030))
        assertEquals(all.map { it.fullMoon }, cal.fullMoons(all.first().newMoon, all.last().nextNewMoon))
        val fresh = LunarCalendar(GoldenLoader.openDe440())
        for (l in all.filterIndexed { i, _ -> i % 17 == 0 }) {
            assertEquals(l, fresh.lunationAt(l.newMoon + 9.days + 7.hours))
            assertEquals(l, fresh.lunationAt(l.newMoon))
            assertEquals(l, fresh.lunationAt(l.nextNewMoon - 1.nanoseconds))
        }
        val mid = at("2013-07-19T13:00:00Z")
        assertEquals(all.filter { it.newMoon >= mid }, fresh.lunations(mid, y2030))
        val sk = all.flatMap { it.sankrantis }
        assertEquals(sk, cal.sankrantis(all.first().newMoon, all.last().nextNewMoon))
        for ((a, b) in sk.zipWithNext()) assertEquals((a.into.ordinal + 1) % 12, b.into.ordinal)
        for (l in all) {
            if (l.adhika) assertTrue(l.sankrantis.isEmpty()) else assertTrue(l.sankrantis.size in 1..2)
            if (l.kshayaMasa == null && !l.adhika) assertEquals(1, l.sankrantis.size)
        }
        val adhika = all.count { it.adhika }
        assertTrue(adhika in 9..13, "adhika count")
        assertEquals(0, all.count { it.kshayaMasa != null })
    }

    @Test
    fun sankrantisAreBitIdenticalWhateverTheRange() {
        val wide = cal.sankrantis(at("2023-12-31T17:43:11.123456789Z"), at("2025-02-01T00:00:00Z"))
        assertEquals(13, wide.size)
        val narrow = cal.sankrantis(at("2024-04-12T05:00:00.5Z"), at("2024-04-15T00:00:00Z"))
        assertEquals(wide.filter { it.into == Rashi.MESHA }, narrow)
        val other = LunarCalendar(GoldenLoader.openDe440())
        assertEquals(wide, other.sankrantis(at("2023-12-31T17:43:11.123456789Z"), at("2025-02-01T00:00:00Z")))
        val chitra = CalcSettings(ayanamsa = AyanamsaId.TRUE_CHITRA)
        val r = cal.sankrantis(at("2024-01-01T00:00:00Z"), at("2025-01-01T00:00:00Z"), chitra)
        assertEquals(12, r.size)
        assertTrue(r.zip(wide).all { (a, b) -> a.into == b.into && a.at != b.at })
        assertTrue(cal.sankrantis(wide[0].at, wide[0].at).isEmpty())
    }

    @Test
    fun kshayaMasaInTheEarly1980s() {
        val ls = cal.lunations(at("1982-06-01T00:00:00Z"), at("1983-06-01T00:00:00Z"))
        val kshaya = ls.filter { it.kshayaMasa != null }
        assertEquals(1, kshaya.size)
        assertEquals(2, kshaya.single().sankrantis.size)
    }

    @Test
    fun eraYearsOfJune2024() {
        val years = cal.eraYears(cal.lunationAt(at("2024-06-01T00:00:00Z")))
        assertEquals(1946, years.shaka)
        assertEquals(2081, years.vikramChaitradi)
        assertEquals(2080, years.vikramKartikadi)
        assertEquals(38, years.samvatsara)
        assertEquals(38, MasaNaming.samvatsara(1946))
        assertEquals(1, MasaNaming.samvatsara(1909))
    }

    @Test
    fun moreThanTwoSankrantisIsAnError() {
        val t = at("2024-01-01T00:00:00Z")
        val three = listOf(Sankranti(Rashi.DHANU, t), Sankranti(Rashi.MAKARA, t), Sankranti(Rashi.KUMBHA, t))
        val e = assertFailsWith<IllegalStateException> { MasaNaming.name(three, three[0]) }
        assertFalse(e.message.orEmpty().any { it.isDigit() })
    }
}
