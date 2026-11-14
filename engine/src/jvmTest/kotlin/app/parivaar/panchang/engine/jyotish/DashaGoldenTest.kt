package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.astro.MaxError
import app.parivaar.panchang.engine.jyotish.M02Golden.a
import app.parivaar.panchang.engine.jyotish.M02Golden.d
import app.parivaar.panchang.engine.jyotish.M02Golden.i
import app.parivaar.panchang.engine.jyotish.M02Golden.l
import app.parivaar.panchang.engine.jyotish.M02Golden.o
import app.parivaar.panchang.engine.jyotish.M02Golden.s
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** 300 Vimshottari and 100 Yogini cases: every boundary within 1 ms, every lord path equal (spec section 4). */
@Tag("golden")
class DashaGoldenTest {
    private companion object {
        const val LIMIT_MS = 1.0
    }

    private val ms = MaxError()
    private val failures = mutableListOf<String>()
    private var boundaries = 0

    private fun yearOf(days: Double): DashaYear =
        DashaYear.entries.single { it.days == days }

    private fun <L> check(want: JsonElement, got: DashaPeriod<L>, at: String) {
        val w = want.a()
        val path = got.lords.joinToString("/") { it.toString() }
        if (w[0].s() != path) failures += "lord path $at want=${w[0].s()} got=$path"
        ms.add(abs((got.start.toEpochMilliseconds() - w[1].l()).toDouble()), "$at start")
        ms.add(abs((got.end.toEpochMilliseconds() - w[2].l()).toDouble()), "$at end")
        boundaries += 2
    }

    private fun <L> verify(tree: DashaTree<L>, c: kotlinx.serialization.json.JsonObject, maha: Int, at: String) {
        val mahaWant = c.getValue("maha").a()
        assertEquals(maha, mahaWant.size, "maha count $at")
        assertEquals(mahaWant.size, tree.maha.size, "engine maha count $at")
        mahaWant.forEachIndexed { i, w -> check(w, tree.maha[i], "$at maha[$i]") }
        val antarWant = c.getValue("antar").a()
        val antar = tree.maha.take(maha).flatMap { tree.children(it) }
        assertEquals(antarWant.size, antar.size, "antar count $at")
        antarWant.forEachIndexed { i, w -> check(w, antar[i], "$at antar[$i]") }
        for ((pi, probe) in c.getValue("probes").a().withIndex()) {
            val p = probe.o()
            val chain = p.getValue("chain").a()
            val got = tree.at(Instant.fromEpochMilliseconds(p.getValue("ms").l()))
            assertEquals(chain.size, got.size, "probe depth $at")
            assertEquals(tree.maxDepth, got.size, "probe depth is maxDepth $at")
            chain.forEachIndexed { lvl, w -> check(w, got[lvl], "$at probe[$pi] level ${lvl + 1}") }
        }
    }

    @Test
    fun dashasMatchOracle() {
        val root = M02Golden.fixture("dasha.json")
        val rows = M02Golden.rows(root, "v", 400, "count")
        assertEquals(300, M02Golden.metaInt(root, "vimshottari"))
        assertEquals(100, M02Golden.metaInt(root, "yogini"))
        var vim = 0
        var yog = 0
        for ((n, row) in rows.withIndex()) {
            val c = row.o()
            val birth = Instant.fromEpochMilliseconds(c.getValue("birthMs").l())
            val moon = c.getValue("moon").d()
            val year = yearOf(c.getValue("yearDays").d())
            val at = "case $n"
            when (c.getValue("kind").s()) {
                "VIM" -> {
                    verify(Dashas.vimshottari(moon, birth, year), c, 9, at)
                    vim++
                }
                "YOG" -> {
                    verify(Dashas.yogini(moon, birth, year), c, 32, at)
                    yog++
                }
                else -> failures += "unknown case kind $at"
            }
        }
        assertEquals(300, vim)
        assertEquals(100, yog)
        val nonZero = failures.size
        M02Golden.report(
            "Dasha (300 Vimshottari + 100 Yogini)",
            listOf(
                "boundaries checked=$boundaries max ms=${ms.value} limit ms=$LIMIT_MS at ${ms.where}",
                "lord path mismatches=$nonZero",
            ),
        )
        if (!(ms.value <= LIMIT_MS)) failures += "boundary ${ms.value} ms > $LIMIT_MS ms at ${ms.where}"
        M02Golden.assertNoFailures("dasha", failures)
        assertEquals(ms.count, boundaries)
    }
}
