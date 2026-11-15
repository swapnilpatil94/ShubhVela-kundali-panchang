@file:Suppress("MagicNumber", "MaxLineLength", "LongMethod", "ComplexMethod", "LongParameterList", "NestedBlockDepth", "MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.sky

import app.parivaar.panchang.core.designsystem.tokens.Gem
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Palette of one gem sky, a port of the GEM table in design/shubhvela-screens.html (rgb 0..255). */
class NebulaPalette(val a: IntArray, val b: IntArray, val c: IntArray, val base: IntArray, val core: IntArray, val seedName: String)

/** Maps the 9 gems onto the HTML palettes (Gomed and Lehsunia have none in the HTML; see docs/DECISIONS.md). */
fun nebulaPaletteOf(gem: Gem): NebulaPalette = when (gem) {
    Gem.MANIKYA -> NebulaPalette(intArrayOf(210, 70, 63), intArrayOf(255, 160, 60), intArrayOf(120, 30, 60), intArrayOf(24, 8, 10), intArrayOf(255, 220, 150), "sun")
    Gem.MOTI -> NebulaPalette(intArrayOf(200, 205, 225), intArrayOf(150, 175, 220), intArrayOf(90, 80, 160), intArrayOf(10, 12, 28), intArrayOf(255, 248, 230), "moon")
    Gem.MOONGA -> NebulaPalette(intArrayOf(224, 106, 78), intArrayOf(255, 170, 90), intArrayOf(140, 40, 80), intArrayOf(22, 8, 18), intArrayOf(255, 200, 150), "mars")
    Gem.PANNA -> NebulaPalette(intArrayOf(47, 163, 107), intArrayOf(90, 220, 200), intArrayOf(40, 80, 160), intArrayOf(6, 16, 24), intArrayOf(200, 255, 220), "mercury")
    Gem.PUKHRAJ -> NebulaPalette(intArrayOf(236, 180, 70), intArrayOf(255, 130, 60), intArrayOf(130, 70, 40), intArrayOf(20, 12, 26), intArrayOf(255, 222, 150), "jupiter")
    Gem.HEERA -> NebulaPalette(intArrayOf(215, 225, 245), intArrayOf(245, 200, 230), intArrayOf(110, 120, 170), intArrayOf(12, 12, 28), intArrayOf(255, 250, 240), "venus")
    Gem.NEELAM -> NebulaPalette(intArrayOf(76, 120, 224), intArrayOf(90, 210, 230), intArrayOf(150, 90, 210), intArrayOf(8, 12, 34), intArrayOf(200, 220, 255), "saturn")
    // Hessonite: warm amber-brown, Mars/Jupiter family.
    Gem.GOMED -> NebulaPalette(intArrayOf(200, 120, 60), intArrayOf(240, 170, 90), intArrayOf(110, 50, 40), intArrayOf(20, 10, 14), intArrayOf(255, 215, 160), "gomed")
    // Cat's eye: olive-gold green, Mercury/Jupiter family.
    Gem.LEHSUNIA -> NebulaPalette(intArrayOf(150, 170, 70), intArrayOf(230, 200, 90), intArrayOf(60, 90, 60), intArrayOf(10, 16, 14), intArrayOf(245, 250, 190), "lehsunia")
}

private fun hash(x: Int, y: Int, z: Int, s: Int): Float {
    var h = (x * 374761393) xor (y * 668265263) xor (z * 1274126177) xor (s * -2048144777)
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h ushr 8) / 16777216f
}

private fun vn(x: Float, y: Float, z: Float, s: Int): Float {
    val xf0 = floor(x); val yf0 = floor(y); val zf0 = floor(z)
    val xi = xf0.toInt(); val yi = yf0.toInt(); val zi = zf0.toInt()
    val xf = x - xf0; val yf = y - yf0; val zf = z - zf0
    val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf); val w = zf * zf * (3 - 2 * zf)
    val a0 = hash(xi, yi, zi, s); val a1 = hash(xi + 1, yi, zi, s)
    val b0 = hash(xi, yi + 1, zi, s); val b1 = hash(xi + 1, yi + 1, zi, s)
    val c0 = hash(xi, yi, zi + 1, s); val c1 = hash(xi + 1, yi, zi + 1, s)
    val d0 = hash(xi, yi + 1, zi + 1, s); val d1 = hash(xi + 1, yi + 1, zi + 1, s)
    val p0 = (a0 + (a1 - a0) * u); val p1 = (b0 + (b1 - b0) * u)
    val q0 = (c0 + (c1 - c0) * u); val q1 = (d0 + (d1 - d0) * u)
    val r0 = p0 + (p1 - p0) * v; val r1 = q0 + (q1 - q0) * v
    return r0 + (r1 - r0) * w
}

