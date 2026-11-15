@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.planets

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU reference of the planet shader ([PLANET_AGSL]): the same per-pixel maths, used by the Canvas fallback
 * (below API 33 or when the shader cannot be compiled) and by the JVM tests. Keep the two in step.
 *
 * Pixel space: (px, py) in disc radii from the centre, y down. View space: n = (px, -py, sqrt(1 - r^2)), +z to
 * the viewer. Saturn's spin axis is the ring normal nR = (0, cos B, sin B) (line of nodes horizontal, D9).
 */
object PlanetShading {
    private const val EARTHSHINE = 0.03f
    private const val UNLIT_RING_FACE = 0.3f
    private const val RING_IN_SHADOW = 0.12f
    private const val EDGE_ON = 0.005f
    private val EARTHSHINE_TINT = floatArrayOf(0x9A / 255f, 0xA0 / 255f, 0xB8 / 255f)
    private val RING_COLOUR = floatArrayOf(0xD4 / 255f, 0xC2 / 255f, 0x9E / 255f)

    /**
     * Brightness of a disc point: mu = n.z (toward the viewer), mu0 = n . sun.
     * Lambert I = clamp((mu0 + soft) / (1 + soft)); Lommel-Seeliger I = 2 mu0 / (mu0 + mu) (mu0 > 0) times
     * smoothstep(-soft, soft, mu0), capped at 1; emissive I = 1 - 0.6 (1 - mu). Then linear limb darkening
     * I (1 - limb (1 - mu)) for the lit models.
     */
    fun discIntensity(model: LightModel, soft: Float, limb: Float, mu: Float, mu0: Float): Float = when (model) {
        LightModel.EMISSIVE -> 1f - 0.6f * (1f - mu)
        LightModel.LAMBERT -> ((mu0 + soft) / (1f + soft)).coerceIn(0f, 1f) * (1f - limb * (1f - mu))
        LightModel.LOMMEL_SEELIGER -> {
            val ls = if (mu0 > 0f) min(1f, 2f * mu0 / (mu0 + mu)) else 0f
            ls * smoothstep(-soft, soft, mu0) * (1f - limb * (1f - mu))
        }
    }

    /** Earthshine on the Moon's night side: 0.03 (1 - smoothstep(-0.02, 0.02, mu0)). */
    fun earthshine(mu0: Float): Float = EARTHSHINE * (1f - smoothstep(-0.02f, 0.02f, mu0))

    /** Ring opacity at [rho] Saturn radii: C 1.239-1.527 0.15, B -1.951 0.85, Cassini -2.027 0.10, A -2.269 0.60. */
    fun ringAlpha(rho: Float): Float {
        val a = 0.15f * band(rho, 1.239f, 1.527f) + 0.85f * band(rho, 1.527f, 1.951f) +
            0.10f * band(rho, 1.951f, 2.027f) + 0.60f * band(rho, 2.027f, 2.269f)
        return a * (0.88f + 0.12f * sin(rho * 140f))
    }

    /**
     * Factor of the ring's shadow on a disc point [n]: the ray toward the Sun meets the ring plane at
     * t = -(n . nR) / (sun . nR) > 0; inside a band the light is cut by its opacity.
     */
    fun ringShadowOnDisc(n: FloatArray, sun: FloatArray, sinB: Float, cosB: Float): Float {
        val sn = sun[1] * cosB + sun[2] * sinB
        val t = if (abs(sinB) < EDGE_ON || abs(sn) < 1e-4f) 0f else -(n[1] * cosB + n[2] * sinB) / sn
        if (t <= 0f) return 1f
        val hx = n[0] + t * sun[0]
        val hy = n[1] + t * sun[1]
        val hz = n[2] + t * sun[2]
        return 1f - ringAlpha(sqrt(hx * hx + hy * hy + hz * hz))
    }

    /**
     * Ring opacity seen at pixel ([px], [py]) (radii, y down) for a tilt with |sin B| >= 0.005: the plane hit
     * z = -y cos B / sin B (y up), zero where the disc is in front of it (the far half behind the planet).
     */
    fun visibleRingAlpha(px: Float, py: Float, sinB: Float, cosB: Float): Float {
        val ps = -py
        val z = -ps * cosB / sinB
        val r2 = px * px + py * py
        if (r2 < 1f && z < sqrt(1f - r2)) return 0f
        return ringAlpha(sqrt(px * px + ps * ps + z * z))
    }

