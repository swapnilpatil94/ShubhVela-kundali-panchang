package app.parivaar.panchang.core.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnitType
import app.parivaar.panchang.core.designsystem.tokens.PpType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeScaleTest {
    private val t = PpType.typography

    @Test fun bodyAtLeast16sp() {
        assertEquals(16f, t.bodyLarge.fontSize.value)
        assertEquals(16f, t.bodyMedium.fontSize.value)
        assertEquals(14f, t.bodySmall.fontSize.value)
    }

    @Test fun lineHeightsAreEmMultiples() {
        for (s in listOf(t.bodyLarge, t.bodyMedium, t.bodySmall, PpType.sanskrit)) {
            assertEquals(TextUnitType.Em, s.lineHeight.type)
        }
        assertEquals(1.4f, t.bodyLarge.lineHeight.value)
        assertEquals(1.75f, PpType.sanskrit.lineHeight.value)
    }

    @Test fun serifForDisplayAndHeadlineOnly() {
        val serif = listOf(t.displayLarge, t.displayMedium, t.displaySmall)
        for (s in serif + listOf(t.headlineLarge, t.headlineMedium, t.headlineSmall)) {
            assertEquals(FontFamily.Serif, s.fontFamily)
        }
        for (s in listOf(t.titleLarge, t.bodyLarge, t.labelLarge)) assertEquals(FontFamily.Default, s.fontFamily)
        assertTrue(t.bodyLarge.fontSize.value >= 16f)
    }
}
