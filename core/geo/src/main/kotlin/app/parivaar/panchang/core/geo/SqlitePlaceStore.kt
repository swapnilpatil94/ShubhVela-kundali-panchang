package app.parivaar.panchang.core.geo

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.parivaar.panchang.core.geo.internal.FtsQuery
import app.parivaar.panchang.core.geo.internal.GeoBox
import app.parivaar.panchang.core.geo.internal.Haversine
import app.parivaar.panchang.core.geo.internal.NameEntry
import app.parivaar.panchang.core.geo.internal.NamedPlace
import app.parivaar.panchang.core.geo.internal.PlaceRanker
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.PlaceHit
import java.io.File

/**
 * Read-only place store over the optional `places.db` (FTS4, built by tools/geo/build_places_db.py).
 * Queries and coordinates are used only as bound parameters; nothing is stored, logged or returned.
 */
class SqlitePlaceStore(dbFile: File) : PlaceSearch {
    private val db: SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)

    override fun search(query: String, limit: Int): List<PlaceHit> {
        val names = FtsQuery.match(query)?.let { matchingNames(it) }.orEmpty()
        val places = if (names.isEmpty()) emptyMap() else loadPlaces(names.keys.toList())
        val candidates = names.mapNotNull { (id, n) -> places[id]?.let { NamedPlace(it, n) } }
        return PlaceRanker.rank(query, candidates, limit)
    }

    private fun matchingNames(match: String): Map<Long, List<NameEntry>> {
        val names = LinkedHashMap<Long, MutableList<NameEntry>>()
        val sql = "SELECT place_id, display, key FROM place_name WHERE key MATCH ? LIMIT ${FtsQuery.MAX_CANDIDATES}"
        db.rawQuery(sql, arrayOf(match)).use { c ->
            while (c.moveToNext()) {
                names.getOrPut(c.getString(0).toLong()) { ArrayList() }.add(NameEntry(c.getString(1), c.getString(2)))
            }
        }
        return names
    }

    override fun nearest(latDeg: Double, lonDeg: Double, maxKm: Double): GeoPlace? {
        val valid = latDeg.isFinite() && lonDeg.isFinite() && maxKm.isFinite() && latDeg in -MAX_LAT..MAX_LAT
        if (!valid) return null
        val box = GeoBox.around(latDeg, lonDeg, maxKm)
        var best: GeoPlace? = null
        var bestKm = Double.MAX_VALUE
        for (range in box.lonRangesE5) {
            val sql = "$SELECT FROM place WHERE lat_e5 BETWEEN ? AND ? AND lon_e5 BETWEEN ? AND ?"
            val args = arrayOf(box.latMinE5, box.latMaxE5, range.first, range.last).map { it.toString() }.toTypedArray()
            db.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    val p = read(c)
                    val d = Haversine.distanceKm(latDeg, lonDeg, p.latDeg, p.lonDeg)
                    val closer = d < bestKm || (d == bestKm && p.id < (best?.id ?: Long.MAX_VALUE))
                    if (d <= maxKm && closer) {
                        best = p
                        bestKm = d
                    }
                }
            }
        }
        return best
    }

    private fun loadPlaces(ids: List<Long>): Map<Long, GeoPlace> {
        val out = HashMap<Long, GeoPlace>()
        for (chunk in ids.chunked(ID_CHUNK)) {
            val marks = chunk.joinToString(",") { "?" }
            val args = chunk.map { it.toString() }.toTypedArray()
            db.rawQuery("$SELECT FROM place WHERE id IN ($marks)", args).use { c ->
                while (c.moveToNext()) read(c).let { out[it.id] = it }
            }
        }
        return out
    }

    private fun read(c: Cursor): GeoPlace = GeoPlace(
        id = c.getLong(C_ID),
        name = c.getString(C_NAME),
        admin1 = if (c.isNull(C_ADMIN1)) null else c.getString(C_ADMIN1),
        countryCode = c.getString(C_CC),
        latDeg = c.getInt(C_LAT) / E5,
        lonDeg = c.getInt(C_LON) / E5,
        elevationM = if (c.isNull(C_ELEV)) null else c.getInt(C_ELEV).toDouble(),
        population = c.getLong(C_POP),
        zoneId = c.getString(C_ZONE),
        overlayKey = if (c.isNull(C_OVERLAY)) null else c.getString(C_OVERLAY),
    )

    private companion object {
        const val SELECT = "SELECT id, name, admin1, cc, lat_e5, lon_e5, elev_m, population, zone, overlay"
        const val ID_CHUNK = 400
        const val E5 = 100_000.0
        const val MAX_LAT = 90.0
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
    }
}
