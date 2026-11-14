package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.BackendId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** docs/specs/M03.md section 4, `PanchangTypesTest`. */
class PanchangTypesTest {
    private val t0 = Instant.parse("2026-11-14T01:23:45.678901234Z")

    private fun hasDigit(text: String?): Boolean = text.orEmpty().any { it.isDigit() }

    @Test
    fun karanaNumbers() {
        assertEquals(Karana.KIMSTUGHNA, Karana.ofNumber(1))
        assertEquals(Karana.BAVA, Karana.ofNumber(2))
        assertEquals(Karana.BAVA, Karana.ofNumber(9))
        assertEquals(Karana.BALAVA, Karana.ofNumber(3))
        assertEquals(Karana.VISHTI, Karana.ofNumber(57))
        assertEquals(Karana.SHAKUNI, Karana.ofNumber(58))
        assertEquals(Karana.CHATUSHPADA, Karana.ofNumber(59))
        assertEquals(Karana.NAGA, Karana.ofNumber(60))
        val vishti = listOf(8, 15, 22, 29, 36, 43, 50, 57)
        for (k in vishti) assertEquals(Karana.VISHTI, Karana.ofNumber(k), "karana $k")
        assertEquals(vishti, (1..60).filter { Karana.ofNumber(it) == Karana.VISHTI })
        for (k in listOf(0, 61, -1)) {
            val e = assertFailsWith<IllegalArgumentException> { Karana.ofNumber(k) }
            assertFalse(hasDigit(e.message))
        }
    }

    @Test
    fun paksha() {
        assertEquals(Paksha.SHUKLA, pakshaOf(1))
        assertEquals(Paksha.SHUKLA, pakshaOf(15))
        assertEquals(Paksha.KRISHNA, pakshaOf(16))
        assertEquals(Paksha.KRISHNA, pakshaOf(30))
        assertFailsWith<IllegalArgumentException> { pakshaOf(0) }
        assertFailsWith<IllegalArgumentException> { pakshaOf(31) }
    }

    @Test
    fun vaarOfCivilDate() {
        assertEquals(Vaar.SATURDAY, Vaar.of(LocalDate(2026, 11, 14)))
        assertEquals(Vaar.SUNDAY, Vaar.of(LocalDate(2026, 11, 15)))
        assertEquals(Vaar.THURSDAY, Vaar.of(LocalDate(1970, 1, 1)))
        assertEquals(Vaar.WEDNESDAY, Vaar.of(LocalDate(1969, 12, 31)))
        assertEquals(Vaar.TUESDAY, Vaar.of(LocalDate(1900, 1, 2)))
        val lords = Vaar.entries.map { it.lord.name }
        assertEquals(listOf("SUN", "MOON", "MARS", "MERCURY", "JUPITER", "VENUS", "SATURN"), lords)
    }

    @Test
    fun enumShapes() {
        assertEquals(listOf(30, 60, 27, 27, 12), LimbKind.entries.map { it.divisions })
        assertEquals(11, Karana.entries.size)
        assertEquals(
            listOf("SUN", "VENUS", "MERCURY", "MOON", "SATURN", "JUPITER", "MARS"),
            Choghadiya.entries.map { it.lord.name },
        )
        assertEquals(13, WindowKind.entries.size)
    }

    @Test
    fun settingsDefaults() {
        val s = CalcSettings()
        assertEquals(TimingStyle.ACTUAL_SUNRISE, s.timingStyle)
        assertFalse(s.abhijitOnWednesday)
    }

    @Test
    fun limbSpanValidatesAndRedacts() {
        val span = LimbSpan(LimbKind.TITHI, 30, t0, t0 + 20.hours, kshaya = true)
        assertEquals("LimbSpan(redacted)", span.toString())
        assertFalse(hasDigit(span.toString()))
        assertEquals(LimbSpan(LimbKind.TITHI, 30, t0, t0 + 20.hours, kshaya = true), span)
        LimbSpan(LimbKind.NAKSHATRA, 0, t0, t0 + 1.hours)
        LimbSpan(LimbKind.MOON_RASHI, 11, t0, t0 + 1.hours)
        assertFailsWith<IllegalArgumentException> { LimbSpan(LimbKind.TITHI, 0, t0, t0 + 1.hours) }
        assertFailsWith<IllegalArgumentException> { LimbSpan(LimbKind.KARANA, 61, t0, t0 + 1.hours) }
        assertFailsWith<IllegalArgumentException> { LimbSpan(LimbKind.YOGA, 27, t0, t0 + 1.hours) }
        val e = assertFailsWith<IllegalArgumentException> { LimbSpan(LimbKind.YOGA, 1, t0, t0) }
        assertFalse(hasDigit(e.message))
    }

    @Test
    fun windowValidatesAndRedacts() {
        val w = Window(WindowKind.CHOGHADIYA, 3, "LABH", night = true, start = t0, end = t0 + 1.hours)
        assertEquals("Window(redacted)", w.toString())
        assertFalse(hasDigit(w.toString()))
        Window(WindowKind.PANCHAK, 0, null, night = false, start = t0, end = t0 + 1.hours)
        assertFailsWith<IllegalArgumentException> { Window(WindowKind.HORA, -1, "SUN", false, t0, t0 + 1.hours) }
        assertFailsWith<IllegalArgumentException> { Window(WindowKind.HORA, 1, "SUN", false, t0, t0) }
    }

