package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.astro.EphemerisRangeException
import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.b
import app.parivaar.panchang.engine.calendar.M04Golden.d
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.isNull
import app.parivaar.panchang.engine.calendar.M04Golden.l
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * G-M04 lunations: both backends x 2,487 lunations of `calendar_lunations.json` (docs/specs/M04.md section 4):
 * new and full moons (same count), sankranti sequences and times, masa / adhika / kshaya (equal unless fragile).
 * Rows outside the DE440 excerpt make Precision throw [EphemerisRangeException]: that is asserted for exactly
 * the literal row set (never skipped) and the same rows are compared on Base.
 */
@Suppress(
    "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements", "ReturnCount",
    "SwallowedException",
) // a golden comparison is one flat list of gates per fixture row; splitting scatters the limits
@Tag("golden")
class CalendarLunationsGoldenTest {
    private class Pass(val limits: M04Golden.Limits, val rows: List<JsonArray>) {
        val calc = LunarCalendar(limits.eph())
        val got = arrayOfNulls<Lunation>(rows.size)
        val thrown = sortedSetOf<Int>()
        val failures = M04Golden.Failures()
        val newMoon = M04Golden.Stat(keep = true)
        val fullMoon = M04Golden.Stat(keep = true)
        val sankranti = M04Golden.Stat()
        val outside = M04Golden.Stat()
        var fragile = 0
        var sankrantiCount = 0
    }

    @Test
    fun precision() = run(M04Golden.PRECISION)

    @Test
    fun base() = run(M04Golden.BASE)

    private fun run(limits: M04Golden.Limits) {
        val root = M04Golden.fixture("calendar_lunations.json")
        val meta = M04Golden.meta(root)
        val rows = root.getValue("lunations").a().map { it.a() }
        assertEquals(M04Golden.LUNATIONS, rows.size, "lunation rows")
        assertEquals(M04Golden.LUNATIONS, meta.getValue("lunations").i(), "meta.lunations")
        assertEquals(M04Golden.LUNATIONS_ADHIKA, rows.count { it[6].b() }, "adhika rows")
        assertEquals(M04Golden.LUNATIONS_ADHIKA, meta.getValue("adhika").i(), "meta.adhika")
        assertEquals(M04Golden.LUNATIONS_KSHAYA, rows.count { !it[7].isNull() }, "kshaya rows")
        assertEquals(M04Golden.LUNATIONS_KSHAYA, meta.getValue("kshaya").i(), "meta.kshaya")
        for (j in 1 until rows.size) assertTrue(rows[j][0].l() > rows[j - 1][0].l(), "rows sorted by new moon")

        val p = Pass(limits, rows)
        fetch(p)
        val outsideSet = if (limits === M04Golden.PRECISION) OUTSIDE_EXCERPT else emptySet()
        assertEquals(outsideSet, p.thrown.toSet(), "${limits.name} rows that throw EphemerisRangeException")
        for (j in rows.indices) {
            val l = p.got[j]
            if (l != null) compare(p, j, l, outsideSet.isEmpty() && j in OUTSIDE_EXCERPT)
        }
        val adhika = rows.filter { it[6].b() && yearOf(it) in 2024..2043 }
            .map { Masa.entries[it[5].i()].name + " " + yearOf(it) }
        M04Golden.report(
            "lunations ${limits.name}",
            listOf(
                "rows ${rows.size}, compared ${p.got.count { it != null }}, outside excerpt ${p.thrown.size} " +
                    "(${if (outsideSet.isEmpty()) "compared on Base" else "EphemerisRangeException asserted"})",
                "new moon max ${p.newMoon.fmt()} s (cap ${limits.momentCapS}), median ${p.newMoon.median()} s",
                "full moon max ${p.fullMoon.fmt()} s (cap ${limits.momentCapS}), median ${p.fullMoon.median()} s",
                "sankranti max ${p.sankranti.fmt()} s over ${p.sankrantiCount} sankrantis",
                "outside-excerpt rows on Base: new moon max ${p.outside.fmt()} s",
                "fragile lunations ${rows.count { it[limits.frag(8)].b() }}, adhika ${rows.count { it[6].b() }}, " +
                    "kshaya ${rows.count { !it[7].isNull() }}",
                "adhika 2024-2043: ${adhika.joinToString(", ")}",
            ),
        )
        M04Golden.assertNoFailures("lunations ${limits.name}", p.failures.list())
        limits.momentMedianCapS?.let {
            assertTrue(p.newMoon.median() <= it, "median new moon error ${p.newMoon.median()} s")
            assertTrue(p.fullMoon.median() <= it, "median full moon error ${p.fullMoon.median()} s")
        }
    }

