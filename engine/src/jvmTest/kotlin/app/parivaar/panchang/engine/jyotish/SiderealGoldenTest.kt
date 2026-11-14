package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.MaxError
import app.parivaar.panchang.engine.jyotish.M02Golden.ARCSEC
import app.parivaar.panchang.engine.jyotish.M02Golden.a
import app.parivaar.panchang.engine.jyotish.M02Golden.d
import app.parivaar.panchang.engine.jyotish.M02Golden.o
import app.parivaar.panchang.engine.jyotish.M02Golden.s
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sidereal longitude = engine `siderealDeg` against `positions.json` lambda minus the grid ayanamsa;
 * 2000 epochs x 9 bodies x 2 ayanamsas (docs/specs/M02.md section 4).
 */
@Tag("golden")
class SiderealGoldenTest {
    private val bodies = listOf(
        Body.SUN, Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS, Body.JUPITER, Body.SATURN,
        Body.RAHU_MEAN, Body.RAHU_TRUE,
    )

    private fun limit(precision: Boolean, b: Body): Double = if (precision) {
        if (b == Body.SUN || b == Body.MOON) 0.05 else 0.1
    } else {
        when (b) {
            Body.MOON -> 10.0
            Body.SUN -> 20.0
            Body.RAHU_MEAN -> 0.1
            else -> 60.0
        }
    }

    private fun run(name: String, precision: Boolean, eph: Ephemeris) {
        val pos = M02Golden.fixture("positions.json")
        val names = pos.getValue("meta").o().getValue("bodies").a().map { it.s() }
        assertEquals(bodies.map { it.name }, names, "positions.json body order")
        val posRows = pos.getValue("v").a()
        val grid = M02Golden.rows(M02Golden.fixture("ayanamsa.json"), "grid", 2000, "gridCount")
        val stats = linkedMapOf<String, MaxError>()
        for ((id, col) in listOf(AyanamsaId.LAHIRI to 2, AyanamsaId.TRUE_CHITRA to 4)) {
            for (i in 0 until 2000) {
                val p = posRows[i].a()
                val g = grid[i].a()
                assertEquals(p[0].d(), g[0].d(), "epoch $i: positions and ayanamsa grids must share the TT")
                val tt = JulianDay(p[0].d())
                for ((b, body) in bodies.withIndex()) {
                    val expected = Zodiac.wrap180(p[1].a()[b].a()[0].d() - g[col].d())
                    val got = Ayanamsa.siderealDeg(eph.geoApparent(body, tt).lonDeg, id, tt)
                    stats.getOrPut("$id ${body.name}") { MaxError() }
                        .add(abs(Zodiac.wrap180(got - expected)) * ARCSEC, "tt=${p[0].d()}")
                }
            }
        }
        val lines = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for ((key, e) in stats) {
            val body = bodies.first { key.endsWith(" " + it.name) }
            val lim = limit(precision, body)
            lines += "$key: n=${e.count} max\"=${e.value} limit\"=$lim at ${e.where}"
            if (!(e.value <= lim)) failures += "$key ${e.value}\" > $lim\" at ${e.where}"
            assertEquals(2000, e.count, "vectors checked for $key")
        }
        assertEquals(18, stats.size)
        M02Golden.report("Sidereal $name (2000 epochs x 9 bodies x 2 ayanamsas)", lines)
        M02Golden.assertNoFailures("sidereal $name", failures)
    }

    @Test
    fun precisionBackendMatchesOracle() = run("PRECISION", true, GoldenLoader.openDe440())

    @Test
    fun baseBackendMatchesOracle() = run("BASE", false, AstronomyEngineEphemeris())
}
