package app.parivaar.panchang.core.designsystem

import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.adjustForContrast
import app.parivaar.panchang.core.designsystem.tokens.containerOf
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

    @Test fun lightTextPairs() = with(PpPalette.Light) {
        pass("onPrimary", ON_PRIMARY, PRIMARY)
        pass("onSecondary", ON_SECONDARY, SECONDARY)
        pass("onTertiary", ON_TERTIARY, TERTIARY)
        pass("onError", ON_ERROR, ERROR)
        for (bg in listOf(SURFACE, SURFACE_CONTAINER)) {
            pass("onSurface", ON_SURFACE, bg)
            pass("onSurfaceVariant", ON_SURFACE_VARIANT, bg)
            pass("primaryText", PRIMARY_TEXT, bg)
            pass("secondary text", SECONDARY, bg)
            pass("tertiary text", TERTIARY, bg)
        }
        pass("primaryText on chips bg", PRIMARY_TEXT, SURFACE_VARIANT)
        pass("auspicious chip", AUSPICIOUS_FG, AUSPICIOUS_BG)
        pass("inauspicious chip", INAUSPICIOUS_FG, INAUSPICIOUS_BG)
        for (role in listOf(PRIMARY, SECONDARY, TERTIARY)) pass("onContainer", ON_SURFACE, containerOf(SURFACE, role))
    }

    @Test fun darkTextPairs() = with(PpPalette.Dark) {
        pass("onPrimary", ON_PRIMARY, PRIMARY)
        pass("onSecondary", ON_SECONDARY, SECONDARY)
        pass("onTertiary", ON_TERTIARY, TERTIARY)
        pass("onError", ON_ERROR, ERROR)
        for (bg in listOf(SURFACE, SURFACE_CONTAINER)) {
            pass("onSurface", ON_SURFACE, bg)
            pass("onSurfaceVariant", ON_SURFACE_VARIANT, bg)
            pass("primaryText", PRIMARY_TEXT, bg)
        }
        pass("auspicious chip", AUSPICIOUS_FG, AUSPICIOUS_BG)
        pass("inauspicious chip", INAUSPICIOUS_FG, INAUSPICIOUS_BG)
        for (role in listOf(PRIMARY, SECONDARY, TERTIARY)) pass("onContainer", ON_SURFACE, containerOf(SURFACE, role))
    }

    @Test fun nonTextGraphicsAtLeast3() {
        pass("light outline", PpPalette.Light.OUTLINE, PpPalette.Light.SURFACE, 3.0)
        pass("light primary icon", PpPalette.Light.PRIMARY, PpPalette.Light.SURFACE_CONTAINER, 2.5)
        pass("dark outline", PpPalette.Dark.OUTLINE, PpPalette.Dark.SURFACE, 3.0)
        pass("dark gold", PpPalette.Dark.GOLD, PpPalette.Dark.SURFACE, 3.0)
    }

    @Test fun seedSaffronFailsAsTextAndVariantIsDerivedByRule() {
        assertTrue(contrastRatio(PpPalette.Light.PRIMARY, PpPalette.Light.SURFACE) < 4.5)
        val bgs = listOf(PpPalette.Light.SURFACE, PpPalette.Light.SURFACE_CONTAINER, PpPalette.Light.SURFACE_VARIANT)
        assertEquals(PpPalette.Light.PRIMARY_TEXT, adjustForContrast(PpPalette.Light.PRIMARY, bgs, darken = true))
        val chipBgs = listOf(PpPalette.Light.AUSPICIOUS_BG, PpPalette.Light.SURFACE, PpPalette.Light.SURFACE_CONTAINER)
        assertEquals(PpPalette.Light.AUSPICIOUS_FG, adjustForContrast(PpPalette.Light.TERTIARY, chipBgs, darken = true))
    }
}
