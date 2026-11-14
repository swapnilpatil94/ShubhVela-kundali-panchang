package app.parivaar.panchang.core.geo.internal

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object Haversine {
    const val EARTH_RADIUS_KM = 6371.0088

    /** Great-circle distance in km; longitude wraps at the antimeridian naturally. */
    fun distanceKm(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Double {
        val p1 = Math.toRadians(lat1Deg)
        val p2 = Math.toRadians(lat2Deg)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2Deg - lon1Deg)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
    }
}
