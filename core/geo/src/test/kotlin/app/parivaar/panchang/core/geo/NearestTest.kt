package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.geo.internal.Haversine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearestTest {
    private val store = SeedTestSupport.store

    @Test fun everySeedCityFindsItself() {
        for (p in store.places) assertEquals(p.id, store.nearest(p.latDeg, p.lonDeg)?.id)
    }

    @Test fun beyondRadiusIsNull() {
        val kiri = store.places.first { it.name == "Kiritimati" }
        // 101 km due north: 101 / 6371.0088 rad in degrees of latitude
        val dLat = Math.toDegrees(101.0 / Haversine.EARTH_RADIUS_KM)
        assertNull(store.nearest(kiri.latDeg + dLat, kiri.lonDeg))
        assertEquals(kiri.id, store.nearest(kiri.latDeg + dLat, kiri.lonDeg, maxKm = 102.0)?.id)
    }

    @Test fun wrapsAtAntimeridian() {
        val suva = store.places.first { it.name == "Suva" }
        // Suva is at lon 178.44; the query is across the +/-180 line (about 175 km away)
        assertEquals(suva.id, store.nearest(suva.latDeg, -179.9, maxKm = 200.0)?.id)
        assertNull(store.nearest(suva.latDeg, -179.9, maxKm = 100.0))
        assertTrue(Haversine.distanceKm(-18.0, 179.9, -18.0, -179.9) in 20.0..22.0)
    }

    @Test fun invalidInputIsNull() {
        assertNull(store.nearest(91.0, 0.0))
        assertNull(store.nearest(Double.NaN, 0.0))
    }

    @Test fun haversineReference() {
        // quarter of the equator
        assertEquals(Math.PI / 2 * Haversine.EARTH_RADIUS_KM, Haversine.distanceKm(0.0, 0.0, 0.0, 90.0), 1e-6)
    }
}
