@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.Synthetic.day
import app.parivaar.panchang.feature.aaj.Synthetic.t
import app.parivaar.panchang.feature.aaj.Synthetic.win
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TransitionsTest {
    private val days = listOf(day(0, listOf(win(WindowKind.RAHU_KAAL, 15.0, 16.5))), day(1))

    @Test fun `next window edge`() = assertEquals(t(15.0), Transitions.nextBoundary(t(12.0), days, 0))

    @Test fun `strictly after now`() = assertEquals(t(16.5), Transitions.nextBoundary(t(15.0), days, 0))

    @Test fun `sunrise is a boundary`() = assertEquals(t(30.0), Transitions.nextBoundary(t(25.0), days, 0))

    @Test fun `local midnight bounds the search`() =
        assertEquals(t(18.5), Transitions.nextBoundary(t(18.0), listOf(day(0)), 19_800))

    @Test fun `midnight when no other boundary`() =
        assertEquals(t(24.0), Transitions.nextBoundary(t(20.0), listOf(day(0)), 0))

    private fun zoneOffset(id: String): (Instant) -> Int {
        val rules = java.time.ZoneId.of(id).rules
        return { rules.getOffset(java.time.Instant.ofEpochSecond(it.epochSeconds)).totalSeconds }
    }

    @Test fun `midnight after the US fall-back night is not an hour off`() {
        // 2026-11-01 08:00 UTC is 03:00 EST; the next local midnight (Nov 2, EST) is 05:00 UTC.
        val now = Instant.parse("2026-11-01T08:00:00Z")
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), Transitions.nextBoundary(now, emptyList(), zoneOffset("America/New_York")))
        // From before the change (Oct 31 22:00 EDT) the midnight is still EDT, 04:00 UTC.
        val before = Instant.parse("2026-11-01T02:00:00Z")
        assertEquals(Instant.parse("2026-11-01T04:00:00Z"), Transitions.nextBoundary(before, emptyList(), zoneOffset("America/New_York")))
    }

    @Test fun `midnight after the European spring-forward night`() {
        // 2026-03-29 12:00 UTC is 14:00 CEST; the next local midnight is Mar 30 00:00 CEST = 22:00 UTC.
        val now = Instant.parse("2026-03-29T12:00:00Z")
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), Transitions.nextBoundary(now, emptyList(), zoneOffset("Europe/Berlin")))
        // 01:00 UTC Mar 29 is 02:00 CET (before the change); the next midnight is Mar 30 CEST too.
        val early = Instant.parse("2026-03-29T01:00:00Z")
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), Transitions.nextBoundary(early, emptyList(), zoneOffset("Europe/Berlin")))
    }
}
