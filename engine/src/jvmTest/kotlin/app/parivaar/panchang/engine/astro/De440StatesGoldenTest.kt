package app.parivaar.panchang.engine.astro

import org.junit.jupiter.api.Tag
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** G1: the Chebyshev reader against jplephem states (docs/specs/M01.md section 4). */
@Tag("golden")
class De440StatesGoldenTest {
    private fun norm(a: List<Double>) = sqrt(a.sumOf { it * it })

    private fun vec(a: JsonArray) = a.map { it.jsonPrimitive.double }

    @Test
    fun readerMatchesJplephemStates() {
        val root = GoldenLoader.json("/golden/de440_states.json")
        val points = root.getValue("v").jsonArray
        assertEquals(200, points.size, "truncated de440_states.json")
        val eph = GoldenLoader.openDe440()
        val maxR = MaxError()
        val maxV = MaxError()
        val failures = mutableListOf<String>()
        for (p in points) {
            val o = p.jsonObject
            val (center, target) = o.getValue("segment").jsonPrimitive.content.split("->").map { it.toInt() }
            val jd = o.getValue("jdTdb").jsonPrimitive.double
            val r = vec(o.getValue("r").jsonArray)
            val v = vec(o.getValue("v").jsonArray)
            val (er, ev) = eph.segmentState(center, target, jd)
            val dr = vec3Delta(listOf(er.x, er.y, er.z), r)
            val dv = vec3Delta(listOf(ev.x, ev.y, ev.z), v)
            val at = "$center->$target jdTdb=$jd"
            maxR.add(dr, at)
            maxV.add(dv, at)
            if (!(dr <= 1e-12 * norm(r) + 1e-6)) failures += "r $at error $dr km"
            if (!(dv <= 1e-12 * norm(v) + 1e-6)) failures += "v $at error $dv km/day"
        }
        GoldenLoader.report(
            "G1 de440 states (200 points)",
            listOf("max |dr| km = ${maxR.value} (${maxR.where})", "max |dv| km/day = ${maxV.value} (${maxV.where})"),
        )
        assertTrue(failures.isEmpty(), "G1 failures: ${failures.take(5)}")
    }

    private fun vec3Delta(a: List<Double>, b: List<Double>) =
        a.indices.maxOf { abs(a[it] - b[it]) }
}
