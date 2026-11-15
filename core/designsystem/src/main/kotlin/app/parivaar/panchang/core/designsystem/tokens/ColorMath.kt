@file:Suppress("MagicNumber")
package app.parivaar.panchang.core.designsystem.tokens

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** WCAG 2.x relative luminance of an ARGB colour (alpha ignored). */
fun relativeLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** WCAG 2.x contrast ratio, always >= 1. */
fun contrastRatio(fg: Long, bg: Long): Double {
    val a = relativeLuminance(fg)
    val b = relativeLuminance(bg)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** Per-channel sRGB lerp, rounded; result is opaque. */
fun lerpArgb(from: Long, to: Long, fraction: Double): Long {
    fun ch(shift: Int): Long {
        val a = ((from shr shift) and 0xFF).toDouble()
        val b = ((to shr shift) and 0xFF).toDouble()
        return (a + (b - a) * fraction).roundToInt().coerceIn(0, 255).toLong()
    }
    return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** `xContainer = lerp(surface, x, 0.18)`. */
fun containerOf(surface: Long, role: Long): Long = lerpArgb(surface, role, 0.18)

/** Chip tint: `lerp(surface, role, 0.10)`; light enough to read as a pill, dark enough for role-coloured text at AA. */
fun chipContainerOf(surface: Long, role: Long): Long = lerpArgb(surface, role, 0.10)

/** Returns [h in 0..360, s, l] (HSL, s and l in 0..1). */
fun argbToHsl(argb: Long): DoubleArray {
    val r = ((argb shr 16) and 0xFF) / 255.0
    val g = ((argb shr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2.0
    val d = mx - mn
    if (d == 0.0) return doubleArrayOf(0.0, 0.0, l)
    val s = d / (1.0 - kotlin.math.abs(2.0 * l - 1.0))
    val h = when (mx) {
        r -> ((g - b) / d) % 6.0
        g -> (b - r) / d + 2.0
        else -> (r - g) / d + 4.0
    } * 60.0
    return doubleArrayOf(if (h < 0) h + 360.0 else h, s, l)
}

fun hslToArgb(h: Double, s: Double, l: Double): Long {
    val c = (1.0 - kotlin.math.abs(2.0 * l - 1.0)) * s
    val hp = h / 60.0
    val x = c * (1.0 - kotlin.math.abs(hp % 2.0 - 1.0))
    val (r1, g1, b1) = when {
        hp < 1 -> Triple(c, x, 0.0)
        hp < 2 -> Triple(x, c, 0.0)
        hp < 3 -> Triple(0.0, c, x)
        hp < 4 -> Triple(0.0, x, c)
        hp < 5 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    val m = l - c / 2.0
    fun q(v: Double) = ((v + m) * 255.0).roundToInt().coerceIn(0, 255).toLong()
    return 0xFF000000L or (q(r1) shl 16) or (q(g1) shl 8) or q(b1)
}

/**
 * Moves HSL lightness 1 % at a time (down when [darken], else up) until [seed]
 * reaches [minRatio] against every background. Returns [seed] if it already passes.
 */
fun adjustForContrast(seed: Long, backgrounds: List<Long>, darken: Boolean, minRatio: Double = 4.5): Long {
    val (h, s, l) = argbToHsl(seed).let { Triple(it[0], it[1], it[2]) }
    var step = 0
    while (step <= 100) {
        val candidate = hslToArgb(h, s, (if (darken) l - step * 0.01 else l + step * 0.01).coerceIn(0.0, 1.0))
        if (backgrounds.all { contrastRatio(candidate, it) >= minRatio }) return candidate
        step++
    }
    return if (darken) 0xFF000000L else 0xFFFFFFFFL
}
