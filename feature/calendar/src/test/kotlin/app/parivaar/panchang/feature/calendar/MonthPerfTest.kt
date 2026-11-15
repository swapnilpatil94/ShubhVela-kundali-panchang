@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import kotlin.test.Test
import org.junit.jupiter.api.Tag
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth

/**
 * Benchmark (tag `benchmark`): excluded from `testDebugUnitTest`; run alone with
 * `gradle --offline :feature:calendar:benchmarkTest`. It must run alone: parallel builds make timings flaky.
 * Months are timed in order, so a third of them are cold (a new quarter block is computed) and the rest are
 * warm; the median is the warm cost, the maximum the cold one.
 */
@Tag("benchmark")
class MonthPerfTest {
    @Test fun `month build median is under 100 ms after warm-up`() {
        val i = Fx.inputs()
        val engine = Real.newEngine()
        val proof = ProofInfo("", "", "", null)
        fun once(ym: YearMonth): Double {
            val dow = DayOfWeek.SUNDAY
            val t0 = System.nanoTime()
            val b = engine.month(MonthBuilder.gridFirst(ym, dow), MonthBuilder.rows(ym, dow) * 7, i)
            MonthBuilder.build(ym, Fx.today, dow, i, b, Fx.key(ym, i), proof)
            return (System.nanoTime() - t0) / 1e6
        }
        for (m in 1..3) once(YearMonth(2025, m))
        val times = (1..12).map { once(YearMonth(2026, it)) }
        val median = times.sorted().let { (it[5] + it[6]) / 2 }
        println("MonthPerf ms: ${times.joinToString { "%.1f".format(it) }}; median %.1f; max %.1f".format(median, times.max()))
        assertTrue(median < BUDGET_MS, "median $median ms >= $BUDGET_MS ms")
    }

    private companion object { const val BUDGET_MS = 100.0 }
}
