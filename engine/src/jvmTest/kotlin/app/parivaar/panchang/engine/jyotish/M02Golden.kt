package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.astro.GoldenLoader
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
 * Shared harness of the M02 golden tests: fixture loading with row-count assertions (a missing or truncated
 * fixture fails, never skips), and the summary report `engine/build/reports/golden/m02-summary.txt`.
 */
internal object M02Golden {
    const val ARCSEC = 3600.0

    /** The fixture [name] (under `/golden/`), with `meta.count`-style sizes asserted by the caller. */
    fun fixture(name: String): JsonObject = GoldenLoader.json("/golden/$name")

    fun JsonElement.d(): Double = jsonPrimitive.double
    fun JsonElement.i(): Int = jsonPrimitive.int
    fun JsonElement.l(): Long = jsonPrimitive.long
    fun JsonElement.s(): String = jsonPrimitive.content
    fun JsonElement.a(): JsonArray = jsonArray
    fun JsonElement.o(): JsonObject = jsonObject

    /** The `meta.<key>` integer of [root]. */
    fun metaInt(root: JsonObject, key: String): Int = root.getValue("meta").o().getValue(key).i()

    /** Array [key] of [root] with exactly [expected] rows; also checks `meta.<countKey>` agrees. */
    fun rows(root: JsonObject, key: String, expected: Int, countKey: String): JsonArray {
        val rows = root.getValue(key).a()
        assertEquals(expected, rows.size, "fixture $key row count (truncated or wrong fixture)")
        assertEquals(expected, metaInt(root, countKey), "fixture meta.$countKey")
        return rows
    }

    private val summary = sortedMapOf<String, List<String>>()

    /** Replaces the section [title] and rewrites the whole report file. */
    @Synchronized
    fun report(title: String, lines: List<String>) {
        summary[title] = lines
        val dir = File(System.getProperty("user.dir"), "build/reports/golden").apply { mkdirs() }
        File(dir, "m02-summary.txt").writeText(
            summary.entries.joinToString("\n") { (t, l) -> "== $t ==\n" + l.joinToString("\n") }.plus("\n"),
        )
    }

    /** Fails with every entry of [failures] (first ten shown) unless it is empty. */
    fun assertNoFailures(title: String, failures: List<String>) {
        assertTrue(failures.isEmpty(), "$title failures (${failures.size}): ${failures.take(10)}")
    }
}
