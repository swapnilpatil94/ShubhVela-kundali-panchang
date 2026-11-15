@file:Suppress("MagicNumber")
package app.parivaar.panchang.core.designsystem

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import app.parivaar.panchang.core.designsystem.tokens.LocalReducedMotion
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.containerOf
import app.parivaar.panchang.core.model.settings.ThemeMode

/** Brand colours that have no Material 3 role. Text variants always pass AA over surface. */
@Immutable
data class PpColors(
    val primaryText: Color,
    val gold: Color, // non-text only
    val auspiciousBg: Color,
    val auspiciousFg: Color,
    val inauspiciousBg: Color,
    val inauspiciousFg: Color,
)

val LocalPpColors = compositionLocalOf { lightPpColors() }

object PpTheme {
    val colors: PpColors
        @Composable @ReadOnlyComposable get() = LocalPpColors.current
}

private fun c(argb: Long) = Color(argb)

private fun lightPpColors() = PpColors(
    c(PpPalette.Light.PRIMARY_TEXT), c(PpPalette.Light.GOLD),
    c(PpPalette.Light.AUSPICIOUS_BG), c(PpPalette.Light.AUSPICIOUS_FG),
    c(PpPalette.Light.INAUSPICIOUS_BG), c(PpPalette.Light.INAUSPICIOUS_FG),
)

private fun darkPpColors() = PpColors(
    c(PpPalette.Dark.PRIMARY_TEXT), c(PpPalette.Dark.GOLD),
    c(PpPalette.Dark.AUSPICIOUS_BG), c(PpPalette.Dark.AUSPICIOUS_FG),
    c(PpPalette.Dark.INAUSPICIOUS_BG), c(PpPalette.Dark.INAUSPICIOUS_FG),
)

internal fun brandLightScheme(): ColorScheme = with(PpPalette.Light) {
    lightColorScheme(
        primary = c(PRIMARY), onPrimary = c(ON_PRIMARY),
        primaryContainer = c(containerOf(SURFACE, PRIMARY)), onPrimaryContainer = c(ON_SURFACE),
        secondary = c(SECONDARY), onSecondary = c(ON_SECONDARY),
        secondaryContainer = c(containerOf(SURFACE, SECONDARY)), onSecondaryContainer = c(ON_SURFACE),
        tertiary = c(TERTIARY), onTertiary = c(ON_TERTIARY),
        tertiaryContainer = c(containerOf(SURFACE, TERTIARY)), onTertiaryContainer = c(ON_SURFACE),
        background = c(BACKGROUND), onBackground = c(ON_SURFACE),
        surface = c(SURFACE), onSurface = c(ON_SURFACE),
        surfaceVariant = c(SURFACE_VARIANT), onSurfaceVariant = c(ON_SURFACE_VARIANT),
        surfaceContainer = c(SURFACE_CONTAINER),
        outline = c(OUTLINE), outlineVariant = c(OUTLINE_VARIANT),
        error = c(ERROR), onError = c(ON_ERROR),
    )
}

internal fun brandDarkScheme(): ColorScheme = with(PpPalette.Dark) {
    darkColorScheme(
        primary = c(PRIMARY), onPrimary = c(ON_PRIMARY),
        primaryContainer = c(containerOf(SURFACE, PRIMARY)), onPrimaryContainer = c(ON_SURFACE),
        secondary = c(SECONDARY), onSecondary = c(ON_SECONDARY),
        secondaryContainer = c(containerOf(SURFACE, SECONDARY)), onSecondaryContainer = c(ON_SURFACE),
        tertiary = c(TERTIARY), onTertiary = c(ON_TERTIARY),
        tertiaryContainer = c(containerOf(SURFACE, TERTIARY)), onTertiaryContainer = c(ON_SURFACE),
        background = c(BACKGROUND), onBackground = c(ON_SURFACE),
        surface = c(SURFACE), onSurface = c(ON_SURFACE),
        surfaceVariant = c(SURFACE_VARIANT), onSurfaceVariant = c(ON_SURFACE_VARIANT),
        surfaceContainer = c(SURFACE_CONTAINER),
        outline = c(OUTLINE), outlineVariant = c(OUTLINE_VARIANT),
        error = c(ERROR), onError = c(ON_ERROR),
    )
}

/**
 * Brand theme. Dynamic colour is opt-in (API 31+) and never used for share cards.
 * [badaText] stacks a 1.2x scale on the system font scale.
 */
@Composable
fun PanchangTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    badaText: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = isDark(themeMode, systemDark)
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> brandDarkScheme()
        else -> brandLightScheme()
    }
    val ppColors = when {
        useDynamic -> dynamicPpColors(scheme)
        dark -> darkPpColors()
        else -> lightPpColors()
    }
    val animationsOff = remember(context) { animatorsDisabled(context) }
    val density = LocalDensity.current
    val scaled = if (badaText) Density(density.density, density.fontScale * BADA_TEXT_SCALE) else density

    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalPpColors provides ppColors,
        LocalReducedMotion provides (reduceMotion || animationsOff),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PpType.typography,
            shapes = PpShapes.material,
            content = content,
        )
    }
}

private fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private fun dynamicPpColors(scheme: ColorScheme) = PpColors(
    scheme.primary, scheme.primary, scheme.tertiaryContainer, scheme.onTertiaryContainer,
    scheme.errorContainer, scheme.onErrorContainer,
)

private fun animatorsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

const val BADA_TEXT_SCALE = 1.2f
