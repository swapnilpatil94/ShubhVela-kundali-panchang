package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.MaxError
import app.parivaar.panchang.engine.jyotish.M02Golden.a
import app.parivaar.panchang.engine.jyotish.M02Golden.d
import app.parivaar.panchang.engine.jyotish.M02Golden.i
import app.parivaar.panchang.engine.jyotish.M02Golden.l
import app.parivaar.panchang.engine.jyotish.M02Golden.s
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * JUPITER, SATURN and RAHU (mean node) ingresses 1950..2050, Lahiri, Precision backend: same sequence and
 * |dt| <= 0.5 s + 0.12" / |v| (docs/specs/M02.md section 4). The Base backend is not gated.
 */
@Tag("golden")
class IngressGoldenTest {
    private companion object {
        const val SLACK_S = 0.5
        const val SLACK_ARCSEC = 0.12
        const val SECONDS_PER_DAY = 86_400.0
        const val ARCSEC_PER_DEG = 3600.0
    }

    @Test
    fun ingressesMatchOracle() {
        val root = M02Golden.fixture("ingress.json")
        val rows = M02Golden.rows(root, "v", 317, "count")
        val gochar = Gochar(GoldenLoader.openDe440())
        val settings = CalcSettings(ayanamsa = AyanamsaId.LAHIRI, node = NodeType.MEAN)
        val from = Instant.parse("1950-01-01T00:00:00Z")
        val to = Instant.parse("2050-01-01T00:00:00Z")
        val failures = mutableListOf<String>()
        val lines = mutableListOf<String>()
        var total = 0
        for (graha in listOf(Graha.JUPITER, Graha.SATURN, Graha.RAHU)) {
            val want = rows.map { it.a() }.filter { it[0].s() == graha.name }
            val got = gochar.ingresses(graha, from, to, settings)
            total += want.size
            if (want.size != got.size) failures += "${graha.name}: ${want.size} expected, ${got.size} found"
            val worst = MaxError()
            for (k in 0 until minOf(want.size, got.size)) {
                val w = want[k]
                val g = got[k]
                val at = "${graha.name}[$k]"
                if (g.into.ordinal != w[1].i() || g.retrograde != w[2].jsonPrimitive.boolean) {
                    failures += "$at sequence differs"
                }
                val speedArcsecPerSec = abs(w[4].d()) * ARCSEC_PER_DEG / SECONDS_PER_DAY
                val limit = SLACK_S + SLACK_ARCSEC / speedArcsecPerSec
                val dt = abs((g.at.toEpochMilliseconds() - w[3].l()).toDouble()) / 1000.0
                worst.add(dt / limit, at) // fraction of the allowed |dt|; must be <= 1
                if (!(dt <= limit)) failures += "$at dt=$dt s > $limit s"
            }
            lines += "${graha.name}: events=${want.size} max |dt|/limit=${worst.value} (<= 1) at ${worst.where}"
        }
        assertEquals(317, total)
        M02Golden.report("Ingress (Precision, 317 events)", lines)
        M02Golden.assertNoFailures("ingress", failures)
    }
}
