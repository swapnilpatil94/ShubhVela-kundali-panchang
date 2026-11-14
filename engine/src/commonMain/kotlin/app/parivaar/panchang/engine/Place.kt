package app.parivaar.panchang.engine

/**
 * An observer on the WGS84 ellipsoid.
 *
 * A place is personal data: it never leaves the process and must not appear in logs or exception messages.
 *
 * @property latDeg geodetic latitude in degrees, north positive, in [-90, 90]
 * @property lonDeg longitude in degrees, east positive, in [-180, 180]
 * @property elevationM height above the ellipsoid in metres (raises the observer; horizon dip is not modelled)
 */
data class Place(val latDeg: Double, val lonDeg: Double, val elevationM: Double = 0.0) {
    init {
        require(latDeg >= -MAX_LAT_DEG && latDeg <= MAX_LAT_DEG) { "latitude must be in [-90, 90] degrees" }
        require(lonDeg >= -MAX_LON_DEG && lonDeg <= MAX_LON_DEG) { "longitude must be in [-180, 180] degrees" }
        require(elevationM.isFinite()) { "elevation must be finite" }
    }

    private companion object {
        const val MAX_LAT_DEG = 90.0
        const val MAX_LON_DEG = 180.0
    }
}