private fun fbm(x: Float, y: Float, z: Float, s: Int, o: Int): Float {
    var a = 0.5f; var f = 1f; var t = 0f; var n = 0f
    for (i in 0 until o) {
        t += a * vn(x * f + 31.7f * i, y * f, z * f, s + i)
        n += a; f *= 2.03f; a *= 0.5f
    }
    return t / n
}

/** The HTML's LCG: s = (s * 9301 + 49297) % 233280 (state fits a Long). */
private class Lcg(seed: Int) {
    private var s: Long = seed * 9301L + 49297L
    fun next(): Float { s = (s * 9301L + 49297L) % 233280L; return s / 233280f }
}

/** Renders the nebula at [nw] x [nh] into packed RGB floats (r, g, b interleaved, 0..255). */
private fun renderNebula(p: NebulaPalette, nw: Int, nh: Int, seed: Int): FloatArray {
    val out = FloatArray(nw * nh * 3)
    val cx = 0.62f; val cy = 0.36f
    val aspect = nh.toFloat() / nw
    var n = 0
    for (j in 0 until nh) {
        val y = j.toFloat() / nh * 2.2f * aspect
        for (i in 0 until nw) {
            val x = i.toFloat() / nw * 2.2f
            val wx = x + 0.9f * fbm(x * 1.3f, y * 1.3f, seed.toFloat(), 3, 3)
            val wy = y + 0.9f * fbm(x * 1.3f + 7f, y * 1.3f, (seed + 1).toFloat(), 3, 3)
            var d = fbm(wx * 1.6f, wy * 1.6f, (seed + 2).toFloat(), 7, 5)
            d = max(0f, (d - 0.38f) / 0.5f); d = d * d * (3 - 2 * d)
            val m = fbm(wx * 2.4f + 3f, wy * 2.4f, (seed + 3).toFloat(), 11, 4)
            val dust = max(0f, (fbm(x * 3.2f, y * 3.2f, (seed + 4).toFloat(), 13, 4) - 0.55f) * 3.2f)
            val rx = i.toFloat() / nw - cx; val ry = (j.toFloat() / nh - cy) * aspect
            val r2 = rx * rx + ry * ry
            val core = exp(-r2 * 18f) * 1.2f + exp(-r2 * 90f) * 1.4f
            val k2 = 1f - min(0.85f, dust * d * 1.2f)
            val dd = d * d
            for (k in 0..2) {
                val cr = p.a[k] * (1 - m) + p.b[k] * m
                val v = (p.base[k] + p.c[k] * d * 0.45f + cr * dd * 0.9f + p.core[k] * core * 0.55f) * k2
                out[n++] = min(255f, v)
            }
        }
    }
    return out
}

private fun blend(px: IntArray, idx: Int, r: Float, g: Float, b: Float, a: Float) {
    if (a <= 0f) return
    val q = if (a > 1f) 1f else a
    val p = px[idx]
    val nr = ((p shr 16) and 0xFF) + (r - ((p shr 16) and 0xFF)) * q
    val ng = ((p shr 8) and 0xFF) + (g - ((p shr 8) and 0xFF)) * q
    val nb = (p and 0xFF) + (b - (p and 0xFF)) * q
    px[idx] = (0xFF shl 24) or ((nr + 0.5f).toInt().coerceIn(0, 255) shl 16) or ((ng + 0.5f).toInt().coerceIn(0, 255) shl 8) or (nb + 0.5f).toInt().coerceIn(0, 255)
}

