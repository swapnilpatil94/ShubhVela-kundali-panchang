package app.parivaar.panchang.core.geo.internal

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/** Latitude/longitude box (in 1e-5 degree integers) around a point, split at the antimeridian (spec 4.3). */
internal class GeoBox(val latMinE5: Int, val latMaxE5: Int, val lonRangesE5: List<IntRange>) {
    companion object {
        private const val KM_PER_DEG = 111.0
        private const val E5 = 100_000.0
        private const val MIN_COS = 0.01
        private const val MAX_LAT = 90.0
        private const val MAX_LON = 180.0
        private const val FULL_TURN = 360.0

        fun around(latDeg: Double, lonDeg: Double, maxKm: Double): GeoBox {
            val dLat = maxKm / KM_PER_DEG
            val dLon = maxKm / (KM_PER_DEG * max(cos(Math.toRadians(latDeg)), MIN_COS))
            val latMin = toE5((latDeg - dLat).coerceAtLeast(-MAX_LAT), floorIt = true)
            val latMax = toE5((latDeg + dLat).coerceAtMost(MAX_LAT), floorIt = false)
            val lonMinE5 = toE5(-MAX_LON, floorIt = true)
            val lonMaxE5 = toE5(MAX_LON, floorIt = false)
            val reachesPole = abs(latDeg) + dLat >= MAX_LAT
            if (dLon >= MAX_LON || reachesPole) return GeoBox(latMin, latMax, listOf(lonMinE5..lonMaxE5))
            val lon = wrap(lonDeg)
            val lo = lon - dLon
            val hi = lon + dLon
            val ranges = when {
                lo < -MAX_LON -> listOf(toE5(lo + FULL_TURN, true)..lonMaxE5, lonMinE5..toE5(hi, false))
                hi > MAX_LON -> listOf(toE5(lo, true)..lonMaxE5, lonMinE5..toE5(hi - FULL_TURN, false))
                else -> listOf(toE5(lo, true)..toE5(hi, false))
            }
            return GeoBox(latMin, latMax, ranges)
        }

        private fun wrap(lonDeg: Double): Double {
            val r = lonDeg - FULL_TURN * floor((lonDeg + MAX_LON) / FULL_TURN)
            return if (abs(r) <= MAX_LON) r else MAX_LON
        }

        private fun toE5(deg: Double, floorIt: Boolean): Int =
            (if (floorIt) floor(deg * E5) else kotlin.math.ceil(deg * E5)).toInt()
    }
}
