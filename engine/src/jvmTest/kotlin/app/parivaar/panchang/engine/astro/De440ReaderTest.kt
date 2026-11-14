package app.parivaar.panchang.engine.astro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Proves the Chebyshev reader against jplephem states (`golden/de440_states.json`, 200 points) at the G1
 * tolerance of docs/specs/M01.md section 4. Read-only on the fixture; the tagged G1 test arrives with B5.
 */
class De440ReaderTest {
    @Test
    fun readerMatchesJplephemStates() {
        val eph = GoldenLoader.openDe440()
        val root = Json.parseToJsonElement(GoldenLoader.resourceBytes("/golden/de440_states.json").decodeToString())
        val points = root.jsonObject.getValue("v").jsonArray
        assertEquals(200, points.size)
        val seen = HashSet<String>()
        var worstR = 0.0
        var worstV = 0.0
        for (p in points) {
            val o = p.jsonObject
            val segment = o.getValue("segment").jsonPrimitive.content
            seen.add(segment)
            val (center, target) = segment.split("->").map { it.toInt() }
            val jd = o.getValue("jdTdb").jsonPrimitive.double
            val r = o.getValue("r").jsonArray.map { it.jsonPrimitive.double }
            val v = o.getValue("v").jsonArray.map { it.jsonPrimitive.double }
            val (er, ev) = eph.segmentState(center, target, jd)
            val tolR = 1e-12 * norm(r) + 1e-6
            val tolV = 1e-12 * norm(v) + 1e-6
            val dr = norm(listOf(er.x - r[0], er.y - r[1], er.z - r[2]))
            val dv = norm(listOf(ev.x - v[0], ev.y - v[1], ev.z - v[2]))
            assertTrue(dr <= tolR, "segment $segment jd $jd: |dr| = $dr km > $tolR")
            assertTrue(dv <= tolV, "segment $segment jd $jd: |dv| = $dv km/day > $tolV")
            worstR = maxOf(worstR, dr / tolR)
            worstV = maxOf(worstV, dv / tolV)
        }
        assertEquals(setOf("0->1", "0->2", "0->3", "0->4", "0->5", "0->6", "0->10", "3->301"), seen)
        println("De440ReaderTest: worst |dr|/tol = $worstR, worst |dv|/tol = $worstV over ${points.size} states")
    }

    @Test
    fun headerAndRange() {
        val eph = GoldenLoader.openDe440()
        assertEquals(2415020.5, eph.validRange.start.value, 0.0)
        assertEquals(2488069.5, eph.validRange.endInclusive.value, 0.0)
        assertTrue(abs(eph.emrat - 81.3005682) < 1e-6)
        assertFailsWith<EphemerisRangeException> { eph.geoApparent(Body.SUN, JulianDay(2415020.4999)) }
        assertFailsWith<EphemerisRangeException> { eph.geoApparent(Body.MOON, JulianDay(2488069.5001)) }
        eph.geoApparent(Body.SATURN, JulianDay(2415020.5))
        eph.geoApparent(Body.SATURN, JulianDay(2488069.5))
    }

    @Test
    fun wrongHashIsRejected() {
        val bytes = GoldenLoader.resourceBytes("/ephem/pp-de440-1900-2100.bin")
        assertFailsWith<IllegalArgumentException> {
            De440Ephemeris.open(ByteArrayBinSource(bytes), "0".repeat(64))
        }
    }

    private fun norm(a: List<Double>) = sqrt(a.sumOf { it * it })
}
