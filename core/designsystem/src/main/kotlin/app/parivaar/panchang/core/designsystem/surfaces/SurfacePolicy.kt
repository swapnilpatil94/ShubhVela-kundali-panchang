@file:Suppress("MagicNumber", "MaxLineLength")
package app.parivaar.panchang.core.designsystem.surfaces

import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import kotlin.math.roundToLong

/** How a glass surface is drawn (M07R section 6 ladder). */
enum class GlassKind {
    /** API 33+: blur, saturation, tint and a tilt-following specular. */
    BLUR_TILT,
    /** API 31-32, or API 33+ with Reduce Motion / battery saver: blur and a static highlight. */
    BLUR_STATIC,
    /** API 30 and below, no backdrop, Reduce Transparency or battery saver: solid ink3 plus a gold hairline. */
    SOLID,
}

object SurfacePolicy {
    const val API_BLUR = 31
    const val API_TILT = 33

    fun glassKind(sdk: Int, reduceTransparency: Boolean, batterySaver: Boolean, reduceMotion: Boolean, hasBackdrop: Boolean): GlassKind =
        when {
            !hasBackdrop || reduceTransparency || batterySaver || sdk < API_BLUR -> GlassKind.SOLID
            sdk >= API_TILT && !reduceMotion -> GlassKind.BLUR_TILT
            else -> GlassKind.BLUR_STATIC
        }

    /** The tilt sensor is only worth registering for [GlassKind.BLUR_TILT]. */
    fun tiltActive(sdk: Int, reduceTransparency: Boolean, batterySaver: Boolean, reduceMotion: Boolean): Boolean =
        glassKind(sdk, reduceTransparency, batterySaver, reduceMotion, hasBackdrop = true) == GlassKind.BLUR_TILT
}

/** Pure colour and tint math for surfaces, ARGB Longs. */
object SurfaceMath {
    const val HERO_ALPHA = 0.55f
    const val WARN_ALPHA = 0.45f
    const val FALLBACK_HAIRLINE_ALPHA = 0.55f
    const val HIGHLIGHT_ALPHA = 0.22f
    const val MAX_TILT_DEG = 10f
    const val TILT_LOW_PASS = 0.1f

    /** Replaces the alpha of [argb] with [alpha] (0..1). */
    fun withAlpha(argb: Long, alpha: Float): Long =
        ((alpha.coerceIn(0f, 1f) * 255f).roundToLong() shl 24) or (argb and 0xFFFFFFL)

    /** Lacquer border: hero gold at .55, warn kumkum at .45, otherwise the hairline of the mood. */
    fun lacquerBorder(spec: PpPalette.Spec, hero: Boolean, warn: Boolean): Long = when {
        warn -> withAlpha(spec.kumkum, WARN_ALPHA)
        hero -> withAlpha(spec.goldFill, HERO_ALPHA)
        else -> spec.line
    }

    /** Solid fallback fill: ink3 of the mood. */
    fun solidFill(spec: PpPalette.Spec): Long = spec.ink3 or 0xFF000000L

    /** Gold hairline of the fallback: haldi on dark moods, deep gold on light ones, alpha .55. */
    fun solidHairline(spec: PpPalette.Spec): Long = withAlpha(spec.goldFill, FALLBACK_HAIRLINE_ALPHA)

    /** One low-pass step (alpha 0.1) then clamp to +-10 degrees. */
    fun lowPassTilt(previous: Float, sampleDeg: Float): Float =
        (previous + TILT_LOW_PASS * (sampleDeg - previous)).coerceIn(-MAX_TILT_DEG, MAX_TILT_DEG)

    /** Specular centre x as a fraction of the surface width for a tilt in degrees: 0.5 at rest, +-0.35 at the clamp. */
    fun specularX(tiltDeg: Float): Float = 0.5f + (tiltDeg.coerceIn(-MAX_TILT_DEG, MAX_TILT_DEG) / MAX_TILT_DEG) * 0.35f
}
