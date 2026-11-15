package app.parivaar.panchang.core.designsystem.tokens

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/** True when system animations are off or the user chose reduced motion. */
val LocalReducedMotion = compositionLocalOf { false }

/** One spring everywhere, no bounce. */
object PpMotion {
    fun <T> spec(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        }

    @Composable
    @ReadOnlyComposable
    fun <T> spec(): FiniteAnimationSpec<T> = spec(LocalReducedMotion.current)
}
