@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.planets

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural equirectangular surface maps (512 x 256), a port of `makeTex` in design/shubhvela-screens.html
 * (hash / vn / fbm / ramp and the per-body recipes: lunar maria and craters, Mercury, Jupiter's belts and Great Red
 * Spot, Saturn's bands and polar tint, Mars with dark regions and polar caps, Venus clouds, solar granulation).
 * Deterministic (integer hash, fixed seeds; no randomness from the platform). Texel (i, j) covers longitude
 * (i + 0.5) / 512 * 2 pi and latitude (0.5 - (j + 0.5) / 256) * pi; x = cos(lat) sin(lon), y = sin(lat),
 * z = cos(lat) cos(lon). Values are stored as float32 like the HTML's Float32Array and rounded to bytes for the
 * ARGB map shared by the shader and the CPU renderer (PlanetTexturesTest checks them against the HTML's own output).
 * Generation takes about 0.1 s per body on a desktop JVM; call it off the main thread.
 */
object PlanetTextures {
    const val WIDTH = 512
    const val HEIGHT = 256

    private val cache = HashMap<PlanetBody, IntArray>()

    /** Opaque ARGB map of [body], generated once and cached (thread-safe). Do not modify the array. */
    fun argb(body: PlanetBody): IntArray {
        synchronized(cache) { cache[body]?.let { return it } }
        val rgb = rgb(body)
        val out = IntArray(WIDTH * HEIGHT) { k ->
            (0xFF shl 24) or (toByte(rgb[k * 3]) shl 16) or (toByte(rgb[k * 3 + 1]) shl 8) or toByte(rgb[k * 3 + 2])
        }
        synchronized(cache) { return cache.getOrPut(body) { out } }
    }

    /** Float RGB (0..255; may exceed 255 like the HTML before clamping), WIDTH x HEIGHT x 3, rows from the north. */
    fun rgb(body: PlanetBody): FloatArray {
        val d = FloatArray(WIDTH * HEIGHT * 3)
        val recipe = recipeFor(body)
        for (j in 0 until HEIGHT) {
            val lat = (0.5 - (j + 0.5) / HEIGHT) * PI
            for (i in 0 until WIDTH) {
                val lon = (i + 0.5) / WIDTH * 2.0 * PI
                val c = recipe.colour(cos(lat) * sin(lon), sin(lat), cos(lat) * cos(lon), lat, lon)
                val k = (j * WIDTH + i) * 3
                d[k] = c[0].toFloat()
                d[k + 1] = c[1].toFloat()
                d[k + 2] = c[2].toFloat()
            }
        }
        return d
    }

    /** Rounds a channel to 0..255 (half up). */
    fun toByte(v: Float): Int = floor(v.toDouble() + 0.5).toInt().coerceIn(0, 255)

    private fun recipeFor(body: PlanetBody): Recipe = when (body) {
        PlanetBody.MOON -> Rocky(moon = true)
        PlanetBody.MERCURY -> Rocky(moon = false)
        PlanetBody.JUPITER -> Giant(jupiter = true)
        PlanetBody.SATURN -> Giant(jupiter = false)
        PlanetBody.MARS -> Recipe { x, y, z, lat, _ ->
            val n = fbm(x * 3, y * 3, z * 3, 41, 5)
            val a = fbm(x * 1.6, y * 1.6, z * 1.6, 43, 4)
            var c = mix(MARS_A, MARS_B, n)
            c = mix(c, MARS_DARK, ss(0.55, 0.66, a) * 0.75)
            if (abs(lat) > 1.25) c = mix(c, MARS_CAP, ss(1.25, 1.38, abs(lat)))
            c
        }
        PlanetBody.VENUS -> Recipe { x, y, z, _, _ ->
            mix(VENUS_A, VENUS_B, fbm(x * 2 + fbm(x * 3, y * 3, z * 3, 7, 3) * 1.5, y * 6, z * 2, 51, 4))
        }
        PlanetBody.SUN -> Recipe { x, y, z, _, _ ->
            val t = fbm(x * 30, y * 30, z * 30, 61, 3) * 0.65 + fbm(x * 6, y * 6, z * 6, 62, 3) * 0.35
            if (t < 0.5) mix(SUN_A, SUN_B, t * 2) else mix(SUN_B, SUN_C, (t - 0.5) * 2)
        }
    }

    private fun interface Recipe {
        fun colour(x: Double, y: Double, z: Double, lat: Double, lon: Double): DoubleArray
    }

    /** Moon (140 craters, maria) and Mercury (220 craters); seed rng(type.length * 7 + 3) as the HTML. */
    private class Rocky(private val moon: Boolean) : Recipe {
        private val craters: List<DoubleArray> = run {
            val r = Rng((if (moon) "moon" else "mercury").length * 7 + 3)
            List(if (moon) 140 else 220) {
                val u = r.next() * 2 - 1
                val th = r.next() * 6.283
                val q = sqrt(1 - u * u)
                doubleArrayOf(q * cos(th), u, q * sin(th), r.next().pow(2.2) * 0.16 + 0.012)
            }
        }

        override fun colour(x: Double, y: Double, z: Double, lat: Double, lon: Double): DoubleArray {
            var b = if (moon) 0.66 else 0.58
            b += (fbm(x * 4, y * 4, z * 4, 11, 5) - 0.5) * 0.34
            b += (fbm(x * 22, y * 22, z * 22, 5, 3) - 0.5) * 0.14
            if (moon) b *= 1 - 0.42 * ss(0.52, 0.62, fbm(x * 1.2 + 2, y * 1.2, z * 1.2, 77, 4))
            for (cr in craters) {
                val dot = x * cr[0] + y * cr[1] + z * cr[2]
                val ang = if (dot < 0.95) 2.0 else acos(min(1.0, dot)) / cr[3]
                b *= when {
                    ang >= 1.25 -> 1.0
                    ang < 0.85 -> 0.84 + 0.1 * ang
                    ang < 1.05 -> 1.16
                    else -> 1.04
                }
            }
            return if (moon) doubleArrayOf(b * 228, b * 224, b * 214) else doubleArrayOf(b * 205, b * 190, b * 172)
        }
    }

    /** Jupiter (19 belt frequency, Great Red Spot) and Saturn (14, polar tint). */
    private class Giant(private val jupiter: Boolean) : Recipe {
        override fun colour(x: Double, y: Double, z: Double, lat: Double, lon: Double): DoubleArray {
            val w = fbm(x * 3, y * 3, z * 3, 19, 4)
            val tw = fbm(x * 9, y * 9, z * 9, 23, 3)
            val v = lat / 1.5708 + (w - 0.5) * 0.12 + (tw - 0.5) * 0.035
            val t = (sin(v * (if (jupiter) 19 else 14)) * 0.5 + 0.5) * 0.6 + (sin(v * 7.3 + 1) * 0.5 + 0.5) * 0.4
            var c = ramp(if (jupiter) JUP else SAT, t)
            if (jupiter) {
                val dl = atan2(sin(lon - 0.9), cos(lon - 0.9))
                val dla = lat + 0.38
                val e = (dl / 0.26).pow(2) + (dla / 0.085).pow(2)
                if (e < 1.4) c = mix(c, mix(GRS_A, GRS_B, tw), ss(1.4, 0.6, e) * 0.85)
            }
            if (!jupiter && lat > 1.15) c = mix(c, SAT_POLE, ss(1.15, 1.4, lat) * 0.5)
            val g = 0.92 + 0.16 * fbm(x * 30, y * 30, z * 30, 3, 2)
            return doubleArrayOf(c[0] * g, c[1] * g, c[2] * g)
        }
    }

    /** The HTML's `rng`: s = s * 9301 + 49297, then s = (s * 9301 + 49297) mod 233280 per draw. */
    private class Rng(seed: Int) {
        private var s = seed * 9301L + 49297L

        fun next(): Double {
            s = (s * 9301L + 49297L) % 233280L
            return s / 233280.0
        }
    }

    private val JUP = listOf(0xF1E7D6, 0xD9BE98, 0xB98258, 0x8A5636, 0xE8D7BC, 0xC79A6E, 0xF4EDE2).map(::hex)
    private val SAT = listOf(0xEFE1BC, 0xDCC58F, 0xC7A86A, 0xE6D3A0, 0xD2B479, 0xF0E4C4).map(::hex)
    private val GRS_A = hex(0xC0563A)
    private val GRS_B = hex(0xE39A72)
    private val SAT_POLE = hex(0x9FA3A8)
    private val MARS_A = hex(0xC8693A)
    private val MARS_B = hex(0xE0915A)
    private val MARS_DARK = hex(0x6B3420)
    private val MARS_CAP = hex(0xF2EEE8)
    private val VENUS_A = hex(0xD9B87A)
    private val VENUS_B = hex(0xF6E7C2)
    private val SUN_A = hex(0xE04E0C)
    private val SUN_B = hex(0xFF9A2E)
    private val SUN_C = hex(0xFFE7A0)
}

private fun hex(rgb: Int) =
    doubleArrayOf(((rgb shr 16) and 0xFF).toDouble(), ((rgb shr 8) and 0xFF).toDouble(), (rgb and 0xFF).toDouble())

private fun mix(a: DoubleArray, b: DoubleArray, t: Double) =
    doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)

private fun ss(a: Double, b: Double, x: Double): Double {
    val t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)
}

