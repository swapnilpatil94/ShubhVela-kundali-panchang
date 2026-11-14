package app.parivaar.panchang.core.geo

import android.content.Context
import android.database.sqlite.SQLiteException
import app.parivaar.panchang.core.geo.internal.TzOverlay
import java.io.File
import java.io.IOException

/** Factory for the place store and the historical overlay, both read from packaged assets. */
object PlaceStores {
    private const val DB_ASSET = "geo/places.db"
    private const val SEED_ASSET = "geo/places_seed.tsv"
    private const val OVERLAY_ASSET = "geo/tz_overlay.json"
    private const val DB_FILE = "geo_places.db"

    /** SqlitePlaceStore iff asset `geo/places.db` exists and opens, else the built-in [SeedPlaceStore]. */
    fun create(context: Context): PlaceSearch {
        val db = installDb(context)
        if (db != null) {
            try {
                return SqlitePlaceStore(db)
            } catch (_: SQLiteException) {
                // corrupt or unreadable copy: fall back to the seed (no details are logged)
            }
        }
        return SeedPlaceStore(readAsset(context, SEED_ASSET), loadOverlay(context).keys)
    }

    fun loadOverlay(context: Context): TzOverlay = TzOverlay.parse(readAsset(context, OVERLAY_ASSET))

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }

    /** Copies the packaged DB to no-backup storage (SQLite cannot open asset streams); null when absent. */
    private fun installDb(context: Context): File? {
        val target = File(context.noBackupFilesDir, DB_FILE)
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val stale = !target.exists() || target.lastModified() < installedAt
        val ready = if (stale) copyDb(context, target) else true
        return if (ready) target else null
    }

    private fun copyDb(context: Context, target: File): Boolean {
        val tmp = File(target.path + ".tmp")
        return try {
            context.assets.open(DB_ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        } catch (_: IOException) {
            tmp.delete()
            false
        }
    }
}
