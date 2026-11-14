package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.tzContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeoContractTest {
    private val place = GeoPlace(1, "X", null, "IN", 19.0, 72.8, null, 10, "Asia/Kolkata", "bombay")

    @Test fun manualOffsetBounds() {
        ManualOffset(-43_200)
        ManualOffset(50_400)
        assertFailsWith<IllegalArgumentException> { ManualOffset(50_401) }
        assertFailsWith<IllegalArgumentException> { ManualOffset(-43_201) }
    }

    @Test fun contextAndEnginePlace() {
        assertEquals("bombay", place.tzContext().overlayKey)
        assertEquals(0.0, place.toEnginePlace().elevationM)
    }
}
