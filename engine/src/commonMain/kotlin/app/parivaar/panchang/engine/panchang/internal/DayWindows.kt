package app.parivaar.panchang.engine.panchang.internal

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.panchang.EightPartTable
import app.parivaar.panchang.engine.panchang.Karana
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangTables
import app.parivaar.panchang.engine.panchang.TimingStyle
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.WeekdayTables
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Windows of one day and the kinds that could not be computed (missing tables). */
internal class DayWindowSet(val windows: List<Window>, val unavailable: Set<WindowKind>)

/**
 * Day windows (docs/specs/M03.md section 3.3) from the sun events of [sun], in exact Long nanosecond
 * arithmetic: P(a, b, n, i) = a + ((b - a) i) div n, so part n of a division ends exactly at b.
 */
internal class DayWindows(
    private val sun: SunDay,
    private val vaar: Vaar,
    private val s: CalcSettings,
    private val tables: PanchangTables,
) {
    private val out = ArrayList<Window>()
    private val unavailable = LinkedHashSet<WindowKind>()

    /**
     * All windows, sorted by (start, kind ordinal, part). [limbs] are the day's spans; [rashiAt] gives the
     * MOON_RASHI span containing an instant (for a Panchak that begins before or ends after the day).
     */
    fun compute(limbs: Map<LimbKind, List<LimbSpan>>, rashiAt: (Instant) -> LimbSpan): DayWindowSet {
        weekdayParts()
        muhurtas()
        eightParts(WindowKind.CHOGHADIYA, WeekdayTables.dayChoghadiya(vaar).map { it.name }, tables.choghadiya)
        val gowri = tables.gowri
        if (gowri != null && gowri.day.isNotEmpty()) {
            eightParts(WindowKind.GOWRI, gowri.day.getValue(vaar), gowri)
        } else {
            unavailable += WindowKind.GOWRI
        }
        horas()
        bhadra(limbs[LimbKind.KARANA].orEmpty())
        panchak(limbs[LimbKind.MOON_RASHI].orEmpty(), rashiAt)
        varjyam(limbs[LimbKind.NAKSHATRA].orEmpty())
        val sorted = out.sortedWith(compareBy<Window>({ it.start }, { it.kind.ordinal }, { it.part }))
        return DayWindowSet(sorted, unavailable.toSet())
    }

    /** RAHU_KAAL, YAMAGANDA, GULIKA: one of 8 parts of the day (or of 06:00-18:00 local for PRINTED_6AM). */
    private fun weekdayParts() {
        val printed = s.timingStyle == TimingStyle.PRINTED_6AM
        val a = if (printed) sun.midnight + PRINTED_START else sun.sunrise
        val b = if (printed) sun.midnight + PRINTED_END else sun.sunset
        val parts = listOf(
            WindowKind.RAHU_KAAL to WeekdayTables.rahuPart(vaar),
            WindowKind.YAMAGANDA to WeekdayTables.yamagandaPart(vaar),
            WindowKind.GULIKA to WeekdayTables.gulikaPart(vaar),
        )
        for ((kind, p) in parts) out += Window(kind, p, null, false, part(a, b, EIGHT, p - 1), part(a, b, EIGHT, p))
    }

    /** ABHIJIT (day muhurta 8), BRAHMA_MUHURTA (night muhurta 14) and the DURMUHURTA slots of the weekday. */
    private fun muhurtas() {
        if (vaar != Vaar.WEDNESDAY || s.abhijitOnWednesday) out += muhurta(WindowKind.ABHIJIT, false, ABHIJIT)
        out += muhurta(WindowKind.BRAHMA_MUHURTA, true, BRAHMA)
        for (slot in tables.durmuhurta.getValue(vaar)) out += muhurta(WindowKind.DURMUHURTA, slot.night, slot.muhurta)
    }

    private fun muhurta(kind: WindowKind, night: Boolean, i: Int): Window {
        val a = if (night) sun.sunset else sun.sunrise
        val b = if (night) sun.nextSunrise else sun.sunset
        return Window(kind, i, null, night, part(a, b, FIFTEEN, i - 1), part(a, b, FIFTEEN, i))
    }

    /** 8 parts of the day with [dayCodes], then 8 parts of the night with the codes of [table]; part 1..8 each. */
    private fun eightParts(kind: WindowKind, dayCodes: List<String>, table: EightPartTable) {
        val nightCodes = table.night.getValue(vaar)
        for (p in 1..EIGHT) {
            out += Window(kind, p, dayCodes[p - 1], false, part(sun.sunrise, sun.sunset, EIGHT, p - 1),
                part(sun.sunrise, sun.sunset, EIGHT, p))
            out += Window(kind, p, nightCodes[p - 1], true, part(sun.sunset, sun.nextSunrise, EIGHT, p - 1),
                part(sun.sunset, sun.nextSunrise, EIGHT, p))
        }
    }

    /**
     * 12 horas of the day, then 12 of the night; hora h = 1..24 has lord HORA_ORDER[(idx(vaar lord) + h - 1)
     * mod 7]. `part` counts within each half (1..12, `night` tells which), as for the other windows.
     */
    private fun horas() {
        val order = WeekdayTables.HORA_ORDER
        val first = order.indexOf(vaar.lord)
        for (h in 1..2 * TWELVE) {
            val night = h > TWELVE
            val p = if (night) h - TWELVE else h
            val a = if (night) sun.sunset else sun.sunrise
            val b = if (night) sun.nextSunrise else sun.sunset
            val lord = order[(first + h - 1) % order.size]
            out += Window(WindowKind.HORA, p, lord.name, night, part(a, b, TWELVE, p - 1), part(a, b, TWELVE, p))
        }
    }

    /** BHADRA: each VISHTI karana span, unclipped; `part` = karana number. */
    private fun bhadra(karanas: List<LimbSpan>) {
        for (k in karanas) {
            if (Karana.ofNumber(k.index) == Karana.VISHTI) {
                out += Window(WindowKind.BHADRA, k.index, null, false, k.start, k.end)
            }
        }
    }

    /** PANCHAK: a KUMBHA span joined with the following MEENA span, [KUMBHA.start, MEENA.end), part 0. */
    private fun panchak(rashis: List<LimbSpan>, rashiAt: (Instant) -> LimbSpan) {
        for ((i, r) in rashis.withIndex()) {
            val pair = when {
                r.index == KUMBHA -> r to (rashis.getOrNull(i + 1) ?: rashiAt(r.end + JOIN_PROBE))
                r.index == MEENA && i == 0 -> rashiAt(r.start - JOIN_PROBE) to r
                else -> continue
            }
            check(pair.first.index == KUMBHA && pair.second.index == MEENA && pair.first.end == pair.second.start) {
                "panchak spans do not join"
            }
            out += Window(WindowKind.PANCHAK, 0, null, false, pair.first.start, pair.second.end)
        }
    }

    /** VARJYAM and AMRIT_KAAL: per NAKSHATRA span (n, s, e) and offset g, [s + (e - s) g / 60, + 4 ghati]. */
    private fun varjyam(nakshatras: List<LimbSpan>) {
        val table = tables.varjyam
        if (table == null) {
            unavailable += WindowKind.VARJYAM
            unavailable += WindowKind.AMRIT_KAAL
            return
        }
        for (n in nakshatras) {
            val lengthNs = toNs(n.end) - toNs(n.start)
            val rows = listOf(
                WindowKind.VARJYAM to table.varjyamGhati[n.index],
                WindowKind.AMRIT_KAAL to table.amritGhati[n.index],
            )
            for ((kind, offsets) in rows) {
                for (g in offsets) {
                    val start = ghati(n.start, lengthNs, g)
                    out += Window(kind, n.index, null, false, start, ghati(n.start, lengthNs, g + WINDOW_GHATI))
                }
            }
        }
    }

    companion object {
        private const val EIGHT = 8
        private const val TWELVE = 12
        private const val FIFTEEN = 15
        private const val ABHIJIT = 8
        private const val BRAHMA = 14
        private const val KUMBHA = 10
        private const val MEENA = 11
        private const val GHATI_PER_SPAN = 60.0
        private const val WINDOW_GHATI = 4.0
        private const val NANOS = 1_000_000_000L
        private val PRINTED_START = 6.hours
        private val PRINTED_END = 18.hours
        private val JOIN_PROBE = 60.seconds

        /** P(a, b, n, i) = a + ((b - a) i) div n in nanoseconds. */
        fun part(a: Instant, b: Instant, n: Int, i: Int): Instant {
            val an = toNs(a)
            return fromNs(an + ((toNs(b) - an) * i).floorDiv(n.toLong()))
        }

        /** [start] + [lengthNs] g / 60, rounded to the nanosecond. */
        private fun ghati(start: Instant, lengthNs: Long, g: Double): Instant =
            fromNs(toNs(start) + (lengthNs.toDouble() * g / GHATI_PER_SPAN).roundToLong())

        private fun toNs(t: Instant): Long = t.epochSeconds * NANOS + t.nanosecondsOfSecond

        private fun fromNs(ns: Long): Instant = Instant.fromEpochSeconds(ns.floorDiv(NANOS), ns.mod(NANOS))
    }
}
