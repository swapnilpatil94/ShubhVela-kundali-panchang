package app.parivaar.panchang.feature.calendar.ics

import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** One all-day public event: a festival or vrat date and its (already localised) name. No personal data. */
data class IcsEvent(val date: LocalDate, val summary: String)

/** RFC 5545 writer for one-off all-day `.ics` shares (docs/specs/M09.md, decisions D3 and D9). Pure. */
object IcsWriter {
    private const val CRLF = "\r\n"
    private const val MAX_OCTETS = 75
    private const val YEAR_DIGITS = 4
    private const val PRODID = "-//ShubhVela//Calendar 1//EN"
    private const val UID_DOMAIN = "shubhvela.invalid"

    /** Serialises [events] with [stamp] as DTSTAMP (UTC); [newUid] supplies a random, non-personal id per event. */
    fun write(events: List<IcsEvent>, stamp: Instant, newUid: () -> String): String {
        val dtstamp = stamp.toLocalDateTime(TimeZone.UTC).let {
            "${ymd(it.date)}T${two(it.hour)}${two(it.minute)}${two(it.second)}Z"
        }
        val out = StringBuilder()
        fun line(s: String) = out.append(fold(s))
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:$PRODID")
        line("CALSCALE:GREGORIAN")
        line("METHOD:PUBLISH")
        for (e in events) {
            line("BEGIN:VEVENT")
            line("UID:${newUid()}@$UID_DOMAIN")
            line("DTSTAMP:$dtstamp")
            line("DTSTART;VALUE=DATE:${ymd(e.date)}")
            line("DTEND;VALUE=DATE:${ymd(e.date.plus(DatePeriod(days = 1)))}")
            line("SUMMARY:${escape(e.summary)}")
            line("TRANSP:TRANSPARENT")
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    /** TEXT escaping per RFC 5545 section 3.3.11. */
    internal fun escape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> append("\\\\")
                c == ';' -> append("\\;")
                c == ',' -> append("\\,")
                c == '\r' && i + 1 < s.length && s[i + 1] == '\n' -> {
                    append("\\n")
                    i++
                }
                c == '\n' || c == '\r' -> append("\\n")
                else -> append(c)
            }
            i++
        }
    }

    /** Folds one content line at [MAX_OCTETS] UTF-8 octets, never inside a code point; ends with CRLF. */
    internal fun fold(line: String): String {
        val out = StringBuilder()
        var octets = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val n = Character.charCount(cp)
            val size = utf8Size(cp)
            if (octets + size > MAX_OCTETS) {
                out.append(CRLF).append(' ')
                octets = 1
            }
            out.appendCodePoint(cp)
            octets += size
            i += n
        }
        return out.append(CRLF).toString()
    }

    @Suppress("MagicNumber")
    private fun utf8Size(cp: Int): Int = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun ymd(d: LocalDate) = d.year.toString().padStart(YEAR_DIGITS, '0') + two(d.month.ordinal + 1) + two(d.day)
}