/** Stars as the HTML draws them at dpr 2 (tiny discs; 3 % big with a thin cross and a soft glow), scaled by [k] = width / 720 px. */
private fun drawStars(px: IntArray, w: Int, h: Int, seed: Int) {
    val k = w / 720f
    val rnd = Lcg(seed + 5)
    val count = Math.round(w * h / (360f * k * k))
    for (s in 0 until count) {
        val x = rnd.next() * w; val y = rnd.next() * h
        val big = rnd.next() < 0.03f
        val a = 0.35f + rnd.next() * 0.6f
        val r = (if (big) 1.6f else 0.6f + rnd.next() * 0.8f) * 2f * k
        disc(px, w, h, x, y, r, a)
        if (big) {
            val arm = 18f * k; val half = 0.8f * 2f * k / 2f
            val aa = a * 0.55f
            val x0 = max(0, (x - arm).toInt()); val x1 = min(w - 1, (x + arm).toInt() + 1)
            val y0 = max(0, (y - arm).toInt()); val y1 = min(h - 1, (y + arm).toInt() + 1)
            for (py in y0..y1) for (qx in x0..x1) {
                val dx = qx + 0.5f - x; val dy = py + 0.5f - y
                val inH = if (kotlin.math.abs(dx) <= arm) (half + 0.5f - kotlin.math.abs(dy)) else 0f
                val inV = if (kotlin.math.abs(dy) <= arm) (half + 0.5f - kotlin.math.abs(dx)) else 0f
                val cov = max(inH, inV).coerceIn(0f, 1f)
                if (cov > 0f) blend(px, py * w + qx, 255f, 250f, 240f, aa * cov)
            }
            val gr = 16f * k
            val gx0 = max(0, (x - gr).toInt()); val gx1 = min(w - 1, (x + gr).toInt() + 1)
            val gy0 = max(0, (y - gr).toInt()); val gy1 = min(h - 1, (y + gr).toInt() + 1)
            for (py in gy0..gy1) for (qx in gx0..gx1) {
                val dx = qx + 0.5f - x; val dy = py + 0.5f - y
                val t = 1f - sqrt(dx * dx + dy * dy) / gr
                if (t > 0f) blend(px, py * w + qx, 255f, 240f, 220f, a * 0.5f * t)
            }
        }
    }
}

private fun disc(px: IntArray, w: Int, h: Int, x: Float, y: Float, r: Float, a: Float) {
    val x0 = max(0, (x - r - 1).toInt()); val x1 = min(w - 1, (x + r + 1).toInt())
    val y0 = max(0, (y - r - 1).toInt()); val y1 = min(h - 1, (y + r + 1).toInt())
    for (py in y0..y1) for (qx in x0..x1) {
        val dx = qx + 0.5f - x; val dy = py + 0.5f - y
        val cov = (r + 0.5f - sqrt(dx * dx + dy * dy)).coerceIn(0f, 1f)
        if (cov > 0f) blend(px, py * w + qx, 255f, 250f, 240f, a * cov)
    }
}

/**
 * Faithful port of the HTML `nebula()`: domain-warped fbm gas, dust lanes, central core glow, then the star field.
 * The gas is computed at ~0.5 scale and bilinearly upsampled; stars are drawn at full resolution. Deterministic, opaque ARGB.
 */
fun nebulaPixels(gem: Gem, width: Int, height: Int): IntArray {
    val p = nebulaPaletteOf(gem)
    val seed = (p.seedName.length * 13) % 97
    val nw = max(60, (width * 0.5f).toInt()); val nh = max(60, (height * 0.5f).toInt())
    val neb = renderNebula(p, nw, nh, seed)
    val out = IntArray(width * height)
    val sx = nw.toFloat() / width; val sy = nh.toFloat() / height
    for (y in 0 until height) {
        val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, nh - 1f)
        val y0 = fy.toInt(); val y1 = min(y0 + 1, nh - 1); val ty = fy - y0
        for (x in 0 until width) {
            val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, nw - 1f)
            val x0 = fx.toInt(); val x1 = min(x0 + 1, nw - 1); val tx = fx - x0
            val i00 = (y0 * nw + x0) * 3; val i10 = (y0 * nw + x1) * 3
            val i01 = (y1 * nw + x0) * 3; val i11 = (y1 * nw + x1) * 3
            var c = 0xFF shl 24
            for (k in 0..2) {
                val top = neb[i00 + k] + (neb[i10 + k] - neb[i00 + k]) * tx
                val bot = neb[i01 + k] + (neb[i11 + k] - neb[i01 + k]) * tx
                c = c or ((top + (bot - top) * ty + 0.5f).toInt().coerceIn(0, 255) shl (16 - 8 * k))
            }
            out[y * width + x] = c
        }
    }
    drawStars(out, width, height, seed)
    return out
}
