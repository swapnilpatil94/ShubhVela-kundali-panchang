package app.parivaar.panchang.core.designsystem.planets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The texture port against the design's own JavaScript: sums and sample texels printed by
 * `node core/designsystem/src/test/resources/planets/texref.js design/shubhvela-screens.html` (V8), byte-rounded.
 */
class PlanetTexturesTest {
    /** Channel sum and the sample texels as "r,g,b r,g,b ...". */
    private class Ref(val sum: Long, packed: String) {
        val texels: List<List<Int>> = packed.split(' ').map { t -> t.split(',').map(String::toInt) }
    }

    private val samples = listOf(0, 777, 32768 + 100, 65536 + 256, 70000, 100001, 131071)

    private val refs = mapOf(
        PlanetBody.MOON to Ref(
            49471148,
            "86,84,80 89,88,84 88,87,83 153,151,144 150,148,141 159,157,150 177,174,166",
        ),
        PlanetBody.MERCURY to Ref(
            43565161,
            "121,112,102 122,113,103 139,129,117 121,112,102 119,110,100 132,122,111 121,112,101",
        ),
        PlanetBody.VENUS to Ref(
            78722024,
            "233,209,161 233,210,161 236,214,168 229,203,152 231,206,156 237,216,172 237,217,172",
        ),
        PlanetBody.MARS to Ref(
            54704990,
            "242,238,232 242,238,232 208,119,70 159,87,52 133,70,43 175,100,60 242,238,232",
        ),
        PlanetBody.JUPITER to Ref(
            63045128,
            "209,183,150 222,199,169 175,120,80 200,157,114 192,143,102 162,116,84 215,187,157",
        ),
        PlanetBody.SATURN to Ref(
            72402950,
            "189,184,170 196,191,176 208,179,119 209,180,121 206,177,118 228,205,151 223,196,137",
        ),
        PlanetBody.SUN to Ref(
            60012811,
            "255,165,63 255,167,66 249,140,40 246,131,36 255,166,64 252,146,42 253,149,44",
        ),
    )

    @Test
    fun hashMatchesTheHtmlInt32Arithmetic() {
        // Math.imul wraps; 2246822519 is -2048144777 as an int32; >>> is ushr; result in [0, 1).
        for (x in -3..3) for (s in listOf(0, 5, 77)) {
            val h = hash(x, 2 * x, -x, s)
            assertTrue(h >= 0.0 && h < 1.0)
        }
        assertEquals(hash(1, 2, 3, 4), hash(1, 2, 3, 4))
        val f = fbm(0.3, -1.2, 2.5, 11, 5)
        assertTrue(f in 0.0..1.0)
    }

    @Test
    fun texelsMatchTheDesignJavaScript() {
        for ((body, ref) in refs) {
            val t0 = System.nanoTime()
            val rgb = PlanetTextures.rgb(body)
            val ms = (System.nanoTime() - t0) / 1_000_000
            var sum = 0L
            for (v in rgb) sum += PlanetTextures.toByte(v)
            val got = samples.map { i -> (0..2).map { PlanetTextures.toByte(rgb[i * 3 + it]) } }
            println("texture $body ${ms}ms sum $sum")
            assertEquals(ref.texels, got, "$body sample texels")
            assertEquals(ref.sum, sum, "$body channel sum")
        }
    }

    @Test
    fun argbIsCachedAndOpaque() {
        val a = PlanetTextures.argb(PlanetBody.MARS)
        assertSame(a, PlanetTextures.argb(PlanetBody.MARS))
        assertEquals(PlanetTextures.WIDTH * PlanetTextures.HEIGHT, a.size)
        assertTrue(a.all { it ushr 24 == 0xFF })
        assertEquals(0xF2, (a[0] shr 16) and 0xFF) // north polar cap
    }
}
