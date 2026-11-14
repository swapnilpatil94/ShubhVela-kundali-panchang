package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.SunriseConvention
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** G3: both backends x 4,000 city-days (24,000 entries) against the oracle (docs/specs/M01.md section 4). */
@Tag("golden")
class RiseSetGoldenTest {
    private class Stat {
        val dt = MaxError() // non-fragile event time error, seconds
        val residual = MaxError() // fragile residual, arcseconds
        var entries = 0
        var fragile = 0
    }

    /** One backend's pass over all entries. [dtLimit] in seconds, [residualLimit] in arcseconds. */
    private class Pass(
        val name: String,
        val eph: Ephemeris,
        val dtLimit: Double,
        val residualLimit: Double,
    ) {
        val riseSet = RiseSet(eph)
        val stats = linkedMapOf<String, Stat>()
        val failures = mutableListOf<String>()
        var entries = 0
        var events = 0
        var fragile = 0
    }

    private fun run(pass: Pass) {
        val root = GoldenLoader.json("/golden/riseset.json")
        val meta = root.getValue("meta").jsonObject
        assertEquals(24_000, meta.getValue("entries").jsonPrimitive.int, "meta.entries")
        val cities = meta.getValue("cities").jsonArray.associate {
            val o = it.jsonObject
            o.getValue("name").jsonPrimitive.content to Place(
                o.getValue("lat").jsonPrimitive.double,
                o.getValue("lon").jsonPrimitive.double,
                o.getValue("elevationM").jsonPrimitive.double,
            )
        }
        val rows = root.getValue("d").jsonArray
        assertEquals(4_000, rows.size, "truncated riseset.json (city-days)")
        for (row in rows) {
            val a = row.jsonArray
            val city = a[0].jsonPrimitive.content
            val ms = a[1].jsonPrimitive.long
            for ((bodyName, convs) in a[2].jsonObject) {
                for ((convName, entry) in convs.jsonObject) {
                    checkEntry(pass, cities.getValue(city), "$city $ms $bodyName $convName", ms, entry.jsonObject)
                }
            }
        }
        assertEquals(24_000, pass.entries, "entries iterated")
        assertEquals(meta.getValue("events").jsonPrimitive.int, pass.events, "events iterated vs meta.events")
        assertEquals(meta.getValue("fragileEntries").jsonPrimitive.int, pass.fragile, "fragile entries vs meta")
        val lines = pass.stats.map { (k, s) ->
            "$k: entries=${s.entries} fragile=${s.fragile} maxDt_s=${s.dt.value} (${s.dt.where}) " +
                "maxFragileResidual_arcsec=${s.residual.value} (${s.residual.where})"
        } + "limits: dt ${pass.dtLimit} s, fragile residual ${pass.residualLimit} arcsec"
        GoldenLoader.report("G3 rise/set ${pass.name} (4000 city-days, 24000 entries)", lines)
        assertTrue(pass.failures.isEmpty(), "G3 ${pass.failures.size} failures: ${pass.failures.take(10)}")
    }

    /** [at] = "city windowStartMs body convention"; the body is the third word. */
    private fun checkEntry(pass: Pass, place: Place, at: String, startMs: Long, entry: JsonObject) {
        val parts = at.split(' ')
        val body = if (parts[2] == "sun") Body.SUN else Body.MOON
        val settings = CalcSettings(sunrise = SunriseConvention.valueOf(parts[3]))
        val start = Instant.fromEpochMilliseconds(startMs)
        val stat = pass.stats.getOrPut("${parts[2]}/${parts[3]}") { Stat() }
        val events = entry["events"]?.jsonArray ?: JsonArray(emptyList())
        stat.entries++
        pass.entries++
        pass.events += events.size
        if (entry["fragile"]?.jsonPrimitive?.boolean == true) {
            stat.fragile++
            pass.fragile++
            fragileResidual(pass, Triple(body, place, settings), start, entry, stat, at)
        } else {
            checkCrossings(pass, pass.riseSet.find(body, place, start, settings), entry, stat, at)
            if (entry["events"] == null) noEventGrid(pass, Triple(body, place, settings), start, entry, at)
        }
    }

