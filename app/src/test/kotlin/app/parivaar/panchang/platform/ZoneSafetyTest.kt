package app.parivaar.panchang.platform

import app.parivaar.panchang.core.model.geo.UnknownZoneException
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Fable review item 2: a crafted zone 'X/Y' degrades instead of crashing start-up, re-plans or widgets. */
class ZoneSafetyTest {
    @Test fun unknownZoneDegradesToTheFallback() {
        assertNull(zoneSafe(null) { TimeZone.of("X/Y") })
        assertEquals(0, zoneSafe(0) { throw UnknownZoneException() })
        assertEquals("Asia/Kolkata", zoneSafe(null) { TimeZone.of("Asia/Kolkata") }?.id)
    }
}
