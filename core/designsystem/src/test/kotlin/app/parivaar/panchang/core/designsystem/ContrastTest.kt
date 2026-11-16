@file:Suppress("MaxLineLength")

package app.parivaar.panchang.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood
import app.parivaar.panchang.core.designsystem.tokens.ratnaColorsOf
import app.parivaar.panchang.core.designsystem.tokens.adjustForContrast
import app.parivaar.panchang.core.designsystem.tokens.contrastRatio
import app.parivaar.panchang.core.designsystem.tokens.relativeLuminance
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {
    private fun pass(name: String, fg: Long, bg: Long, min: Double = 4.5) {
        val r = contrastRatio(fg, bg)
        assertTrue(r >= min, "$name ${java.lang.Long.toHexString(fg)} on ${java.lang.Long.toHexString(bg)} = $r < $min")
    }

    @Test fun luminanceAndRatioReferences() {
        assertEquals(1.0, relativeLuminance(0xFFFFFFFFL))
        assertEquals(0.0, relativeLuminance(0xFF000000L))
        assertTrue(abs(contrastRatio(0xFFFFFFFFL, 0xFF000000L) - 21.0) < 1e-9)
    }

    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

    /** The live Material scheme ([ratnaScheme]) every screen reads, in every Ratna mood (not the retired M07 palette). */
    @Test fun liveRatnaSchemePairsInEveryMood() {
        for (mood in RatnaMood.entries) {
            val c = ratnaScheme(ratnaColorsOf(mood))
            pass("$mood onPrimary", c.onPrimary.argb(), c.primary.argb())
            pass("$mood onPrimaryContainer", c.onPrimaryContainer.argb(), c.primaryContainer.argb())
            pass("$mood onSecondaryContainer", c.onSecondaryContainer.argb(), c.secondaryContainer.argb())
            pass("$mood onTertiaryContainer", c.onTertiaryContainer.argb(), c.tertiaryContainer.argb())
            pass("$mood onErrorContainer", c.onErrorContainer.argb(), c.errorContainer.argb())
            for ((name, bg) in listOf(
                "background" to c.background, "surface" to c.surface, "surfaceVariant" to c.surfaceVariant,
                "surfaceContainerLow" to c.surfaceContainerLow, "surfaceContainer" to c.surfaceContainer,
                "surfaceContainerHigh" to c.surfaceContainerHigh, "surfaceContainerHighest" to c.surfaceContainerHighest,
            )) {
                pass("$mood onSurface on $name", c.onSurface.argb(), bg.argb())
                pass("$mood onSurfaceVariant on $name", c.onSurfaceVariant.argb(), bg.argb())
                pass("$mood secondary text on $name", c.secondary.argb(), bg.argb())
                pass("$mood tertiary text on $name", c.tertiary.argb(), bg.argb())
                pass("$mood outline on $name", c.outline.argb(), bg.argb(), 3.0)
            }
            pass("$mood inverse", c.inverseOnSurface.argb(), c.inverseSurface.argb())
        }
    }

    /** The share card still renders with the M07 light palette (ShareCardRenderer): its pairs stay checked. */
    @Test fun shareCardPalettePairs() = with(PpPalette.Light) {
        for (fg in listOf(ON_SURFACE, ON_SURFACE_VARIANT, PRIMARY_TEXT)) pass("share ${java.lang.Long.toHexString(fg)}", fg, BACKGROUND)
    }

    @Test fun seedSaffronFailsAsTextAndVariantIsDerivedByRule() {
        assertTrue(contrastRatio(PpPalette.Light.PRIMARY, PpPalette.Light.SURFACE) < 4.5)
        val bgs = listOf(PpPalette.Light.SURFACE, PpPalette.Light.SURFACE_CONTAINER, PpPalette.Light.SURFACE_VARIANT)
        assertEquals(PpPalette.Light.PRIMARY_TEXT, adjustForContrast(PpPalette.Light.PRIMARY, bgs, darken = true))
        val chipBgs = listOf(PpPalette.Light.AUSPICIOUS_BG, PpPalette.Light.SURFACE, PpPalette.Light.SURFACE_CONTAINER)
        assertEquals(PpPalette.Light.AUSPICIOUS_FG, adjustForContrast(PpPalette.Light.TERTIARY, chipBgs, darken = true))
    }
}
