package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.MaxError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * Shared harness of the M04 golden tests (docs/specs/M04.md section 4): fixture loading with row counts asserted
 * from literals and `meta` (a missing or truncated fixture fails, never skips), the two backends with their
 * section 4 tolerances, thread-safe error statistics and the summary
 * `engine/build/reports/golden/m04-summary.txt` (aggregate maxima and counts only: no city, date or place).
 */
internal object M04Golden {
    const val CITIES = 10
    const val MS = 1000.0

    // ---- literal sizes of the committed fixtures (meta must agree)
    const val LUNATIONS = 2_487
    const val LUNATIONS_ADHIKA = 76
    const val LUNATIONS_KSHAYA = 2
    const val DAYS_PER_CITY = 1_561
    const val CITY_DAYS = 15_610
    const val SOLAR_PER_CITY = 4_848
    const val SOLAR_ROWS = 48_480
    const val ECLIPSE_GLOBAL_ROWS = 233
    const val ECLIPSE_LOCAL_ROWS = 1_928
    const val ECLIPSE_SITES = 12

    /** Section 4 tolerances of one backend column. Seconds unless stated; arcseconds as `*Arcsec`. */
    class Limits(
        val name: String,
        val momentCapS: Double,
        val momentArcsec: Double,
        val momentMedianCapS: Double?,
        val sankrantiBaseS: Double,
        val sankrantiArcsec: Double,
        val sunS: Double,
        val lunarContactArcsec: Double,
        val solarContactArcsec: Double,
        val contactCapS: Double,
        val magnitudeTol: Double,
        val horizonS: Double,
        val fragileIndexIsPrecision: Boolean,
        val eph: () -> Ephemeris,
    ) {
        /** Index of this backend's fragile column in a fixture row (`fragP` first, then `fragB`). */
        fun frag(pIndex: Int): Int = if (fragileIndexIsPrecision) pIndex else pIndex + 1
    }

    val PRECISION = Limits(
        "PRECISION", 5.0, 0.1, null, 0.5, 0.12, 5.0, 0.1, 0.2, 5.0, 0.001, 5.0, true,
    ) { GoldenLoader.openDe440() }
    val BASE = Limits(
        "BASE", 120.0, 30.0, 30.0, 1.0, 20.01, 10.0, 30.0, 30.0, 120.0, 0.02, 10.0, false,
    ) { AstronomyEngineEphemeris() }

    fun fixture(name: String): JsonObject = GoldenLoader.json("/golden/$name")

    fun JsonElement.d(): Double = jsonPrimitive.double
    fun JsonElement.i(): Int = jsonPrimitive.int
    fun JsonElement.l(): Long = jsonPrimitive.long
    fun JsonElement.s(): String = jsonPrimitive.content
    fun JsonElement.b(): Boolean = jsonPrimitive.boolean
    fun JsonElement.a(): JsonArray = jsonArray
    fun JsonElement.o(): JsonObject = jsonObject
    fun JsonElement.isNull(): Boolean = this is JsonNull

    fun meta(root: JsonObject): JsonObject = root.getValue("meta").o()

    fun at(ms: Long): Instant = Instant.fromEpochMilliseconds(ms)

    /** |engine instant - fixture millisecond| in seconds. */
    fun dtS(t: Instant, ms: Long): Double = kotlin.math.abs((t - at(ms)).toDouble(DurationUnit.SECONDS))

    /** min(cap, 1 s + e/rate); a non-positive or non-finite rate gives NaN so that every gate fails. */
    fun rateLimit(cap: Double, e: Double, rateArcsecPerS: Double): Double =
        if (rateArcsecPerS.isFinite() && rateArcsecPerS > 0.0) minOf(cap, 1.0 + e / rateArcsecPerS) else Double.NaN

    class City(val name: String, val place: Place, val offsetMin: Int, val row: JsonObject) {
        val offsetSeconds: Int get() = offsetMin * 60
    }

    /** The ten fixture cities (rows under [rowsKey]), meta cross-checked against the rows and the literal sizes. */
    fun cities(root: JsonObject, rowsKey: String, countKey: String, perCity: Int): List<City> {
        val metaCities = meta(root).getValue("cities").a()
        val rows = root.getValue("cities").a()
        assertEquals(CITIES, rows.size, "fixture city count")
        assertEquals(CITIES, metaCities.size, "meta city count")
        return rows.mapIndexed { i, r ->
            val m = metaCities[i].o()
            val o = r.o()
            assertEquals(m.getValue("name").s(), o.getValue("name").s(), "city $i name")
            assertEquals(m.getValue("offsetMin").i(), o.getValue("offsetMin").i(), "city $i offset")
            assertEquals(perCity, m.getValue(countKey).i(), "city $i meta.$countKey")
            assertEquals(perCity, o.getValue(rowsKey).a().size, "city $i rows")
            val place = Place(m.getValue("lat").d(), m.getValue("lon").d(), 0.0)
            City(o.getValue("name").s(), place, o.getValue("offsetMin").i(), o)
        }
    }

    /** Thread-safe running maximum (a [MaxError]) with an optional sample list for the median. */
    class Stat(private val keep: Boolean = false) {
        private val max = MaxError()
        private val samples = ArrayList<Double>()

        @Synchronized
        fun add(error: Double, at: String) {
            max.add(error, at)
            if (keep) samples += error
        }

        val value: Double @Synchronized get() = if (max.count == 0) 0.0 else max.value
        val where: String @Synchronized get() = max.where
        val count: Int @Synchronized get() = max.count

        @Synchronized
        fun median(): Double {
            if (samples.isEmpty()) return Double.NaN
            val s = samples.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
        }

        fun fmt(): String = "%.4g (n=%d)".format(value, count)
    }

    /** Thread-safe failure list. */
    class Failures {
        private val q = ConcurrentLinkedQueue<String>()
        operator fun plusAssign(s: String) {
            q += s
        }

        fun list(): List<String> = q.toList()
    }

    /** Runs [f] over [items] on up to [threads] threads; the first failure is rethrown. Order preserved. */
    fun <T, R> parallel(
        items: List<T>,
        threads: Int = Runtime.getRuntime().availableProcessors(),
        f: (T) -> R,
    ): List<R> {
        val pool = Executors.newFixedThreadPool(minOf(threads, items.size).coerceAtLeast(1))
        try {
            return items.map { pool.submit<R> { f(it) } }.map {
                try {
                    it.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private val summary = sortedMapOf<String, List<String>>()

    /** Replaces the section [title] and rewrites the whole report file. */
    @Synchronized
    fun report(title: String, lines: List<String>) {
        summary[title] = lines
        val dir = File(System.getProperty("user.dir"), "build/reports/golden").apply { mkdirs() }
        File(dir, "m04-summary.txt").writeText(
            summary.entries.joinToString("\n") { (t, l) -> "== $t ==\n" + l.joinToString("\n") }.plus("\n"),
        )
    }

    /** Fails with every entry of [failures] (first ten shown) unless it is empty. */
    fun assertNoFailures(title: String, failures: List<String>) {
        assertTrue(failures.isEmpty(), "$title failures (${failures.size}): ${failures.take(30)}")
    }
}