    /** Engine lunations in chunks (rows `i until j`); a throwing chunk is redone row by row (Precision only). */
    private fun fetch(p: Pass) {
        val n = p.rows.size
        var i = 0
        while (i < n) {
            val j = minOf(n, i + CHUNK)
            val chunk = runCatching { query(p, i, j) }
            if (chunk.isSuccess) {
                chunk.getOrThrow().forEachIndexed { k, l -> p.got[i + k] = l }
            } else {
                chunk.exceptionOrNull().let { if (it !is EphemerisRangeException) throw it!! }
                for (r in i until j) {
                    val one = runCatching { query(p, r, r + 1) }
                    if (one.isSuccess) {
                        p.got[r] = one.getOrThrow().single()
                    } else {
                        one.exceptionOrNull().let { if (it !is EphemerisRangeException) throw it!! }
                        p.thrown += r
                    }
                }
            }
            i = j
        }
    }

    private fun query(p: Pass, i: Int, j: Int): List<Lunation> {
        val from = M04Golden.at(p.rows[i][0].l()) - 1.days
        val to = M04Golden.at(p.rows[j - 1][0].l()) + 1.days
        val got = p.calc.lunations(from, to)
        assertEquals(j - i, got.size, "${p.limits.name} lunation count of a chunk")
        return got
    }

    private fun compare(p: Pass, j: Int, l: Lunation, outside: Boolean) {
        val row = p.rows[j]
        val lim = p.limits
        val at = "row $j"
        fun moment(kind: String, got: Instant, ms: Long, rate: Double, stat: M04Golden.Stat) {
            val dt = M04Golden.dtS(got, ms)
            val limit = M04Golden.rateLimit(lim.momentCapS, lim.momentArcsec, rate)
            stat.add(dt, "$kind $at")
            if (!(dt <= limit)) p.failures += "${lim.name} $kind $at |dt| $dt s > $limit s (rate $rate)"
            if (outside) p.outside.add(dt, "$kind $at")
        }
        moment("new moon", l.newMoon, row[0].l(), row[1].d(), p.newMoon)
        moment("full moon", l.fullMoon, row[2].l(), row[3].d(), p.fullMoon)
        val fragile = row[lim.frag(8)].b()
        if (fragile) p.fragile++
        val want = row[4].a()
        val sameSequence = want.size == l.sankrantis.size &&
            want.indices.all { Rashi.entries[want[it].a()[0].i()] == l.sankrantis[it].into }
        if (!sameSequence) {
            if (!fragile) p.failures += "${lim.name} $at sankranti sequence ${l.sankrantis.map { it.into }} vs fixture"
        } else {
            for ((k, sk) in l.sankrantis.withIndex()) {
                val x = want[k].a()
                val dt = M04Golden.dtS(sk.at, x[1].l())
                val v = x[2].d()
                val limit = lim.sankrantiBaseS + if (v > 0.0) lim.sankrantiArcsec / v else Double.NaN
                p.sankranti.add(dt, "$at sankranti $k")
                p.sankrantiCount++
                if (!(dt <= limit)) p.failures += "${lim.name} $at sankranti $k |dt| $dt s > $limit s"
            }
        }
        val got = Triple(l.masa.ordinal, l.adhika, l.kshayaMasa?.ordinal)
        val exp = Triple(row[5].i(), row[6].b(), if (row[7].isNull()) null else row[7].i())
        if (got != exp && !fragile) p.failures += "${lim.name} $at masa/adhika/kshaya $got vs $exp"
    }

    private fun yearOf(row: JsonArray): Int = M04Golden.at(row[0].l()).toString().substring(0, 4).toInt()

    private companion object {
        const val CHUNK = 40

        /**
         * Rows (index in the fixture) whose computation needs ephemeris outside the DE440 excerpt
         * (1900-01-01..2100-01-01): the first lunation and the last 15 rows. Literal, asserted exactly.
         */
        val OUTSIDE_EXCERPT: Set<Int> = setOf(0) + (2472..2486)
    }
}
