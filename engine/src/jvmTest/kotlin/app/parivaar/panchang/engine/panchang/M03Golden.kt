package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared harness of the M03 golden tests (docs/specs/M03.md section 4): fixture loading with row-count
 * assertions (a missing or truncated fixture fails, never skips), the two backends with their section 4
 * tolerances, and the summary `engine/build/reports/golden/m03-summary.txt`.
 */
internal object M03Golden {
    const val CITY_DAYS = 8_300
    const val WINDOW_CITY_DAYS = 2_000
    const val DAYS_PER_CITY = 830
    const val WINDOW_DAYS_PER_CITY = 200
    const val CITIES = 10
    const val ARCSEC = 3600.0

    /** Literal per-kind boundary totals of the committed fixture; `meta.boundaryTotals` must agree. */
    val BOUNDARY_TOTALS = mapOf(
        LimbKind.TITHI to 14_511, LimbKind.KARANA to 29_027, LimbKind.NAKSHATRA to 14_101,
        LimbKind.YOGA to 15_160, LimbKind.MOON_RASHI to 6_261,
    )

    /** Section 4 tolerances of one backend column. Seconds unless stated. */
    class Limits(
        val name: String,
        val boundaryCapS: Double,
        val positionBudgetArcsec: Map<LimbKind, Double>,
        val medianCapS: Double?,
        val sunEventS: Double,
        val windowS: Double,
        val fallbackResidualArcsec: Double,
        val eph: () -> Ephemeris,
    )

    private fun budget(tithiKarana: Double, nakRashi: Double, yoga: Double) = mapOf(
        LimbKind.TITHI to tithiKarana, LimbKind.KARANA to tithiKarana, LimbKind.NAKSHATRA to nakRashi,
        LimbKind.MOON_RASHI to nakRashi, LimbKind.YOGA to yoga,
    )

    val PRECISION = Limits("PRECISION", 5.0, budget(0.1, 0.06, 0.12), null, 5.0, 5.0, 4.0) { GoldenLoader.openDe440() }
    val BASE = Limits("BASE", 120.0, budget(30.0, 10.01, 30.02), 30.0, 10.0, 10.0, 50.0) { AstronomyEngineEphemeris() }

    /** Both durmuhurta and (night) choghadiya rule files shipped under `rules/`; no Gowri, no varjyam. */
    val tables: PanchangTables by lazy {
        PanchangRules.parse(RuleTestSupport.rule("durmuhurta.json"), RuleTestSupport.rule("choghadiya.json"))
    }

    fun fixture(name: String): JsonObject = GoldenLoader.json("/golden/$name")

    fun JsonElement.d(): Double = jsonPrimitive.double
    fun JsonElement.i(): Int = jsonPrimitive.int
    fun JsonElement.l(): Long = jsonPrimitive.long
    fun JsonElement.s(): String = jsonPrimitive.content
    fun JsonElement.a(): JsonArray = jsonArray
    fun JsonElement.o(): JsonObject = jsonObject

    fun meta(root: JsonObject): JsonObject = root.getValue("meta").o()

    class City(val name: String, val place: Place, val offsetMin: Int, val row: JsonObject) {
        val offsetSeconds: Int get() = offsetMin * 60
    }

    /** The ten fixture cities, meta cross-checked against the rows (name, offset) and the literal sizes. */
    fun cities(root: JsonObject, countKey: String): List<City> {
        val metaCities = meta(root).getValue("cities").a()
        val rows = root.getValue("cities").a()
        assertEquals(CITIES, rows.size, "fixture city count")
        assertEquals(CITIES, metaCities.size, "meta city count")
        return rows.mapIndexed { i, r ->
            val m = metaCities[i].o()
            val o = r.o()
            assertEquals(m.getValue("name").s(), o.getValue("name").s(), "city $i name")
            assertEquals(m.getValue("offsetMin").i(), o.getValue("offsetMin").i(), "city $i offset")
            val place = Place(m.getValue("lat").d(), m.getValue("lon").d(), 0.0)
            assertEquals(m.getValue(countKey).i(), o.getValue("days").a().size, "city $i rows vs meta.$countKey")
            City(o.getValue("name").s(), place, o.getValue("offsetMin").i(), o)
        }
    }

    /** Median of [xs] (empty -> NaN, which fails every gate). */
    fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return Double.NaN
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private val summary = sortedMapOf<String, List<String>>()

    /** Replaces the section [title] and rewrites the whole report file. */
    @Synchronized
    fun report(title: String, lines: List<String>) {
        summary[title] = lines
        val dir = File(System.getProperty("user.dir"), "build/reports/golden").apply { mkdirs() }
        File(dir, "m03-summary.txt").writeText(
            summary.entries.joinToString("\n") { (t, l) -> "== $t ==\n" + l.joinToString("\n") }.plus("\n"),
        )
    }

    /** Fails with every entry of [failures] (first ten shown) unless it is empty. */
    fun assertNoFailures(title: String, failures: List<String>) {
        assertTrue(failures.isEmpty(), "$title failures (${failures.size}): ${failures.take(10)}")
    }
}