private fun ramp(stops: List<DoubleArray>, t0: Double): DoubleArray {
    val t = max(0.0, min(0.9999, t0))
    val n = stops.size - 1
    val i = floor(t * n).toInt()
    return mix(stops[i], stops[i + 1], t * n - i)
}

/** The HTML's integer hash (Math.imul and unsigned shifts) to [0, 1). 2246822519 wraps to -2048144777 as an int32. */
internal fun hash(x: Int, y: Int, z: Int, s: Int): Double {
    var h = (x * 374761393) xor (y * 668265263) xor (z * 1274126177) xor (s * -2048144777)
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h.toLong() and 0xFFFFFFFFL) / 4294967296.0
}

/** Value noise, trilinear with smoothstep weights. */
internal fun vn(x: Double, y: Double, z: Double, s: Int): Double {
    val xi = floor(x)
    val yi = floor(y)
    val zi = floor(z)
    val xf = x - xi
    val yf = y - yi
    val zf = z - zi
    val u = xf * xf * (3 - 2 * xf)
    val v = yf * yf * (3 - 2 * yf)
    val w = zf * zf * (3 - 2 * zf)
    val ix = xi.toInt()
    val iy = yi.toInt()
    val iz = zi.toInt()
    fun h(i: Int, j: Int, k: Int) = hash(ix + i, iy + j, iz + k, s)
    fun l(a: Double, b: Double, t: Double) = a + (b - a) * t
    return l(
        l(l(h(0, 0, 0), h(1, 0, 0), u), l(h(0, 1, 0), h(1, 1, 0), u), v),
        l(l(h(0, 0, 1), h(1, 0, 1), u), l(h(0, 1, 1), h(1, 1, 1), u), v),
        w,
    )
}

/** Normalised fractal sum of [o] octaves (lacunarity 2.03, gain 0.5, x offset 31.7 per octave, seed s + i). */
internal fun fbm(x: Double, y: Double, z: Double, s: Int, o: Int): Double {
    var a = 0.5
    var f = 1.0
    var t = 0.0
    var n = 0.0
    for (i in 0 until o) {
        t += a * vn(x * f + 31.7 * i, y * f, z * f, s + i)
        n += a
        f *= 2.03
        a *= 0.5
    }
    return t / n
}
