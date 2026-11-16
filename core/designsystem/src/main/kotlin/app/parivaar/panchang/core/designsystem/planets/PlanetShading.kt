@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.planets

import kotlin.math.PI
import kotlin.math.abs
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
 * CPU reference of the planet shader ([PLANET_AGSL]), a port of `setupPlanet` / `renderBody` in
 * design/shubhvela-screens.html with the engine's lighting instead of the HTML's fixed light: used by the Canvas
 * fallback (below API 33 or when the shader cannot be compiled) and by the JVM tests. Keep the two in step.
 *
 * Pixel space: (px, py) in disc radii from the centre, y down. View space: n = (px, -py, sqrt(1 - r^2)), +z to the
 * viewer. Spin axis (body y) N = (-sin g cos b, cos g cos b, sin b) for the axis tilt b and roll g of [PlanetStyle];
 * for Saturn b = the ring tilt B and g = 0, so N is also the ring normal (line of nodes horizontal, D9).
 * Colours are 0..255 (the design's units) until the final premultiplied 0..1 output.
 */
@Suppress("TooManyFunctions")
object PlanetShading {
    private const val RING_IN_SHADOW = 0.12f
    private const val RING_SHADOW_ON_DISC = 0.75f
    private const val RING_GAIN = 0.86f
    private val AMBIENT = floatArrayOf(5f, 7f, 11f)
    private val RIM = floatArrayOf(255f * 0.9f, 235f * 0.85f, 200f * 0.7f)

    /**
     * Brightness of a disc point: mu = n.z (toward the viewer), mu0 = n . sun.
     * Lambert (design): clamp((mu0 + soft) / (1 + soft))^0.85 x (1 - limb (1 - mu^0.35)); Lommel-Seeliger (design's
     * lunar terminator): 2 mu0 / (mu0 + mu) (mu0 > 0) x smoothstep(-soft, soft, mu0), then x (1 - limb (1 - mu));
     * emissive (spec): 1 - 0.6 (1 - mu). The lunar exposure 0.62 is applied by the colour stage, not here.
     */
    fun discIntensity(model: LightModel, soft: Float, limb: Float, mu: Float, mu0: Float): Float = when (model) {
        LightModel.EMISSIVE -> 1f - 0.6f * (1f - mu)
        LightModel.LAMBERT -> {
            val wrapped = ((mu0 + soft) / (1f + soft)).coerceIn(0f, 1f)
            wrapped.pow(0.85f) * (1f - limb * (1f - mu.coerceAtLeast(0f).pow(0.35f)))
        }
        LightModel.LOMMEL_SEELIGER -> {
            val ls = if (mu0 > 0f) 2f * mu0 / (mu0 + mu + 1e-4f) else 0f
            ls * smoothstep(-soft, soft, mu0) * (1f - limb * (1f - mu))
        }
    }

    /** Ring opacity at [rho] Saturn radii (design `ringCol`): C 0.16, B 0.8, Cassini 0.06, A 0.55, Encke gap. */
    fun ringAlpha(rho: Float): Float {
        var a = when {
            rho > 1.24f && rho < 1.53f -> 0.16f
            rho >= 1.53f && rho < 1.95f -> 0.8f
            rho >= 1.95f && rho < 2.02f -> 0.06f
            rho >= 2.02f && rho < 2.27f -> 0.55f
            else -> 0f
        }
        if (rho > 2.205f && rho < 2.215f) a = 0.05f
        return a * (0.82f + 0.18f * sin(rho * 230f) * sin(rho * 61f))
    }

    /** Ring colour (0..255) and opacity at [rho]: [r, g, b, alpha]. */
    fun ring(rho: Float): FloatArray = FloatArray(4).also { ringInto(rho, it) }

    /** As [ring], into [c] (no allocation: the CPU fallback calls this per pixel). */
    internal fun ringInto(rho: Float, c: FloatArray) {
        when {
            rho > 1.24f && rho < 1.53f -> { c[0] = 140f; c[1] = 125f; c[2] = 100f }
            rho >= 1.53f && rho < 1.95f -> { c[0] = 228f; c[1] = 212f; c[2] = 172f }
            rho >= 1.95f && rho < 2.02f -> { c[0] = 120f; c[1] = 110f; c[2] = 95f }
            rho >= 2.02f && rho < 2.27f -> { c[0] = 205f; c[1] = 188f; c[2] = 148f }
            else -> { c[0] = 0f; c[1] = 0f; c[2] = 0f }
        }
        c[3] = ringAlpha(rho)
    }

    /**
     * Factor of the ring's shadow on a disc point [n] (design: x (1 - 0.75 alpha)): the ray toward the Sun meets
     * the ring plane at t = -(n . nR) / (sun . nR) > 0. Ring normal nR = (0, cos B, sin B).
     */
    fun ringShadowOnDisc(n: FloatArray, sun: FloatArray, sinB: Float, cosB: Float): Float =
        ringShadowOnDisc(n[0], n[1], n[2], sun, sinB, cosB)

    private fun ringShadowOnDisc(nx: Float, ny: Float, nz: Float, sun: FloatArray, sinB: Float, cosB: Float): Float {
        val sn = sun[1] * cosB + sun[2] * sinB
        val t = if (abs(sinB) < EDGE_ON || abs(sn) < 1e-4f) 0f else -(ny * cosB + nz * sinB) / sn
        if (t <= 0f) return 1f
        val hx = nx + t * sun[0]
        val hy = ny + t * sun[1]
        val hz = nz + t * sun[2]
        return 1f - RING_SHADOW_ON_DISC * ringAlpha(sqrt(hx * hx + hy * hy + hz * hz))
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
    fun planetShadowOnRing(h: FloatArray, sun: FloatArray): Boolean = planetShadowOnRing(h[0], h[1], h[2], sun)

    private fun planetShadowOnRing(hx: Float, hy: Float, hz: Float, sun: FloatArray): Boolean {
        val b = hx * sun[0] + hy * sun[1] + hz * sun[2]
        val c = hx * hx + hy * hy + hz * hz - 1f
        return b < 0f && b * b - c > 0f
    }

    /**
     * Premultiplied RGBA (0..1) of one pixel into [out]. [px], [py] in radii (y down); the [Scene] carries the
     * texture, the radius in px and the spin.
     */
    internal fun shade(s: Scene, px: Float, py: Float, out: FloatArray) {
        val r2 = px * px + py * py
        out.fill(0f)
        if (r2 < 1f) disc(s, px, py, out)
        if (s.ringOn) ring(s, px, py, out)
    }

    /** Per-frame constants: unit sun, spin axis, ring tilt, texture and spin offset in texels, radius in px. */
    internal class Scene(val style: PlanetStyle, light: PlanetLight, rot: Float, val rPx: Float, val tex: IntArray) {
        val sun = light.unitSun()
        val ringOn = light.ringTiltDeg != null
        private val b = light.ringTiltDeg?.let { it * (PI / 180.0).toFloat() } ?: style.axisTiltRad
        private val g = if (ringOn) 0f else style.axisRollRad
        val sinB = sin(b)
        val cosB = cos(b)
        val sinG = sin(g)
        val cosG = cos(g)
        val offTexels = rot / (2f * PI.toFloat()) * PlanetTextures.WIDTH

        /** Per-pixel scratch (one Scene per render, one thread): no allocation inside the pixel loop. */
        val col = FloatArray(3)
        val rc = FloatArray(4)
    }

    private fun disc(s: Scene, px: Float, py: Float, out: FloatArray) {
        val x = px
        val y = -py
        val r2 = x * x + y * y
        val z = sqrt(1f - r2)
        val bx = s.cosG * x + s.sinG * y
        val by0 = -s.sinG * x + s.cosG * y
        val by = s.cosB * by0 + s.sinB * z
        val bz = -s.sinB * by0 + s.cosB * z
        val texel = sample(s, atan2(bx, bz), asin(by.coerceIn(-1f, 1f)))
        val sun = s.sun
        val d = x * sun[0] + y * sun[1] + z * sun[2]
        val style = s.style
        var sh = discIntensity(style.model, style.soft, style.limb, z, d)
        if (style.model == LightModel.LOMMEL_SEELIGER) sh *= style.exposure
        if (s.ringOn && d > 0f) sh *= ringShadowOnDisc(x, y, z, sun, s.sinB, s.cosB)
        val rim = if (style.rim > 0f) (1f - z).pow(3) * max(0f, d + 0.25f) * style.rim else 0f
        val col = s.col
        for (k in 0..2) {
            val channel = ((texel shr (16 - 8 * k)) and 0xFF).toFloat()
            col[k] = channel * sh + style.earthshine * AMBIENT[k] + RIM[k] * rim
        }
        if (s.ringOn && abs(s.sinB) >= EDGE_ON) {
            val ra = visibleRingAlpha(px, py, s.sinB, s.cosB)
            if (ra > 0f) {
                val rc = s.rc
                ringInto(sqrt(x * x + y * y + (y * s.cosB / s.sinB).pow(2)), rc)
                val face = ringFace(s)
                for (k in 0..2) col[k] = col[k] * (1f - ra) + rc[k] * RING_GAIN * face * ra
            }
        }
        val a = min(1f, (1f - sqrt(r2)) * s.rPx * 1.2f)
        for (k in 0..2) out[k] = min(255f, max(0f, col[k])) / 255f * a
        out[3] = a
    }

    /** Ring pixels outside the disc (and the edge-on line, drawn over the disc). */
    private fun ring(s: Scene, px: Float, py: Float, out: FloatArray) {
        val r2 = px * px + py * py
        val ps = -py
        val rc = s.rc
        val alpha: Float
        var light = ringFace(s)
        if (abs(s.sinB) < EDGE_ON) {
            ringInto(abs(px), rc)
            alpha = rc[3] * (1f - smoothstep(0f, 1.5f / s.rPx, abs(ps)))
            light = 0.5f
        } else {
            if (r2 < 1f) return // over the disc: composited in disc()
            val z = -ps * s.cosB / s.sinB
            ringInto(sqrt(px * px + ps * ps + z * z), rc)
            alpha = rc[3]
            if (planetShadowOnRing(px, ps, z, s.sun)) light *= RING_IN_SHADOW
        }
        if (alpha <= 0f) return
        for (k in 0..2) out[k] = rc[k] * RING_GAIN * light / 255f * alpha + out[k] * (1f - alpha)
        out[3] = alpha + out[3] * (1f - alpha)
    }

    /**
     * A static frame of [sizePx] x [sizePx] as non-premultiplied ARGB (for `Bitmap.setPixels`), centre at the
     * middle, radius [planetRadiusPx]. Generates the texture on first use (call off the main thread).
     */
    fun render(body: PlanetBody, light: PlanetLight, sizePx: Int, rot: Float = 0f): IntArray {
        val r = planetRadiusPx(body, sizePx.toFloat())
        val scene = Scene(planetStyle(body), light, rot, r, PlanetTextures.argb(body))
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

/** 1 when the Sun lights the face of the ring we see (sign(sun . nR) == sign(sin B)), else 0.3. */
private fun ringFace(s: PlanetShading.Scene): Float {
    val sn = s.sun[1] * s.cosB + s.sun[2] * s.sinB
    return if (sn * s.sinB > 0f) 1f else UNLIT_RING_FACE
}

/** Nearest texel as in the HTML: u = (lon / 2 pi + 1) mod 1 x 512 + spin, v = floor((0.5 - lat / pi) 256). */
private fun sample(s: PlanetShading.Scene, lon: Float, lat: Float): Int {
    val w = PlanetTextures.WIDTH
    val u0 = ((lon / (2f * PI.toFloat()) + 1f) % 1f) * w + s.offTexels
    var u = floor(u0).toInt() % w
    if (u < 0) u += w
    val v = min(PlanetTextures.HEIGHT - 1, floor((0.5f - lat / PI.toFloat()) * PlanetTextures.HEIGHT).toInt())
    return s.tex[v.coerceAtLeast(0) * w + u]
}

/** Unpremultiplies and packs a premultiplied RGBA. */
private fun argb(p: FloatArray): Int {
    val a = p[3]
    if (a <= 0f) return 0
    fun ch(v: Float) = (min(1f, v / a) * 255f + 0.5f).toInt()
    return ((a * 255f + 0.5f).toInt() shl 24) or (ch(p[0]) shl 16) or (ch(p[1]) shl 8) or ch(p[2])
}

/** GLSL smoothstep (edge0 < edge1). */
internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val UNLIT_RING_FACE = 0.3f
private const val EDGE_ON = 0.005f
