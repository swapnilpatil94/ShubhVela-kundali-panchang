package app.parivaar.panchang.feature.calendar.ics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class IcsWriterTest {
    private val stamp = Instant.parse("2026-11-15T09:05:07Z")
    private val long = "दीपावली लक्ष्मी पूजन, महा-उत्सव; एकादशी व्रत पारण समय सारणी ".repeat(3)
    private val events = listOf(
        IcsEvent(LocalDate(2026, 11, 8), "Diwali"),
        IcsEvent(LocalDate(2026, 12, 31), long),
    )
    private var n = 0
    private fun ics() = IcsWriter.write(events, stamp) { "uid-${n++}" }

    private fun unfold(s: String) = s.replace("\r\n ", "")

    @Test fun `every line ends CRLF and none is longer than 75 octets`() {
        val s = ics()
        assertTrue(s.endsWith("\r\n"))
        assertFalse(s.replace("\r\n", "").contains('\n') || s.replace("\r\n", "").contains('\r'))
        for (line in s.split("\r\n").dropLast(1)) assertTrue(line.toByteArray().size <= 75, line)
    }

    @Test fun `folding never splits a code point and unfolds to the original`() {
        val s = ics()
        assertTrue(s.contains("\r\n "), "long summary must fold")
        val unfolded = unfold(s)
        assertTrue(unfolded.contains("SUMMARY:" + IcsWriter.escape(long)))
        assertFalse(s.contains('�'))
    }

    @Test fun `structure and balance`() {
        val lines = unfold(ics()).split("\r\n").filter { it.isNotEmpty() }
        assertEquals("BEGIN:VCALENDAR", lines.first())
        assertEquals("END:VCALENDAR", lines.last())
        assertEquals(lines.count { it == "BEGIN:VEVENT" }, lines.count { it == "END:VEVENT" })
        assertEquals(2, lines.count { it == "BEGIN:VEVENT" })
        assertTrue("VERSION:2.0" in lines && "CALSCALE:GREGORIAN" in lines && "METHOD:PUBLISH" in lines)
        assertTrue("PRODID:-//ShubhVela//Calendar 1//EN" in lines)
        assertEquals(setOf("DTSTAMP:20261115T090507Z"), lines.filter { it.startsWith("DTSTAMP") }.toSet())
        assertTrue(lines.all { !it.startsWith("DTSTART") || it.startsWith("DTSTART;VALUE=DATE:") })
        assertEquals(2, lines.count { it == "TRANSP:TRANSPARENT" })
    }

    @Test fun `uids are unique and non-personal`() {
        val uids = unfold(ics()).split("\r\n").filter { it.startsWith("UID:") }
        assertEquals(2, uids.toSet().size)
        assertTrue(uids.all { it.endsWith("@shubhvela.invalid") })
    }

    @Test fun `all-day dates`() {
        val s = ics()
        assertTrue(s.contains("DTSTART;VALUE=DATE:20261108\r\nDTEND;VALUE=DATE:20261109\r\n"))
        assertTrue(s.contains("DTSTART;VALUE=DATE:20261231\r\nDTEND;VALUE=DATE:20270101\r\n"))
    }

    @Test fun `text escaping`() {
        assertEquals("a\\,b\\;c\\\\d\\ne\\nf", IcsWriter.escape("a,b;c\\d\ne\r\nf"))
    }

    @Test fun `no location or geo`() {
        val s = ics()
        assertFalse(s.contains("GEO") || s.contains("LOCATION") || s.contains("ORGANIZER") || s.contains("ATTENDEE"))
    }

    @Test fun `empty list is still a valid calendar`() {
        val s = IcsWriter.write(emptyList(), stamp) { "x" }
        assertTrue(s.startsWith("BEGIN:VCALENDAR\r\n") && s.endsWith("END:VCALENDAR\r\n"))
    }
}
