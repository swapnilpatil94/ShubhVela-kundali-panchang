package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.geo.internal.Haversine
import app.parivaar.panchang.core.geo.internal.NameEntry
import app.parivaar.panchang.core.geo.internal.NameKey
import app.parivaar.panchang.core.geo.internal.NamedPlace
import app.parivaar.panchang.core.geo.internal.PlaceRanker
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.PlaceHit
import java.time.ZoneId

/**
 * In-memory place store over the hand-written seed TSV (M05 spec 4.8). Parsing happens once in the
 * constructor; afterwards the instance is immutable and thread-safe. Error messages never contain place data.
 */
class SeedPlaceStore(tsv: String, overlayKeys: Set<String>) : PlaceSearch {
    private val entries: List<NamedPlace> = parse(tsv, overlayKeys)

    internal val places: List<GeoPlace> get() = entries.map { it.place }

    override fun search(query: String, limit: Int): List<PlaceHit> = PlaceRanker.rank(query, entries, limit)

    override fun nearest(latDeg: Double, lonDeg: Double, maxKm: Double): GeoPlace? {
        if (!validQuery(latDeg, lonDeg, maxKm)) return null
        var best: GeoPlace? = null
        var bestKm = Double.MAX_VALUE
        for (e in entries) {
            val p = e.place
            val d = Haversine.distanceKm(latDeg, lonDeg, p.latDeg, p.lonDeg)
            val closer = d < bestKm || (d == bestKm && p.id < (best?.id ?: Long.MAX_VALUE))
            if (d <= maxKm && closer) {
                best = p
                bestKm = d
            }
        }
        return best
    }

    private companion object {
        const val COLUMNS = 12
        const val ALT_SEPARATOR = '|'
        const val C_ID = 0
        const val C_NAME = 1
        const val C_ADMIN1 = 2
        const val C_CC = 3
        const val C_LAT = 4
        const val C_LON = 5
        const val C_ELEV = 6
        const val C_POP = 7
        const val C_ZONE = 8
        const val C_OVERLAY = 9
        const val C_ALT = 10
        const val MAX_LAT = 90.0
        const val MAX_LON = 180.0

        fun validQuery(latDeg: Double, lonDeg: Double, maxKm: Double): Boolean =
            latDeg.isFinite() && lonDeg.isFinite() && maxKm.isFinite() && latDeg in -MAX_LAT..MAX_LAT

        fun num(text: String): Double = requireNotNull(text.toDoubleOrNull()) { "Seed number is invalid" }

        fun parse(tsv: String, overlayKeys: Set<String>): List<NamedPlace> {
            val zones = ZoneId.getAvailableZoneIds()
            val seen = HashSet<Long>()
            val lines = tsv.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotEmpty() }.drop(1)
            return lines.map { line ->
                val c = line.split('\t')
                require(c.size == COLUMNS) { "Seed row has wrong column count" }
                val id = requireNotNull(c[C_ID].toLongOrNull()) { "Seed id is not a number" }
                require(seen.add(id)) { "Seed id is duplicated" }
                val place = place(id, c, zones, overlayKeys)
                val names = listOf(c[C_NAME]) + c[C_ALT].split(ALT_SEPARATOR).filter { it.isNotBlank() }
                NamedPlace(place, names.map { NameEntry(it, NameKey.of(it)) }.filter { it.key.isNotEmpty() })
            }.toList()
        }

        fun place(id: Long, c: List<String>, zones: Set<String>, overlayKeys: Set<String>): GeoPlace {
            val lat = num(c[C_LAT])
            val lon = num(c[C_LON])
            require(lat in -MAX_LAT..MAX_LAT && lon in -MAX_LON..MAX_LON) { "Seed coordinate out of range" }
            require(c[C_ZONE] in zones) { "Seed zone is not a known time zone" }
            val overlay = c[C_OVERLAY].ifEmpty { null }
            require(overlay == null || overlay in overlayKeys) { "Seed overlay key is not in the overlay file" }
            require(c[C_NAME].isNotBlank()) { "Seed name is empty" }
            val pop = requireNotNull(c[C_POP].toLongOrNull()) { "Seed population is not a number" }
            val elev = c[C_ELEV].ifEmpty { null }?.let { num(it) }
            val admin1 = c[C_ADMIN1].ifEmpty { null }
            return GeoPlace(id, c[C_NAME], admin1, c[C_CC], lat, lon, elev, pop, c[C_ZONE], overlay)
        }
    }
}
