@file:Suppress("MagicNumber", "MatchingDeclarationName", "MaxLineLength")

package app.parivaar.panchang.core.designsystem.sky

import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.contrastRatio
import app.parivaar.panchang.core.designsystem.tokens.lighten
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Pure sky-header model (no Android types): text-safe zone, luminance cap, shade stops and the worst-case contrast model. */
object SkySafeZone {
    /** Relative luminance cap inside the zone: worst case #F6F1E7 text gives 7.46:1; 7:1 holds up to 0.0833. */
    const val MAX_LUMINANCE = 0.075f

    /** The text column's width fraction without a planet ([SkyHeaderSpec.TEXT_FRACTION]); with one it is narrower. */
    const val X_FRAC = SkyHeaderSpec.TEXT_FRACTION

    /** Bottom padding of the bottom-anchored text column ([SkyHeaderSpec.TEXT_BOTTOM_PAD_DP]). */
    fun textBottomFrac(headerHeightDp: Float): Float = 1f - SkyHeaderSpec.TEXT_BOTTOM_PAD_DP / headerHeightDp

    /**
     * Vertical extent of the zone. The text column is bottom-anchored and the header height is content-driven
     * (min [SkyHeaderSpec.HEIGHT_DP] plus the status bar, growing with font scale), so its bottom edge
     * [textBottomFrac] tends to 1 as the header grows: the only bound valid for every height is the full height.
     */
    const val Y_FRAC = 1f
    const val FEATHER_DP = 14f
}

/** Colours drawn as text on the sky header, checked by the worst-case model. */
object SkyText {
    /** The meta line's hora-lord span: 13 sp semibold, so it is held to 7:1 like body text. */
    const val MIN_CONTRAST = 7.0

    private val accents: Map<Gem, Long> by lazy { Gem.entries.associateWith { accentOf(it) } }

    /** The gem lightened by the smallest amount (at least 25 %) that gives [MIN_CONTRAST] over the worst capped sky. */
    fun horaLordArgb(gem: Gem): Long = accents.getValue(gem)

    private fun accentOf(gem: Gem): Long {
        var f = BASE_LIGHTEN
        while (f < 1f) {
            val c = lighten(0xFF000000L or gem.argb, f)
            if (SkyContrastModel.worstContrast(c, COARSE_STEPS) >= MIN_CONTRAST + MARGIN) return c
            f += STEP
        }
        return lighten(0xFF000000L or gem.argb, 1f)
    }

    private const val BASE_LIGHTEN = 0.25f
    private const val STEP = 0.01f
    private const val COARSE_STEPS = 20
    private const val MARGIN = 0.05 // the coarse grid may miss the exact worst sample; the test checks the fine grid
}

/** Reading shade (rgb 8,10,20, left to right) and fade into ink0 (top to bottom): stops (fraction, alpha). */
object SkyShade {
    const val SHADE_RGB = 0xFF080A14L
    val shadeStops = listOf(0f to 0.78f, 0.55f to 0.55f, 1f to 0.12f)
    val fadeStops = listOf(0f to 0.25f, 0.45f to 0.10f, 0.72f to 0.78f, 1f to 1.0f)

    private fun at(stops: List<Pair<Float, Float>>, t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        for (i in 1 until stops.size) {
            val (x1, a1) = stops[i]
            if (x <= x1) {
                val (x0, a0) = stops[i - 1]
                return a0 + (a1 - a0) * (x - x0) / (x1 - x0)
            }
        }
        return stops.last().second
    }

    fun shadeAlpha(xFrac: Float): Float = at(shadeStops, xFrac)
    fun fadeAlpha(yFrac: Float): Float = at(fadeStops, yFrac)
}

private fun toLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
private fun fromLinear(c: Float): Float = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f

/** WCAG relative luminance of sRGB components in 0..1. */
fun luminanceOf(r: Float, g: Float, b: Float): Float = 0.2126f * toLinear(r) + 0.7152f * toLinear(g) + 0.0722f * toLinear(b)

/**
 * Scales an sRGB colour (components 0..1) in linear light so its relative luminance is at most [cap].
 * Pixels already at or below [cap] are returned unchanged (never darkened); the result keeps hue and is monotone in input luminance.
 */
