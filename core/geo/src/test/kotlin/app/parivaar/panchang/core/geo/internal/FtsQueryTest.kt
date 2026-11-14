package app.parivaar.panchang.core.geo.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtsQueryTest {
    @Test fun tokensGetPrefixStar() {
        assertEquals("prayag*", FtsQuery.match("Prayag"))
        assertEquals("new* york*", FtsQuery.match("  New-York! "))
        assertEquals("प्रयाग*", FtsQuery.match("प्रयाग"))
    }

    @Test fun shortOrEmptyIsNull() {
        assertNull(FtsQuery.match("a"))
        assertNull(FtsQuery.match(" -- "))
    }

    @Test fun noOperatorCharactersSurvive() {
        val m = requireNotNull(FtsQuery.match("a\"b OR c* (d) NEAR:e"))
        assertTrue(m.none { it == '"' || it == '(' || it == ':' })
    }

    @Test fun boxNormal() {
        val b = GeoBox.around(10.0, 20.0, 111.0)
        assertEquals(1, b.lonRangesE5.size)
        assertTrue(b.latMinE5 <= 900_000 && b.latMaxE5 >= 1_100_000)
    }

    @Test fun boxSplitsAtAntimeridian() {
        val east = GeoBox.around(0.0, 179.9, 100.0)
        assertEquals(2, east.lonRangesE5.size)
        assertEquals(18_000_000, east.lonRangesE5[0].last)
        assertEquals(-18_000_000, east.lonRangesE5[1].first)
        val west = GeoBox.around(0.0, -179.9, 100.0)
        assertEquals(2, west.lonRangesE5.size)
    }

    @Test fun boxNearPoleCoversAllLongitudes() {
        val b = GeoBox.around(89.9, 10.0, 100.0)
        assertEquals(1, b.lonRangesE5.size)
        assertEquals(-18_000_000..18_000_000, b.lonRangesE5[0])
    }
}