    /** True when the ring point [h] (radii) lies in the planet's shadow: its ray toward the Sun hits the sphere. */
    fun planetShadowOnRing(h: FloatArray, sun: FloatArray): Boolean {
        val b = h[0] * sun[0] + h[1] * sun[1] + h[2] * sun[2]
        val c = h[0] * h[0] + h[1] * h[1] + h[2] * h[2] - 1f
        return b < 0f && b * b - c > 0f
    }

    /**
     * Premultiplied RGBA of one pixel into [out]. [px], [py] in radii (y down); the [Scene] carries the radius in
     * px (edge antialiasing over 1.5 px) and the spin angle in radians.
     */
    internal fun shade(s: Scene, px: Float, py: Float, out: FloatArray) {
        val r2 = px * px + py * py
        out.fill(0f)
        if (r2 < 1f) disc(s, px, py, out)
        if (s.ringOn) ring(s, px, py, out)
    }

    /** Per-frame constants: unit sun, ring tilt sine/cosine, spin, radius in px. */
    internal class Scene(val style: PlanetStyle, light: PlanetLight, val rot: Float, val rPx: Float) {
        val sun = light.unitSun()
        val ringOn = light.ringTiltDeg != null
        private val b = (light.ringTiltDeg ?: 0f) * (Math.PI / 180.0).toFloat()
        val sinB = sin(b)
        val cosB = cos(b)
    }

    private fun disc(s: Scene, px: Float, py: Float, out: FloatArray) {
        val style = s.style
        val sun = s.sun
        val sinB = s.sinB
        val cosB = s.cosB
        val rot = s.rot
        val r2 = px * px + py * py
        val n = floatArrayOf(px, -py, sqrt(1f - r2))
        // Body frame: spin axis = ring normal (identity without a tilt), then the spin about that axis.
        val my = n[1] * cosB + n[2] * sinB
        val mz = -n[1] * sinB + n[2] * cosB
        val qx = n[0] * cos(rot) + mz * sin(rot)
        val qz = mz * cos(rot) - n[0] * sin(rot)
        val c = surfaceColour(style, qx, my, qz)
        val mu = n[2]
        val mu0 = n[0] * sun[0] + n[1] * sun[1] + n[2] * sun[2]
        var i = discIntensity(style.model, style.soft, style.limb, mu, mu0)
        if (s.ringOn) i *= ringShadowOnDisc(n, sun, sinB, cosB)
        val es = style.earthshine * earthshine(mu0) / 0.6f
        val a = 1f - smoothstep(1f - 1.5f / s.rPx, 1f, sqrt(r2))
        for (k in 0..2) out[k] = min(1f, c[k] * (i + es * EARTHSHINE_TINT[k])) * a
        out[3] = a
    }

    private fun ring(s: Scene, px: Float, py: Float, out: FloatArray) {
        val sun = s.sun
        val sinB = s.sinB
        val cosB = s.cosB
        val ps = -py
        val ra: Float
        val light: Float
        if (abs(sinB) < EDGE_ON) {
            // Edge-on: a line through the centre, drawn over the disc.
            ra = ringAlpha(abs(px)) * (1f - smoothstep(0f, 1.5f / s.rPx, abs(ps)))
            light = 0.5f
        } else {
            val z = -ps * cosB / sinB
            val h = floatArrayOf(px, ps, z)
            ra = visibleRingAlpha(px, py, sinB, cosB)
            if (ra <= 0f) return
            val sn = sun[1] * cosB + sun[2] * sinB
            val face = if (sn * sinB > 0f) 1f else UNLIT_RING_FACE
            val shadow = if (planetShadowOnRing(h, sun)) RING_IN_SHADOW else 1f
            light = face * shadow * (0.45f + 0.55f * sqrt(abs(sn)))
        }
        if (ra <= 0f) return
        for (k in 0..2) out[k] = RING_COLOUR[k] * light * ra + out[k] * (1f - ra)
        out[3] = ra + out[3] * (1f - ra)
    }