fun capLuminance(r: Float, g: Float, b: Float, cap: Float = SkySafeZone.MAX_LUMINANCE): FloatArray {
    val lr = toLinear(r); val lg = toLinear(g); val lb = toLinear(b)
    val l = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb
    if (l <= cap) return floatArrayOf(r, g, b)
    val s = cap / l
    return floatArrayOf(fromLinear(lr * s), fromLinear(lg * s), fromLinear(lb * s))
}

/** 1 inside the zone, fading to 0 over [featherPx] outside its right and bottom edges (pixel centres). */
fun safeZoneWeight(x: Int, y: Int, width: Int, height: Int, featherPx: Float): Float {
    val dx = max(0f, (x + 0.5f) - SkySafeZone.X_FRAC * width)
    val dy = max(0f, (y + 0.5f) - SkySafeZone.Y_FRAC * height)
    if (featherPx <= 0f) return if (dx > 0f || dy > 0f) 0f else 1f
    return (1f - min(dx / featherPx, 1f)) * (1f - min(dy / featherPx, 1f))
}

/**
 * Applies the text-safe luminance cap in place to ARGB pixels ([width] x [height]); decision D3: on the CPU for every image on every API.
 * Inside the zone 8-bit channels are floored so quantisation can never lift luminance above the cap; the feather blends toward the original.
 */
fun applyTextSafeZone(argb: IntArray, width: Int, height: Int, featherPx: Float, cap: Float = SkySafeZone.MAX_LUMINANCE) {
    for (y in 0 until height) {
        for (x in 0 until width) {
            val w = safeZoneWeight(x, y, width, height, featherPx)
            if (w <= 0f) continue
            val p = argb[y * width + x]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val c = capLuminance(r, g, b, cap)
            fun out(orig: Float, capped: Float): Int {
                val v = orig + (capped - orig) * w
                return (if (w >= 1f) floor(v * 255f + 1e-3f) else v * 255f + 0.5f).toInt().coerceIn(0, 255)
            }
            argb[y * width + x] = (p and 0xFF000000.toInt()) or (out(r, c[0]) shl 16) or (out(g, c[1]) shl 8) or out(b, c[2])
        }
    }
}

/** Procedural sky for a gem (nebula + stars, port of the design HTML); see [nebulaPixels]. Opaque ARGB, deterministic. */
fun placeholderSkyPixels(gem: Gem, width: Int, height: Int): IntArray = nebulaPixels(gem, width, height)

/** Worst-case header contrast model: brightest allowed pixel under shade and fade, sRGB compositing as Compose draws it. */
object SkyContrastModel {
    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt().coerceIn(0, 255)

    private fun over(base: Long, top: Long, alpha: Float): Long {
        fun c(s: Int) = lerp(((base shr s) and 0xFF).toInt(), ((top shr s) and 0xFF).toInt(), alpha).toLong()
        return 0xFF000000L or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    /** Candidate worst pixels at exactly the cap: grey and the three primaries (floored 8-bit, as the pipeline stores them). */
    fun cappedCandidates(cap: Float = SkySafeZone.MAX_LUMINANCE): List<Long> =
        listOf(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 0f, 1f), floatArrayOf(0f, 1f, 1f))
            .map { v ->
                val c = capLuminance(v[0], v[1], v[2], cap)
                fun q(f: Float) = floor(f * 255f + 1e-3f).toLong().coerceIn(0, 255)
                0xFF000000L or (q(c[0]) shl 16) or (q(c[1]) shl 8) or q(c[2])
            }

    fun background(pixel: Long, xFrac: Float, yFrac: Float): Long {
        val shaded = over(pixel, SkyShade.SHADE_RGB, SkyShade.shadeAlpha(xFrac))
        return over(shaded, PpPalette.INK0, SkyShade.fadeAlpha(yFrac))
    }

    /** Minimum contrast of [text] over the whole zone (x <= [SkySafeZone.X_FRAC], y <= [SkySafeZone.Y_FRAC]), worst capped pixel at every sample. */
    fun worstContrast(text: Long, steps: Int = 40): Double {
        var worst = Double.MAX_VALUE
        for (px in cappedCandidates()) for (i in 0..steps) for (j in 0..steps) {
            val x = SkySafeZone.X_FRAC * i / steps
            val y = SkySafeZone.Y_FRAC * j / steps
            worst = min(worst, contrastRatio(text, background(px, x, y)))
        }
        return worst
    }
}
