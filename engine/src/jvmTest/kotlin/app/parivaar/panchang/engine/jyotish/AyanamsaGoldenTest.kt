package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.MaxError
import app.parivaar.panchang.engine.jyotish.M02Golden.ARCSEC
import app.parivaar.panchang.engine.jyotish.M02Golden.a
import app.parivaar.panchang.engine.jyotish.M02Golden.d
import app.parivaar.panchang.engine.jyotish.M02Golden.s
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lahiri against the four IAE values and Lahiri and True Chitra against the grid (docs/specs/M02.md section 4). */
@Tag("golden")
class AyanamsaGoldenTest {
    private companion object {
        const val IAE_LIMIT = 0.1
        const val LAHIRI_MEAN_LIMIT = 0.005
        const val LAHIRI_TRUE_LIMIT = 0.01
        const val CHITRA_LIMIT = 0.01
    }

    @Test
    fun ayanamsaMatchesIaeAndGrid() {
        val root = M02Golden.fixture("ayanamsa.json")
        val iae = M02Golden.rows(root, "iae", 4, "iaeCount")
        val grid = M02Golden.rows(root, "grid", 2000, "gridCount")
        val iaeErr = MaxError()
        for (row in iae) {
            val r = row.a()
            val tt = JulianDay(r[0].d())
            val got = if (r[1].s() == "true") {
                Ayanamsa.trueDeg(AyanamsaId.LAHIRI, tt)
            } else {
                Ayanamsa.meanDeg(AyanamsaId.LAHIRI, tt)
            }
            iaeErr.add(abs(got - r[2].d()) * ARCSEC, "jd=${r[0].d()} ${r[1].s()}")
        }
        val lm = MaxError()
        val lt = MaxError()
        val cm = MaxError()
        val ct = MaxError()
        for (row in grid) {
            val r = row.a()
            val tt = JulianDay(r[0].d())
            val at = "tt=${r[0].d()}"
            lm.add(abs(Ayanamsa.meanDeg(AyanamsaId.LAHIRI, tt) - r[1].d()) * ARCSEC, at)
            lt.add(abs(Ayanamsa.trueDeg(AyanamsaId.LAHIRI, tt) - r[2].d()) * ARCSEC, at)
            cm.add(abs(Ayanamsa.meanDeg(AyanamsaId.TRUE_CHITRA, tt) - r[3].d()) * ARCSEC, at)
            ct.add(abs(Ayanamsa.trueDeg(AyanamsaId.TRUE_CHITRA, tt) - r[4].d()) * ARCSEC, at)
        }
        val rows = listOf(
            Triple("IAE (4 rows)", iaeErr, IAE_LIMIT),
            Triple("Lahiri mean", lm, LAHIRI_MEAN_LIMIT),
            Triple("Lahiri true", lt, LAHIRI_TRUE_LIMIT),
            Triple("True Chitra mean", cm, CHITRA_LIMIT),
            Triple("True Chitra true", ct, CHITRA_LIMIT),
        )
        M02Golden.report(
            "Ayanamsa (4 IAE + 2000 grid)",
            rows.map { (n, e, lim) -> "$n: n=${e.count} max\"=${e.value} limit\"=$lim at ${e.where}" },
        )
        M02Golden.assertNoFailures(
            "ayanamsa",
            rows.filter { (_, e, lim) -> !(e.value <= lim) }
                .map { (n, e, lim) -> "$n ${e.value}\" > $lim\" at ${e.where}" },
        )
        assertEquals(listOf(4, 2000, 2000, 2000, 2000), rows.map { it.second.count })
    }
}
