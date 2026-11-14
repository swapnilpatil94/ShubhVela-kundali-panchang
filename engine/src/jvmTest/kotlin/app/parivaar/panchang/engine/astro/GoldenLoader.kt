package app.parivaar.panchang.engine.astro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Loads the committed fixtures from the `:engine-golden` resources and collects the golden summary report.
 * A missing fixture is a failure (it throws), never a skip.
 */
internal object GoldenLoader {
    private const val BIN = "/ephem/pp-de440-1900-2100.bin"

    fun resourceBytes(path: String): ByteArray =
        checkNotNull(GoldenLoader::class.java.getResourceAsStream(path)) { "missing golden fixture $path" }
            .use { it.readBytes() }

    val expectedSha256: String by lazy {
        resourceBytes("$BIN.sha256").decodeToString().trim().substringBefore(' ')
    }

    private val binBytes: ByteArray by lazy { resourceBytes(BIN) }

    /** A fresh instance per call: ephemeris instances hold caches and are not thread-safe. */
    fun openDe440(): De440Ephemeris = De440Ephemeris.open(ByteArrayBinSource(binBytes), expectedSha256)

    fun json(path: String): JsonObject = Json.parseToJsonElement(resourceBytes(path).decodeToString()).jsonObject

    // ---- summary report: engine/build/reports/golden/m01-summary.txt ----

    private val summary = sortedMapOf<String, List<String>>()

    /** Replaces the section [title] and rewrites the whole report file. */
    @Synchronized
    fun report(title: String, lines: List<String>) {
        summary[title] = lines
        val dir = File(System.getProperty("user.dir"), "build/reports/golden").apply { mkdirs() }
        File(dir, "m01-summary.txt").writeText(
            summary.entries.joinToString("\n") { (t, l) -> "== $t ==\n" + l.joinToString("\n") }.plus("\n"),
        )
    }
}

/**
 * Running maximum of an error with the vector it occurred on.
 *
 * A non-finite error (NaN or infinite, e.g. from an engine NaN) is recorded as +Infinity and stays the
 * maximum, so it fails every `value > limit` gate. No vector can be skipped silently (spec M01 section 4).
 */
internal class MaxError {
    var value = 0.0
        private set
    var where = ""
        private set
    var count = 0
        private set

    fun add(error: Double, at: String) {
        count++
        if (!error.isFinite()) {
            if (value.isFinite()) where = "$at (non-finite error: $error)"
            value = Double.POSITIVE_INFINITY
            return
        }
        if (error > value || count == 1) {
            value = error
            where = at
        }
    }
}
