@file:Suppress("MagicNumber", "MaxLineLength")
package app.parivaar.panchang.core.designsystem.tokens

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf

/** True when system animations are off or the user chose reduced motion. */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Chrome springs equal to Material `MotionScheme.standard()` (fast/default/slow spatial and effects), defined here
 * because `MotionScheme` is still experimental in material3 1.4.0. Reveal springs are the expressive set.
 * Every spec collapses to `snap()` under reduced motion.
 */
object PpMotion {
    /** Legacy M07 spring (no bounce); kept for existing call sites. */
    fun <T> spec(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    @Composable
    @ReadOnlyComposable
    fun <T> spec(): FiniteAnimationSpec<T> = spec(LocalReducedMotion.current)

    fun <T> fastSpatial(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.9f, 1400f)
    fun <T> defaultSpatial(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.9f, 700f)
    fun <T> slowSpatial(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.9f, 300f)
    fun <T> fastEffects(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(1f, 3800f)
    fun <T> defaultEffects(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(1f, 1600f)
    fun <T> slowEffects(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(1f, 800f)

    /** Reveal and ritual moments (expressive spatial). */
    fun <T> reveal(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(0.8f, 380f)

    /** The daily-reveal Moon fade, 0 to 1 over 900 ms. */
    fun <T> moonFade(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else tween(MOON_FADE_MS, easing = LinearEasing)

    const val MOON_FADE_MS = 900
}

/** Loop periods in ms. Twinkle varies per star within the range. */
object PpLoop {
    const val TWINKLE_MIN_MS = 800
    const val TWINKLE_MAX_MS = 3000
    const val DIYA_MS = 1600
    const val ORB_MS = 3200
}

/**
 * A looping phase 0..1 (restart, linear). Under [LocalReducedMotion] no animation runs and the state holds
 * [restFrame], the designed still frame. Read the value in the draw phase to avoid recomposition.
 */
@Composable
fun rememberLoop(periodMs: Int, restFrame: Float = 0.5f): State<Float> {
    if (LocalReducedMotion.current) {
        return androidx.compose.runtime.remember(restFrame) { mutableFloatStateOf(restFrame) }
    }
    val t = rememberInfiniteTransition(label = "ppLoop")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "phase")
}
