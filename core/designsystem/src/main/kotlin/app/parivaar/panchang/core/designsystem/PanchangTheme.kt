@file:Suppress("MagicNumber", "MaxLineLength")
package app.parivaar.panchang.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.parivaar.panchang.core.designsystem.surfaces.LocalReduceTransparency
import app.parivaar.panchang.core.designsystem.tokens.LocalRatnaColors
import app.parivaar.panchang.core.designsystem.tokens.LocalReducedMotion
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.RatnaColors
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood
import app.parivaar.panchang.core.designsystem.tokens.chipContainerOf
import app.parivaar.panchang.core.designsystem.tokens.containerOf
import app.parivaar.panchang.core.designsystem.tokens.ratnaColorsOf
import app.parivaar.panchang.core.model.settings.ThemeMode

/**
 * M07 brand roles, now derived from [RatnaColors] so existing call sites keep compiling.
 * New code should read [PpTheme.ratna].
 */
@Immutable
data class PpColors(
    val primaryText: Color,
    val gold: Color,
    val auspiciousBg: Color,
    val auspiciousFg: Color,
    val inauspiciousBg: Color,
    val inauspiciousFg: Color,
    val neutralBg: Color,
    val neutralFg: Color,
)

val LocalPpColors = compositionLocalOf { ppColorsOf(ratnaColorsOf(RatnaMood.NIGHT)) }

object PpTheme {
    val colors: PpColors
        @Composable @ReadOnlyComposable get() = LocalPpColors.current

    /** Ratna roles of the current mood (bg, lacquer, text, gold, kumkum, okay, glass tint). */
    val ratna: RatnaColors
        @Composable @ReadOnlyComposable get() = LocalRatnaColors.current
}

internal fun ppColorsOf(r: RatnaColors): PpColors {
    fun container(role: Color) = Color(chipContainerOf(r.lacquerBottom.toArgbLong(), role.toArgbLong()))
    return PpColors(
        primaryText = r.gold, gold = r.gold,
        auspiciousBg = container(r.gold), auspiciousFg = r.gold,
        inauspiciousBg = container(r.kumkum), inauspiciousFg = r.kumkum,
        neutralBg = container(r.okay), neutralFg = r.okay,
    )
}

private fun Color.toArgbLong(): Long =
    (((alpha * 255f + 0.5f).toLong() and 0xFF) shl 24) or
        (((red * 255f + 0.5f).toLong() and 0xFF) shl 16) or
        (((green * 255f + 0.5f).toLong() and 0xFF) shl 8) or
        ((blue * 255f + 0.5f).toLong() and 0xFF)

/** Material scheme from the Ratna roles: gold primary, kumkum secondary, okay tertiary. */
internal fun ratnaScheme(r: RatnaColors): ColorScheme {
    val surface = r.bg0
    fun container(role: Color) = Color(containerOf(surface.toArgbLong(), role.toArgbLong()))
    val onAccent = if (r.isDark) r.bg0 else Color.White
    return if (r.isDark) {
        darkColorScheme(
            primary = r.goldFill, onPrimary = r.onGoldFill, primaryContainer = container(r.goldFill), onPrimaryContainer = r.text,
            secondary = r.kumkum, onSecondary = onAccent, secondaryContainer = container(r.kumkum), onSecondaryContainer = r.text,
            tertiary = r.okay, onTertiary = onAccent, tertiaryContainer = container(r.okay), onTertiaryContainer = r.text,
            background = r.bg0, onBackground = r.text, surface = surface, onSurface = r.text,
            surfaceVariant = r.ink3, onSurfaceVariant = r.text2, surfaceTint = r.goldFill,
            inverseSurface = r.text, inverseOnSurface = r.bg0, inversePrimary = r.goldFill,
            outline = r.text3, outlineVariant = r.line,
            error = r.kumkum, onError = onAccent, errorContainer = container(r.kumkum), onErrorContainer = r.text,
            surfaceDim = r.bg0, surfaceBright = r.ink3,
            surfaceContainerLowest = r.bg0, surfaceContainerLow = r.lacquerBottom, surfaceContainer = r.lacquerTop,
            surfaceContainerHigh = r.lacquerTop, surfaceContainerHighest = r.ink3,
        )
    } else {
        lightColorScheme(
            primary = r.goldFill, onPrimary = r.onGoldFill, primaryContainer = container(r.goldFill), onPrimaryContainer = r.text,
            secondary = r.kumkum, onSecondary = onAccent, secondaryContainer = container(r.kumkum), onSecondaryContainer = r.text,
            tertiary = r.okay, onTertiary = onAccent, tertiaryContainer = container(r.okay), onTertiaryContainer = r.text,
            background = r.bg0, onBackground = r.text, surface = surface, onSurface = r.text,
            surfaceVariant = r.ink3, onSurfaceVariant = r.text2, surfaceTint = r.goldFill,
            inverseSurface = r.text, inverseOnSurface = r.bg0, inversePrimary = r.goldFill,
            outline = r.text3, outlineVariant = r.line,
            error = r.kumkum, onError = onAccent, errorContainer = container(r.kumkum), onErrorContainer = r.text,
            surfaceDim = r.bg1, surfaceBright = r.lacquerTop,
            surfaceContainerLowest = r.lacquerTop, surfaceContainerLow = r.lacquerTop, surfaceContainer = r.lacquerTop,
            surfaceContainerHigh = r.ink3, surfaceContainerHighest = r.ink3,
        )
    }
}

/**
 * Ratna Glass theme. Same call signature as M07 plus [mood]: null derives it from [themeMode]
 * (DARK to NIGHT, LIGHT to CHANDAN, SYSTEM by the system setting). [dynamicColor] is accepted and ignored
 * under Ratna (decision D6: brand palette wins; the stored setting is kept). [reduceTransparency] (decision D4) forces solid chrome surfaces. [badaText] stacks 1.2x on the font scale.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun PanchangTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    badaText: Boolean = false,
    reduceMotion: Boolean = false,
    mood: RatnaMood? = null,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val resolved = mood ?: moodFor(themeMode, systemDark)
    val context = LocalContext.current
    val animationsOff = remember(context) { animatorsDisabled(context) }
    val density = LocalDensity.current
    val scaled = if (badaText) Density(density.density, density.fontScale * BADA_TEXT_SCALE) else density

    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalReducedMotion provides (reduceMotion || animationsOff),
        LocalReduceTransparency provides reduceTransparency,
    ) {
        RatnaProviders(ratnaColorsOf(resolved), content)
    }
}

/** Scoped mood (UTSAV on a festival hero, PAPER in regional views). Never wrap the whole app in it. */
@Composable
fun RatnaMoodScope(mood: RatnaMood, content: @Composable () -> Unit) {
    RatnaProviders(ratnaColorsOf(mood), content)
}

@Composable
private fun RatnaProviders(ratna: RatnaColors, content: @Composable () -> Unit) {
    val scheme = remember(ratna) { ratnaScheme(ratna) }
    val pp = remember(ratna) { ppColorsOf(ratna) }
    CompositionLocalProvider(LocalRatnaColors provides ratna, LocalPpColors provides pp) {
        MaterialTheme(colorScheme = scheme, typography = PpType.typography, shapes = PpShapes.material, content = content)
    }
}

internal fun moodFor(mode: ThemeMode, systemDark: Boolean): RatnaMood = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) RatnaMood.NIGHT else RatnaMood.CHANDAN
    ThemeMode.LIGHT -> RatnaMood.CHANDAN
    ThemeMode.DARK -> RatnaMood.NIGHT
}

private fun animatorsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

const val BADA_TEXT_SCALE = 1.2f
