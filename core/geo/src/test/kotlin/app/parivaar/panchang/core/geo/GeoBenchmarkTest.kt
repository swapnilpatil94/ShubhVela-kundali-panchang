package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.geo.internal.TzOverlay
import app.parivaar.panchang.core.model.geo.TzContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Architect budgets from M05 spec section 5; tighten only, never loosen. */
class GeoBenchmarkTest {
    @Test fun seedSearchP95Within5ms() {
        val store = SeedTestSupport.store
        val words = store.places.map { it.name.lowercase().take(4) }.filter { it.length >= 2 }
        val queries = List(QUERIES) { words[it % words.size] }
        repeat(WARMUP_QUERIES) { store.search(queries[it % queries.size]) }
        val nanos = LongArray(QUERIES) { i ->
            val t0 = System.nanoTime()
            store.search(queries[i])
            System.nanoTime() - t0
        }.sortedArray()
        val p95Ms = nanos[(QUERIES * P95).toInt() - 1] / NANOS_PER_MS
        println("seed search p95 ms: $p95Ms")
        assertTrue(p95Ms <= SEARCH_BUDGET_MS, "p95 over budget")
    }

    @Test fun offsetAtMeanWithin50us() {
        val dir = requireNotNull(System.getProperty("pp.rules.dir"))
        val resolver = JavaTimeOffsetResolver(TzOverlay.parse(File(dir, "tz_overlay.json").readText()))
        val ctx = TzContext("America/New_York", -74.0, null)
        val base = Instant.fromEpochSeconds(BASE_EPOCH)
        repeat(WARMUP_CALLS) { resolver.offsetAt(Instant.fromEpochSeconds(base.epochSeconds + it * STEP), ctx) }
        val t0 = System.nanoTime()
        repeat(CALLS) { resolver.offsetAt(Instant.fromEpochSeconds(base.epochSeconds + it * STEP), ctx) }
        val meanNs = (System.nanoTime() - t0) / CALLS
        println("offsetAt mean ns: $meanNs")
        assertTrue(meanNs <= OFFSET_BUDGET_US * NANOS_PER_US, "mean over budget")
    }

    private companion object {
        const val QUERIES = 500
        const val WARMUP_QUERIES = 200
        const val P95 = 0.95
        const val NANOS_PER_MS = 1_000_000.0
        const val NANOS_PER_US = 1_000L
        const val SEARCH_BUDGET_MS = 5.0
        const val OFFSET_BUDGET_US = 50L
        const val CALLS = 100_000
        const val WARMUP_CALLS = 10_000
        const val BASE_EPOCH = 1_000_000_000L
        const val STEP = 3_600L
    }
}
