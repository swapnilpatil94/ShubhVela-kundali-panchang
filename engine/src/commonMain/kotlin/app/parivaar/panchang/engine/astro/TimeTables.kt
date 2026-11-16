package app.parivaar.panchang.engine.astro

import kotlin.time.Instant

/**
 * Leap-second table and its horizon (docs/specs/M01.md section 3.2). Updatable through a signed content pack
 * (ENGINE.md section 3), so the rules take it as a parameter.
 *
 * @property leapSeconds (00:00 UTC instant from which the value applies, TAI-UTC in seconds), ascending.
 * @property validThrough last instant for which [leapSeconds] is authoritative. Instants in
 *   `[leapSeconds.first().first, validThrough]` use the table; earlier ones the deltaT polynomials; later ones
 *   the value at `validThrough` extended by a small slope (continuous, see [TimeScales]).
 */
data class TimeTables(val leapSeconds: List<Pair<Instant, Int>>, val validThrough: Instant) {
    init {
        require(leapSeconds.isNotEmpty()) { "leapSeconds must not be empty" }
        require(leapSeconds.zipWithNext().all { (a, b) -> a.first < b.first }) { "leapSeconds must ascend" }
        require(validThrough >= leapSeconds.last().first) { "validThrough precedes the last table entry" }
    }

    companion object {
        /**
         * TAI-UTC since 1972 (IERS; the list in docs/specs/M01.md section 3.2).
         *
         * `validThrough` = 2027-01-01T00:00:00Z. Source: IERS Bulletin C 72 (2026-07-06): no leap second at end
         * of December 2026. The bulletin covers that one opportunity, so the table is authoritative up to the
         * end of the period it covers and no further. The value MUST stay identical to `VALID_THROUGH` in
         * `tools/oracle/timescale.py` (the golden vectors are generated with it): when a later Bulletin C is
         * issued, change both together, cite the new number here and regenerate the golden data.
         */
        val DEFAULT: TimeTables = TimeTables(
            leapSeconds = listOf(
                "1972-01-01" to 10, "1972-07-01" to 11, "1973-01-01" to 12, "1974-01-01" to 13,
                "1975-01-01" to 14, "1976-01-01" to 15, "1977-01-01" to 16, "1978-01-01" to 17,
                "1979-01-01" to 18, "1980-01-01" to 19, "1981-07-01" to 20, "1982-07-01" to 21,
                "1983-07-01" to 22, "1985-07-01" to 23, "1988-01-01" to 24, "1990-01-01" to 25,
                "1991-01-01" to 26, "1992-07-01" to 27, "1993-07-01" to 28, "1994-07-01" to 29,
                "1996-01-01" to 30, "1997-07-01" to 31, "1999-01-01" to 32, "2006-01-01" to 33,
                "2009-01-01" to 34, "2012-07-01" to 35, "2015-07-01" to 36, "2017-01-01" to 37,
            ).map { (date, taiMinusUtc) -> Instant.parse(date + "T00:00:00Z") to taiMinusUtc },
            validThrough = Instant.parse("2027-01-01T00:00:00Z"),
        )
    }
}