    /**
     * Non-fragile entry without events: the engine's f must be finite and on the oracle's side at every
     * 10-minute sample ("above": f >= 0; "below": f < 0). Without this a NaN altitude would yield
     * `NoEvent(alwaysAbove = false)` and pass a "below" entry unchecked.
     */
    private fun noEventGrid(
        pass: Pass,
        target: Triple<Body, Place, CalcSettings>,
        start: Instant,
        entry: JsonObject,
        at: String,
    ) {
        val (body, place, settings) = target
        val above = entry.getValue("noEvent").jsonPrimitive.content == "above"
        for (k in 0..GRID) {
            val f = pass.riseSet.crossingDeg(body, place, start + (10 * k).minutes, settings)
            val sameSide = if (above) f >= 0.0 else f < 0.0 // false for NaN
            if (!f.isFinite() || !sameSide) {
                pass.failures += "${pass.name} $at sample $k f=$f deg contradicts noEvent(above=$above)"
                return
            }
        }
    }

    private fun checkCrossings(pass: Pass, result: RiseSetResult, entry: JsonObject, stat: Stat, at: String) {
        val events = entry["events"]?.jsonArray
        if (events == null) {
            val above = entry.getValue("noEvent").jsonPrimitive.content == "above"
            if (result !is RiseSetResult.NoEvent || result.alwaysAbove != above) {
                pass.failures += "${pass.name} $at expected noEvent(above=$above) got $result"
            }
            return
        }
        val got = (result as? RiseSetResult.Events)?.list
        if (got == null || got.size != events.size) {
            pass.failures += "${pass.name} $at expected ${events.size} events got ${got?.size ?: result}"
            return
        }
        for ((i, ev) in events.withIndex()) {
            val x = ev.jsonArray
            val kind = RiseSetKind.valueOf(x[0].jsonPrimitive.content)
            if (got[i].kind != kind) {
                pass.failures += "${pass.name} $at event $i kind ${got[i].kind} expected $kind"
            }
            val oracle = Instant.fromEpochMilliseconds(x[1].jsonPrimitive.long)
            val dt = abs((got[i].at - oracle).inWholeMicroseconds) / MICROS
            stat.dt.add(dt, "$at event $i")
            if (!(dt <= pass.dtLimit)) pass.failures += "${pass.name} $at event $i |dt| $dt s > ${pass.dtLimit} s"
        }
    }

    /** Fragile entries: residual f_engine(t_oracle) at each oracle event; counts are not compared. */
    private fun fragileResidual(
        pass: Pass,
        target: Triple<Body, Place, CalcSettings>,
        start: Instant,
        entry: JsonObject,
        stat: Stat,
        at: String,
    ) {
        val (body, place, settings) = target
        val events = entry["events"]?.jsonArray
        if (events != null) {
            for ((i, ev) in events.withIndex()) {
                val t = Instant.fromEpochMilliseconds(ev.jsonArray[1].jsonPrimitive.long)
                val r = abs(pass.riseSet.crossingDeg(body, place, t, settings)) * ARCSEC
                stat.residual.add(r, "$at event $i")
                if (!(r <= pass.residualLimit)) { // also true for NaN
                    pass.failures += "fragile ${pass.name} $at event $i residual $r arcsec > ${pass.residualLimit}"
                }
            }
            return
        }
        // No oracle crossing: on the 10-minute grid the engine's f must not violate the stated side by more than
        // the limit ("above": f >= -limit everywhere; "below": f <= +limit everywhere).
        val above = entry.getValue("noEvent").jsonPrimitive.content == "above"
        for (k in 0..GRID) {
            val f = pass.riseSet.crossingDeg(body, place, start + (10 * k).minutes, settings) * ARCSEC
            val violation = if (above) -f else f
            stat.residual.add(maxOf(violation, 0.0), "$at sample $k")
            if (!(violation <= pass.residualLimit)) { // also true for NaN
                pass.failures += "fragile ${pass.name} $at sample $k f=$f arcsec violates noEvent"
            }
        }
    }

    @Test
    fun precisionBackendMatchesOracle() = run(Pass("PRECISION", GoldenLoader.openDe440(), 5.0, 2.0))

    @Test
    fun baseBackendMatchesOracle() = run(Pass("BASE", AstronomyEngineEphemeris(), 10.0, 25.0))

    private companion object {
        const val MICROS = 1e6
        const val ARCSEC = 3600.0
        const val GRID = 144
    }
}
