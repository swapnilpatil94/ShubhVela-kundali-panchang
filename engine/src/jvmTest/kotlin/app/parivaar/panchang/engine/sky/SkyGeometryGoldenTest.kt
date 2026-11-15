package app.parivaar.panchang.engine.sky

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.EclipticPos
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.astro.MaxError
import app.parivaar.panchang.engine.astro.TimeScales
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Tag
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * M07R B5: sky geometry of both backends against `sky_geometry.json` (tools/oracle/gen_sky.py: Skyfield 1.55 +
 * DE440, own geometry). Row counts are asserted from literals and from `meta`; a missing fixture fails; unknown
 * row kinds fail; every gate is `!(err <= limit)` so a NaN fails.
 *
 * Limits are derived from the M01 position gates (PositionsGoldenTest: Precision 0.05" Sun/Moon, 0.1" planets;
 * Base 20" Sun, 10" Moon, 60" planets): a direction error is at most sqrt(2) times the per-coordinate gate, d.
 * - elongation: d(body) + d(Sun) + m
 * - phase angle: A (d(body) + d(Sun)) + m, A = R_max / r_min (|di/dpsi| = R / r at both conjunctions)
 * - k: half the phase-angle limit in radians (|dk| = sin(i) / 2 |di|)
 * - chi, weighted by sin(elongation): d(Sun) + 1.6 d(body) + m (a moved body also turns its local north by up to
 *   tan(dec) <= 0.6 of the move); q, weighted by sin(zenith distance): 1.6 d(Moon) + m
 * - ring tilt B: d(Saturn) + m (pole constant identical on both sides; only the geometry is tested)
 * m = model margin: Precision 0.05" (Meeus 23.2 velocity vs barycentric, ~0.02"; deflection inverse, rounding);
 * Base 2" (the Base backend has no light deflection but the engine removes one; <= 1.5" for Mercury 1 degree
 * from the Sun).
 */
@Tag("golden")
class SkyGeometryGoldenTest {
    private class Limits(
        val name: String,
        val sunArcsec: Double,
        val moonArcsec: Double,
        val planetArcsec: Double,
        val margin: Double,
    )

    private val precision = Limits("PRECISION", 0.05, 0.05, 0.1, 0.05)
    private val base = Limits("BASE", 20.0, 10.0, 60.0, 2.0)

    private fun Limits.dir(body: Body): Double = SQRT2 * when (body) {
        Body.SUN -> sunArcsec
        Body.MOON -> moonArcsec
        else -> planetArcsec
    }

    private fun amplification(body: Body): Double = when (body) {
        Body.MOON -> 1.01
        Body.MERCURY -> 1.0167 / 0.3075
        Body.VENUS -> 1.0167 / 0.7184
        Body.MARS -> 1.0167 / 1.3814
        Body.JUPITER -> 1.0167 / 4.95
        Body.SATURN -> 1.0167 / 9.0
        else -> error("no amplification for ${body.name}")
    }

    private class Stat {
        val elong = MaxError()
        val phase = MaxError()
        val k = MaxError()
        val chi = MaxError()
    }

    private class Fixture(val meta: JsonObject, val rows: List<JsonArray>, val places: List<Place>)

    private fun load(): Fixture {
        val root = GoldenLoader.json("/golden/sky_geometry.json")
        val meta = root.getValue("meta").jsonObject
        val rows = root.getValue("rows").jsonArray.map { it.jsonArray }
        val counts = meta.getValue("counts").jsonObject
        assertEquals(EPOCHS, counts.getValue("epochs").jsonPrimitive.int, "meta epochs")
        assertEquals(BODY_ROWS, counts.getValue("body").jsonPrimitive.int, "meta body rows")
        assertEquals(EPOCHS, counts.getValue("ring").jsonPrimitive.int, "meta ring rows")
        assertEquals(PAR_ROWS, counts.getValue("parallactic").jsonPrimitive.int, "meta parallactic rows")
        assertEquals(BODY_ROWS + EPOCHS + PAR_ROWS, rows.size, "truncated sky_geometry.json")
        val bodies = meta.getValue("bodies").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(LIT.map { it.name }, bodies, "sky_geometry.json bodies")
        val places = meta.getValue("places").jsonArray.map {
            val o = it.jsonObject
            Place(o.getValue("lat").jsonPrimitive.double, o.getValue("lon").jsonPrimitive.double)
        }
        assertEquals(PLACES, places.size, "sky_geometry.json places")
        val pole = meta.getValue("saturnPole").jsonObject
        assertEquals(listOf(40.589, -0.036), pole.getValue("alpha0Deg").jsonArray.map { it.jsonPrimitive.double })
        assertEquals(listOf(83.537, -0.004), pole.getValue("delta0Deg").jsonArray.map { it.jsonPrimitive.double })
        return Fixture(meta, rows, places)
    }

