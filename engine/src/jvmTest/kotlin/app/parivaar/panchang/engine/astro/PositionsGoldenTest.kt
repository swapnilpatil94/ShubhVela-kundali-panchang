package app.parivaar.panchang.engine.astro

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** G2: both backends x 10,000 epochs x 9 bodies against Skyfield + DE440 (docs/specs/M01.md section 4). */
@Tag("golden")
class PositionsGoldenTest {
    private enum class Backend { PRECISION, BASE }

    private val oracleBodies = listOf(
        Body.SUN, Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS, Body.JUPITER, Body.SATURN,
        Body.RAHU_MEAN, Body.RAHU_TRUE,
    )

    private fun wrap180(d: Double): Double {
        var x = d % 360.0
        if (x > 180.0) x -= 360.0
        if (x <= -180.0) x += 360.0
        return x
    }

    /** Longitude/latitude limit in arcseconds (the beta limit `*` reuses the lambda limit). */
    private fun angleLimitArcsec(b: Backend, body: Body): Double = when (b) {
        Backend.PRECISION -> if (body == Body.SUN || body == Body.MOON) 0.05 else 0.1
        Backend.BASE -> when (body) {
            Body.MOON -> 10.0
            Body.SUN -> 20.0
            Body.RAHU_MEAN, Body.KETU_MEAN -> 0.1
            else -> 60.0
        }
    }

    private fun speedLimit(b: Backend) = if (b == Backend.PRECISION) 1e-5 else 1e-2

    private class Vector(val tt: Double, val rows: List<Triple<Double, Double, Double>>)

    private fun loadVectors(): List<Vector> {
        val root = GoldenLoader.json("/golden/positions.json")
        val names = root.getValue("meta").jsonObject.getValue("bodies").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(oracleBodies.map { it.name }, names, "positions.json body order")
        val v = root.getValue("v").jsonArray
        assertEquals(10_000, v.size, "truncated positions.json (epochs)")
        return v.map { e ->
            val a = e.jsonArray
            val rows = a[1].jsonArray.map { r ->
                val x = r.jsonArray
                Triple(x[0].jsonPrimitive.double, x[1].jsonPrimitive.double, x[2].jsonPrimitive.double)
            }
            assertEquals(9, rows.size, "positions.json bodies per epoch")
            Vector(a[0].jsonPrimitive.double, rows)
        }
    }

    private class Stat {
        val lon = MaxError()
        val lat = MaxError()
        val speed = MaxError()
    }

    private fun run(backend: Backend, eph: Ephemeris) {
        val vectors = loadVectors()
        val stats = linkedMapOf<Body, Stat>()
        val checked = linkedMapOf<Body, Stat>()
        for (b in oracleBodies + listOf(Body.KETU_MEAN, Body.KETU_TRUE)) {
            stats[b] = Stat()
            checked[b] = stats.getValue(b)
        }
        for (vec in vectors) {
            val tt = JulianDay(vec.tt)
            for ((i, body) in oracleBodies.withIndex()) {
                val (lon, lat, speed) = vec.rows[i]
                val at = "tt=${vec.tt}"
                val e = eph.geoApparent(body, tt)
                val s = stats.getValue(body)
                s.lon.add(abs(wrap180(e.lonDeg - lon)) * 3600.0, at)
                s.lat.add(abs(e.latDeg - lat) * 3600.0, at)
                s.speed.add(abs(e.speedDegPerDay - speed), at)
                val ketu = when (body) {
                    Body.RAHU_MEAN -> Body.KETU_MEAN
                    Body.RAHU_TRUE -> Body.KETU_TRUE
                    else -> null
                }
                if (ketu != null) {
                    val k = eph.geoApparent(ketu, tt)
                    val ks = stats.getValue(ketu)
                    ks.lon.add(abs(wrap180(k.lonDeg - (lon + 180.0))) * 3600.0, at)
                    ks.lat.add(abs(k.latDeg - lat) * 3600.0, at)
                    ks.speed.add(abs(k.speedDegPerDay - speed), at)
                }
            }
        }
        val lines = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for ((body, s) in stats) {
            val lim = angleLimitArcsec(backend, body)
            val sl = speedLimit(backend)
            lines += "${body.name}: n=${s.lon.count} maxLon\"=${s.lon.value} maxLat\"=${s.lat.value} " +
                "maxSpeed=${s.speed.value} limits(\"=$lim, speed=$sl)"
            if (s.lon.value > lim) failures += "$backend ${body.name} lon ${s.lon.value}\" > $lim\" at ${s.lon.where}"
            if (s.lat.value > lim) failures += "$backend ${body.name} lat ${s.lat.value}\" > $lim\" at ${s.lat.where}"
            if (s.speed.value > sl) failures += "$backend ${body.name} speed ${s.speed.value} > $sl at ${s.speed.where}"
            assertEquals(10_000, s.lon.count, "vectors checked for ${body.name}")
        }
        GoldenLoader.report("G2 positions $backend (10000 epochs x 9 bodies)", lines)
        assertTrue(failures.isEmpty(), "G2 failures: ${failures.take(10)}")
    }

    @Test
    fun precisionBackendMatchesOracle() = run(Backend.PRECISION, GoldenLoader.openDe440())

    @Test
    fun baseBackendMatchesOracle() = run(Backend.BASE, AstronomyEngineEphemeris())
}
