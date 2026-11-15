@file:Suppress("MaxLineLength")
package app.parivaar.panchang.core.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnitType
import app.parivaar.panchang.core.designsystem.tokens.PpFonts
import app.parivaar.panchang.core.designsystem.tokens.PpType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeScaleTest {
    private val t = PpType.typography

    @Test fun bodyIs14NotDesign13() {
        assertEquals(14f, PpType.body.fontSize.value)
        assertEquals(14f, t.bodyMedium.fontSize.value)
        assertEquals(16f, t.bodyLarge.fontSize.value) // long-form reading
        assertEquals(12f, PpType.meta.fontSize.value)
    }

    @Test fun scaleMatchesDesignWithFloors() {
        assertEquals(36f, PpType.displayXl.fontSize.value)
        assertEquals(30f, PpType.displayL.fontSize.value)
        assertEquals(24f, PpType.displayM.fontSize.value)
        assertEquals(20f, PpType.displayS.fontSize.value)
        assertEquals(38f, PpType.numXl.fontSize.value)
        assertEquals("lnum,tnum", PpType.numXl.fontFeatureSettings)
        assertEquals(11f, PpType.eyebrow.fontSize.value)
        assertEquals(0.2f, PpType.eyebrow.letterSpacing.value)
        assertEquals(11f, PpType.label.fontSize.value)
    }

    @Test fun nothingBelow11sp() {
        for (s in PpType.all) assertTrue(s.fontSize.value >= PpType.MIN_SP, "style below floor: ${s.fontSize}")
    }

    @Test fun lineHeightsAreEmMultiples() {
        for (s in PpType.all) {
            if (s.lineHeight.isSp || s.lineHeight.isEm) assertEquals(TextUnitType.Em, s.lineHeight.type)
        }
        assertEquals(1.4f, PpType.body.lineHeight.value)
        assertEquals(1.75f, PpType.sanskrit.lineHeight.value)
    }

    /** 200 % system scale with Bada Text (x1.2): the largest effective size stays finite and every style keeps its floor in sp. */
    @Test fun scalingKeepsFloorAndStaysBounded() {
        val effective = 2.0f * 1.2f
        for (s in PpType.all) {
            assertTrue(s.fontSize.value * effective >= PpType.MIN_SP * effective)
            assertTrue(s.fontSize.value * effective <= 38f * 2.4f + 0.01f)
        }
    }

    @Test fun familiesComeOnlyFromTheSwapPoint() {
        for (s in listOf(t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall)) {
            assertEquals(PpFonts.display, s.fontFamily)
        }
        for (s in listOf(t.titleLarge, t.bodyLarge, t.labelLarge, t.labelSmall)) assertEquals(PpFonts.ui, s.fontFamily)
        assertEquals(PpFonts.sanskrit, PpType.sanskrit.fontFamily)
        assertEquals(FontFamily.Serif, PpFonts.display)
    }
}
