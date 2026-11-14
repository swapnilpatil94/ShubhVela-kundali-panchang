package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.geo.internal.TzOverlay
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.TzContext
import app.parivaar.panchang.core.model.geo.UnknownZoneException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OffsetResolverTest {
    private val overlay = TzOverlay.parse(File(System.getProperty("pp.rules.dir"), "tz_overlay.json").readText())
    private val r = JavaTimeOffsetResolver(overlay)

    private val ny = TzContext("America/New_York", -74.0060, null)
    private val london = TzContext("Europe/London", -0.1278, null)
    private val sydney = TzContext("Australia/Sydney", 151.2093, null)
    private val apia = TzContext("Pacific/Apia", -171.76, null)
    private val kiri = TzContext("Pacific/Kiritimati", -157.4, null)
    private val mumbai = TzContext("Asia/Kolkata", 72.8777, "bombay")
    private val kolkata = TzContext("Asia/Kolkata", 88.3639, "calcutta")

    private fun ldt(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) = LocalDateTime(y, mo, d, h, mi, 0, 0)
    private fun z(s: String) = Instant.parse(s)
    private fun day(ctx: TzContext, y: Int, mo: Int, d: Int) = r.civilDayOffset(LocalDate(y, mo, d), ctx)
    private fun dayOff(ctx: TzContext, y: Int, mo: Int, d: Int) =
        (day(ctx, y, mo, d) as CivilDayOffset.Valid).offset.offsetSeconds

    @Test
    fun newYorkDst() {
        val v = day(ny, 2026, 3, 8) as CivilDayOffset.Valid
        assertEquals(-14_400, v.offset.offsetSeconds)
        assertTrue(OffsetFlag.DST in v.offset.flags)
        val gap = r.localToInstant(ldt(2026, 3, 8, 2, 30), ny)
        assertEquals(z("2026-03-08T07:30:00Z"), gap.instant)
        assertTrue(OffsetFlag.GAP_SHIFTED in gap.offset.flags)
        val e = r.localToInstant(ldt(2026, 11, 1, 1, 30), ny)
        assertEquals(z("2026-11-01T05:30:00Z"), e.instant)
        assertEquals(z("2026-11-01T06:30:00Z"), e.alternative)
        assertTrue(OffsetFlag.AMBIGUOUS in e.offset.flags)
        val l = r.localToInstant(ldt(2026, 11, 1, 1, 30), ny, AmbiguityChoice.LATER)
        assertEquals(z("2026-11-01T06:30:00Z"), l.instant)
        assertEquals(z("2026-11-01T05:30:00Z"), l.alternative)
    }

    @Test
    fun londonDst() {
        assertEquals(z("2026-03-29T01:30:00Z"), r.localToInstant(ldt(2026, 3, 29, 1, 30), london).instant)
        val e = r.localToInstant(ldt(2026, 10, 25, 1, 30), london)
        assertEquals(z("2026-10-25T00:30:00Z"), e.instant)
        assertEquals(z("2026-10-25T01:30:00Z"), e.alternative)
    }

    @Test
    fun sydneyDst() {
        val e = r.localToInstant(ldt(2026, 4, 5, 2, 30), sydney)
        assertEquals(z("2026-04-04T15:30:00Z"), e.instant)
        assertEquals(z("2026-04-04T16:30:00Z"), e.alternative)
        val g = r.localToInstant(ldt(2026, 10, 4, 2, 30), sydney)
        assertEquals(z("2026-10-03T16:30:00Z"), g.instant)
        assertTrue(OffsetFlag.GAP_SHIFTED in g.offset.flags)
    }

    @Test
    fun datelineNonexistentDays() {
        assertEquals(-36_000, dayOff(apia, 2011, 12, 29))
        assertEquals(CivilDayOffset.Nonexistent, day(apia, 2011, 12, 30))
        assertEquals(50_400, dayOff(apia, 2011, 12, 31))
        assertEquals(-36_000, dayOff(kiri, 1994, 12, 30))
        assertEquals(CivilDayOffset.Nonexistent, day(kiri, 1994, 12, 31))
        assertEquals(50_400, dayOff(kiri, 1995, 1, 1))
    }

    @Test
    fun mumbaiOverlay() {
        val a = r.localToInstant(ldt(1950, 6, 15, 10, 0), mumbai)
        assertEquals(z("1950-06-15T05:09:00Z"), a.instant)
        assertEquals(OffsetSource.OVERLAY, a.offset.source)
        assertEquals("bombay_time", a.offset.overlayId)
        assertTrue(OffsetFlag.HISTORICAL_VERIFY in a.offset.flags)
        val m = r.localToInstant(ldt(1950, 6, 15, 10, 0), mumbai, manual = ManualOffset(19_800))
        assertEquals(z("1950-06-15T04:30:00Z"), m.instant)
        assertEquals(OffsetSource.MANUAL, m.offset.source)
        val g = r.localToInstant(ldt(1955, 1, 1, 0, 20), mumbai)
        assertEquals(z("1954-12-31T19:29:00Z"), g.instant)
        assertTrue(OffsetFlag.GAP_SHIFTED in g.offset.flags)
        val after = r.offsetAt(z("1960-06-15T00:00:00Z"), mumbai)
        assertEquals(19_800, after.offsetSeconds)
        assertEquals(OffsetSource.IANA, after.source)
    }

    @Test
    fun kolkataAndOthers() {
        val k = r.offsetAt(z("1947-06-15T00:00:00Z"), kolkata)
        assertEquals(21_200, k.offsetSeconds)
        assertEquals(OffsetSource.OVERLAY, k.source)
        assertEquals(19_800, r.offsetAt(z("1948-06-15T00:00:00Z"), kolkata).offsetSeconds)
        val delhi = r.offsetAt(z("1950-06-15T00:00:00Z"), TzContext("Asia/Kolkata", 77.209, null))
        assertEquals(19_800, delhi.offsetSeconds)
        assertEquals(OffsetSource.IANA, delhi.source)
        assertTrue(OffsetFlag.HISTORICAL_VERIFY in delhi.flags)
        val madras = r.offsetAt(z("1930-06-15T00:00:00Z"), TzContext("Asia/Kolkata", 80.27, "madras"))
        assertEquals(OffsetSource.IANA, madras.source)
        assertTrue(OffsetFlag.NO_OVERLAY_DATA in madras.flags)
    }

    @Test
    fun lmt() {
        val a = r.offsetAt(z("1890-06-01T00:00:00Z"), TzContext("Asia/Kolkata", 75.0, null))
        assertEquals(18_000, a.offsetSeconds)
        assertEquals(OffsetSource.LMT, a.source)
        assertTrue(OffsetFlag.PRE_STANDARD_LMT in a.flags)
        val lon = r.offsetAt(z("1890-06-01T00:00:00Z"), TzContext("Europe/London", -0.1278, null))
        assertEquals(-31, lon.offsetSeconds)
        val kt = TzContext("Asia/Kathmandu", 85.324, null)
        assertEquals(OffsetSource.LMT, r.offsetAt(z("1910-06-01T00:00:00Z"), kt).source)
        assertEquals(20_700, r.offsetAt(z("1990-06-01T00:00:00Z"), kt).offsetSeconds)
    }

    @Test
    fun invalidInputs() {
        assertFailsWith<IllegalArgumentException> { ManualOffset(50_401) }
        assertFailsWith<UnknownZoneException> {
            r.offsetAt(z("2026-01-01T00:00:00Z"), TzContext("Nowhere/Land", 0.0, null))
        }
        assertFailsWith<UnknownZoneException> {
            r.civilDayOffset(LocalDate(2026, 1, 1), TzContext("Nowhere/Land", 0.0, null))
        }
        assertNull(r.localToInstant(ldt(2026, 1, 1), london).alternative)
    }

    @Test
    fun overlayParserRejections() {
        fun doc(entries: String, extra: String = "") =
            """{"schema":1,"verifyZones":[],"verifyUntilUtc":"1956-01-01T00:00:00Z","entries":[$entries]$extra}"""
        fun e(id: String, from: String, until: String, off: String = "1000") =
            """{"id":"$id","key":"k","offsetSeconds":$off,"fromUtc":"$from","untilUtc":"$until","status":"VERIFY","source":"t"}"""
        val a = "1900-01-01T00:00:00Z"
        val b = "1910-01-01T00:00:00Z"
        val c = "1920-01-01T00:00:00Z"
        TzOverlay.parse(doc(e("a", a, b) + "," + e("b", b, c)))
        assertFailsWith<IllegalArgumentException> { TzOverlay.parse(doc(e("a", a, b) + "," + e("a", b, c))) }
        assertFailsWith<IllegalArgumentException> { TzOverlay.parse(doc(e("a", b, a))) }
        assertFailsWith<IllegalArgumentException> { TzOverlay.parse(doc(e("a", a, c) + "," + e("b", b, c))) }
        assertFailsWith<IllegalArgumentException> { TzOverlay.parse(doc(e("a", a, b, "50401"))) }
        assertFailsWith<IllegalArgumentException> { TzOverlay.parse("not json") }
    }
}