    /**
     * A static frame of [sizePx] x [sizePx] as non-premultiplied ARGB (for `Bitmap.setPixels`), centre at the
     * middle, radius [planetRadiusPx].
     */
    fun render(body: PlanetBody, light: PlanetLight, sizePx: Int, rot: Float = 0f): IntArray {
        val r = planetRadiusPx(body, sizePx.toFloat())
        val scene = Scene(planetStyle(body), light, rot, r)
        val half = sizePx / 2f
        val out = IntArray(sizePx * sizePx)
        val px = FloatArray(4)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                shade(scene, (x + 0.5f - half) / r, (y + 0.5f - half) / r, px)
                out[y * sizePx + x] = argb(px)
            }
        }
        return out
    }
}

/** Unpremultiplies and packs a premultiplied RGBA. */
private fun argb(p: FloatArray): Int {
    val a = p[3]
    if (a <= 0f) return 0
    fun ch(v: Float) = (min(1f, v / a) * 255f + 0.5f).toInt()
    return ((a * 255f + 0.5f).toInt() shl 24) or (ch(p[0]) shl 16) or (ch(p[1]) shl 8) or ch(p[2])
}

/** Procedural surface (placeholder until the owner textures): fbm noise, latitude bands for the giants. */
private fun surfaceColour(style: PlanetStyle, qx: Float, qy: Float, qz: Float): FloatArray {
    val nz = fbm(qx * 3f + 7.31f, qy * 3f + 7.31f, qz * 3f + 7.31f)
    var t = (nz * style.spots + 0.5f * (1f - style.spots)).coerceIn(0f, 1f)
    if (style.bands > 0f) {
        val lat = asin(qy.coerceIn(-1f, 1f))
        val b = sin(lat * style.bands + nz * 2.5f * style.spots)
        t = (0.5f + 0.4f * b + (nz - 0.47f) * 0.6f).coerceIn(0f, 1f)
    }
    val lo = smoothstep(0f, 0.6f, t)
    val hi = smoothstep(0.6f, 1f, t)
    return FloatArray(3) { k ->
        val a = channel(style.colA, k)
        val m = a + (channel(style.colB, k) - a) * lo
        m + (channel(style.colC, k) - m) * hi
    }
}

private fun channel(argb: Long, k: Int): Float = ((argb shr (16 - 8 * k)) and 0xFF).toFloat() / 255f

private fun fract(v: Float): Float = v - floor(v)

private fun hash(x0: Float, y0: Float, z0: Float): Float {
    val x = fract(x0 * 0.3183099f + 0.1f) * 17f
    val y = fract(y0 * 0.3183099f + 0.1f) * 17f
    val z = fract(z0 * 0.3183099f + 0.1f) * 17f
    return fract(x * y * z * (x + y + z))
}

private fun vnoise(x: Float, y: Float, z: Float): Float {
    val ix = floor(x)
    val iy = floor(y)
    val iz = floor(z)
    val fx = fade(x - ix)
    val fy = fade(y - iy)
    val fz = fade(z - iz)
    fun h(a: Float, b: Float, c: Float) = hash(ix + a, iy + b, iz + c)
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    val z0 = lerp(lerp(h(0f, 0f, 0f), h(1f, 0f, 0f), fx), lerp(h(0f, 1f, 0f), h(1f, 1f, 0f), fx), fy)
    val z1 = lerp(lerp(h(0f, 0f, 1f), h(1f, 0f, 1f), fx), lerp(h(0f, 1f, 1f), h(1f, 1f, 1f), fx), fy)
    return lerp(z0, z1, fz)
}

private fun fade(f: Float): Float = f * f * (3f - 2f * f)

private fun fbm(x0: Float, y0: Float, z0: Float): Float {
    var x = x0
    var y = y0
    var z = z0
    var a = 0.5f
    var s = 0f
    repeat(4) {
        s += a * vnoise(x, y, z)
        x = x * 2.03f + 1.7f
        y = y * 2.03f + 1.7f
        z = z * 2.03f + 1.7f
        a *= 0.5f
    }
    return s
}

/** GLSL smoothstep (edge0 < edge1). */
internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun band(r: Float, r0: Float, r1: Float): Float = if (r >= r0 && r < r1) 1f else 0f