    @Test
    fun tablesValidate() {
        val night = Vaar.entries.associateWith { List(8) { "AMRIT" } }
        val choghadiya = EightPartTable(day = emptyMap(), night = night)
        val durmuhurta = Vaar.entries.associateWith { listOf(DurmuhurtaSlot(night = false, muhurta = 14)) }
        val tables = PanchangTables(durmuhurta, choghadiya, gowri = null, varjyam = null)
        assertEquals(14, tables.durmuhurta.getValue(Vaar.SUNDAY).single().muhurta)
        assertFailsWith<IllegalArgumentException> { DurmuhurtaSlot(false, 0) }
        assertFailsWith<IllegalArgumentException> { DurmuhurtaSlot(true, 16) }
        assertFailsWith<IllegalArgumentException> {
            EightPartTable(emptyMap(), night + (Vaar.MONDAY to List(7) { "AMRIT" }))
        }
        assertFailsWith<IllegalArgumentException> { EightPartTable(emptyMap(), night - Vaar.MONDAY) }
        assertFailsWith<IllegalArgumentException> { EightPartTable(mapOf(Vaar.SUNDAY to List(8) { "A" }), night) }
        assertFailsWith<IllegalArgumentException> {
            PanchangTables(durmuhurta - Vaar.FRIDAY, choghadiya, null, null)
        }
        val rows = List(27) { listOf(10.0) }
        VarjyamTable(rows, rows)
        assertFailsWith<IllegalArgumentException> { VarjyamTable(rows.drop(1), rows) }
        assertFailsWith<IllegalArgumentException> { VarjyamTable(rows, rows.dropLast(1) + listOf(listOf(57.0))) }
        assertFailsWith<IllegalArgumentException> { VarjyamTable(listOf(listOf(Double.NaN)) + rows.drop(1), rows) }
    }

    @Test
    fun tablesAreDefensiveCopies() {
        val row = MutableList(8) { "AMRIT" }
        val night = Vaar.entries.associateWith { row }.toMutableMap()
        val table = EightPartTable(emptyMap(), night)
        row[0] = "ROG"
        night.remove(Vaar.SUNDAY)
        assertEquals("AMRIT", table.night.getValue(Vaar.SUNDAY)[0])
    }

    private fun day(limbs: Map<LimbKind, List<LimbSpan>>, offset: Int = 19_800): PanchangDay = PanchangDay(
        date = LocalDate(2026, 11, 14),
        utcOffsetSeconds = offset,
        vaar = Vaar.SATURDAY,
        sunrise = t0,
        sunset = t0 + 11.hours,
        nextSunrise = t0 + 24.hours,
        flags = setOf(DayFlag.SUN_FALLBACK),
        backend = BackendId.PRECISION_DE440,
        limbs = limbs,
        windows = listOf(Window(WindowKind.ABHIJIT, 8, null, false, t0 + 5.hours, t0 + 6.hours)),
        unavailable = setOf(WindowKind.GOWRI),
        shool = Shool(Direction.EAST, "CURD"),
    )

    @Test
    fun dayAtSunriseAndRedaction() {
        val before = LimbSpan(LimbKind.TITHI, 4, t0 - 10.hours, t0)
        val first = LimbSpan(LimbKind.TITHI, 5, t0, t0 + 20.hours)
        val second = LimbSpan(LimbKind.TITHI, 6, t0 + 20.hours, t0 + 44.hours)
        val d = day(mapOf(LimbKind.TITHI to listOf(before, first, second)))
        assertSame(first, d.atSunrise(LimbKind.TITHI))
        assertEquals("PanchangDay(redacted)", d.toString())
        assertFalse(hasDigit(d.toString()))
        assertEquals(d, day(mapOf(LimbKind.TITHI to listOf(before, first, second))))
        val missing = assertFailsWith<IllegalStateException> { d.atSunrise(LimbKind.YOGA) }
        assertFalse(hasDigit(missing.message))
    }

    @Test
    fun dayValidatesOffsetAndOrder() {
        day(emptyMap(), offset = -43_200)
        day(emptyMap(), offset = 50_400)
        for (bad in listOf(-43_201, 50_401)) {
            val e = assertFailsWith<IllegalArgumentException> { day(emptyMap(), offset = bad) }
            assertFalse(hasDigit(e.message))
        }
        assertFailsWith<IllegalArgumentException> { day(emptyMap()).copy(sunset = t0) }
        assertFailsWith<IllegalArgumentException> { day(emptyMap()).copy(nextSunrise = t0 + 11.hours) }
    }

    @Test
    fun exceptionCarriesCodeOnly() {
        val e = PanchangUnavailableException(PanchangError.NO_SUN_DAY)
        assertEquals("NO_SUN_DAY", e.message)
        assertEquals(PanchangError.NO_SUN_DAY, e.code)
        assertFalse(hasDigit(e.message))
        assertFalse(hasDigit(e.toString().substringAfter(": ")))
    }
}