    /** One backend over the fixture; positions cached per epoch (rows of one epoch are consecutive). */
    private inner class Pass(val lim: Limits, val eph: Ephemeris, val places: List<Place>) {
        val stats = LIT.associateWith { Stat() }
        val ring = MaxError()
        val par = MaxError()
        val failures = mutableListOf<String>()
        private var lastMs = Long.MIN_VALUE
        private var cache: Map<Body, EclipticPos> = emptyMap()

        fun row(row: JsonArray) {
            val kind = row[0].jsonPrimitive.content
            val ms = row[1].jsonPrimitive.long
            val at = Instant.fromEpochMilliseconds(ms)
            val tt = TimeScales.utcToTt(at)
            if (ms != lastMs) {
                cache = (LIT + Body.SUN).associateWith { eph.geoApparent(it, tt) }
                lastMs = ms
            }
            val where = "utcMs=$ms"
            when (kind) {
                "body" -> body(row, tt, where)
                "ring" -> {
                    val b = SkyGeometry.saturnRingTiltDeg(cache.getValue(Body.SATURN), cache.getValue(Body.SUN), tt)
                    gate(abs(b - row[2].d()) * ARCSEC, ringLimit(), ring, "$where ring B")
                }
                "parallactic" -> {
                    val place = places[row[2].jsonPrimitive.int]
                    require(row[3].jsonPrimitive.content == "MOON") { "unexpected parallactic body in fixture" }
                    val q = SkyGeometry.parallacticAngleDeg(cache.getValue(Body.MOON), place, at)
                    val err = abs(wrap180(q - row[4].d())) * ARCSEC * sin(row[5].d() * DEG)
                    gate(err, parLimit(), par, "$where q*sin(z)")
                }
                else -> error("unknown row kind in fixture: $kind")
            }
        }

        private fun body(row: JsonArray, tt: JulianDay, where: String) {
            val body = Body.valueOf(row[2].jsonPrimitive.content)
            require(body in LIT) { "unexpected body in fixture" }
            val pos = cache.getValue(body)
            val sun = cache.getValue(Body.SUN)
            val s = stats.getValue(body)
            val i = SkyGeometry.phaseAngleDeg(pos, sun, tt)
            val chi = SkyGeometry.brightLimbAngleDeg(pos, sun, tt)
            val at = "$body $where"
            gate(abs(SkyGeometry.elongationDeg(pos, sun) - row[3].d()) * ARCSEC, elongLimit(body), s.elong, "$at elong")
            gate(abs(i - row[4].d()) * ARCSEC, phaseLimit(body), s.phase, "$at phase")
            gate(abs(SkyGeometry.illuminatedFraction(i) - row[5].d()), kLimit(body), s.k, "$at k")
            val chiErr = abs(wrap180(chi - row[6].d())) * ARCSEC * sin(row[3].d() * DEG)
            gate(chiErr, chiLimit(body), s.chi, "$at chi*sin(psi)")
        }

        /** Records err / limit and fails unless err <= limit (a NaN fails). */
        private fun gate(err: Double, limit: Double, stat: MaxError, what: String) {
            stat.add(err / limit, what)
            if (!(err <= limit)) failures += "${lim.name} $what $err > $limit"
        }

        fun elongLimit(b: Body) = lim.dir(b) + lim.dir(Body.SUN) + lim.margin
        fun phaseLimit(b: Body) = amplification(b) * (lim.dir(b) + lim.dir(Body.SUN)) + lim.margin
        fun kLimit(b: Body) = phaseLimit(b) / ARCSEC * DEG / 2.0
        fun chiLimit(b: Body) = lim.dir(Body.SUN) + CHI_BODY_FACTOR * lim.dir(b) + lim.margin
        fun ringLimit() = lim.dir(Body.SATURN) + lim.margin
        fun parLimit() = CHI_BODY_FACTOR * lim.dir(Body.MOON) + lim.margin

        fun lines(): List<String> = stats.map { (b, s) ->
            "${b.name}: n=${s.elong.count} max/limit elong=${s.elong.value} of ${elongLimit(b)}\" " +
                "phase=${s.phase.value} of ${phaseLimit(b)}\" k=${s.k.value} of ${kLimit(b)} " +
                "chi*sin(psi)=${s.chi.value} of ${chiLimit(b)}\""
        } + "RING: n=${ring.count} max/limit B=${ring.value} of ${ringLimit()}\"" +
            "PARALLACTIC: n=${par.count} max/limit q*sin(z)=${par.value} of ${parLimit()}\""
    }

    private fun run(lim: Limits, eph: Ephemeris) {
        val fx = load()
        val pass = Pass(lim, eph, fx.places)
        fx.rows.forEach(pass::row)
        val lines = pass.lines()
        GoldenLoader.report("M07R sky geometry ${lim.name} (max error / limit)", lines)
        println(lines.joinToString("\n", prefix = "M07R sky ${lim.name}\n"))
        assertEquals(BODY_ROWS / LIT.size, pass.stats.getValue(Body.SATURN).elong.count, "body rows checked")
        assertEquals(BODY_ROWS, pass.stats.values.sumOf { it.k.count }, "body rows checked")
        assertEquals(EPOCHS, pass.ring.count, "ring rows checked")
        assertEquals(PAR_ROWS, pass.par.count, "parallactic rows checked")
        assertTrue(pass.failures.isEmpty(), "M07R sky failures (${pass.failures.size}): ${pass.failures.take(10)}")
    }

    @Test
    fun precisionBackendMatchesOracle() = run(precision, GoldenLoader.openDe440())

    @Test
    fun baseBackendMatchesOracle() = run(base, AstronomyEngineEphemeris())

    private companion object {
        const val EPOCHS = 2_004
        const val PLACES = 8
        const val BODY_ROWS = EPOCHS * 6
        const val PAR_ROWS = EPOCHS * PLACES
        const val ARCSEC = 3600.0
        const val DEG = PI / 180.0
        const val CHI_BODY_FACTOR = 1.6

        val SQRT2 = sqrt(2.0)
        val LIT = listOf(Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS, Body.JUPITER, Body.SATURN)

        fun JsonElement.d(): Double = jsonPrimitive.double

        fun wrap180(d: Double): Double {
            var x = d % 360.0
            if (x > 180.0) x -= 360.0
            if (x <= -180.0) x += 360.0
            return x
        }
    }
}
