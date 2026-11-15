@file:Suppress("MagicNumber", "MaxLineLength")
package app.parivaar.panchang.core.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * THE font swap point. Only this object names a [FontFamily]; nothing else may.
 * Today: system families (Serif display, SansSerif UI, Default = Noto for every Indic script).
 * Owner fonts (OFL) drop into `core/designsystem/src/main/res/font/`; then change the four values below to
 * `FontFamily(Font(R.font.cormorant_medium, FontWeight.Medium), ...)` and nothing else changes.
 */
object PpFonts {
    /** Display and numerals (Cormorant Garamond later). */
    val display: FontFamily = FontFamily.Serif
    /** UI text (Manrope later). */
    val ui: FontFamily = FontFamily.SansSerif
    /** Hindi UI and other Indic scripts (Tiro Devanagari Hindi / Noto Sans later); system Noto fallback. */
    val deva: FontFamily = FontFamily.Default
    /** Shlokas and sankalp (Tiro Devanagari Sanskrit later). */
    val sanskrit: FontFamily = FontFamily.Serif
}

/**
 * Type scale (DESIGN_V2 section 3 with decision D1 floors: body 14 not 13, nothing under 11 sp).
 * Line heights are em multiples; never fix heights on text containers. Everything scales with Bada Text.
 */
object PpType {
    const val MIN_SP = 11f

    val displayXl = TextStyle(fontFamily = PpFonts.display, fontSize = 36.sp, fontWeight = FontWeight.Medium, lineHeight = 1.15.em)
    val displayL = TextStyle(fontFamily = PpFonts.display, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.2.em)
    val displayM = TextStyle(fontFamily = PpFonts.display, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 1.25.em)
    val displayS = TextStyle(fontFamily = PpFonts.display, fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 1.3.em)
    val numXl = TextStyle(
        fontFamily = PpFonts.display, fontSize = 38.sp, fontWeight = FontWeight.Medium, lineHeight = 1.1.em,
        fontFeatureSettings = "lnum,tnum",
    )
    val body = TextStyle(fontFamily = PpFonts.ui, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 1.4.em)
    val meta = TextStyle(fontFamily = PpFonts.ui, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 1.4.em)
    /** Callers upper-case the string (`uppercase()`); no case change applies to Indic scripts. */
    val eyebrow = TextStyle(
        fontFamily = PpFonts.ui, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.em, lineHeight = 1.4.em,
    )
    val label = TextStyle(fontFamily = PpFonts.ui, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.3.em)
    val devaHeadline = TextStyle(fontFamily = PpFonts.deva, fontSize = 24.sp, fontWeight = FontWeight.Normal, lineHeight = 1.4.em)

    /** Shlokas and mantras: generous line height for stacked Indic marks. */
    val sanskrit: TextStyle = TextStyle(fontFamily = PpFonts.sanskrit, fontSize = 16.sp, lineHeight = 1.75.em)

    private val base = Typography()
    private fun TextStyle.ui() = copy(fontFamily = PpFonts.ui, lineHeight = 1.35.em)

    /** Material mapping. bodyLarge stays 16 sp for long-form reading; bodyMedium is the 14 sp body; bodySmall is meta. */
    val typography: Typography = Typography(
        displayLarge = displayXl,
        displayMedium = displayL,
        displaySmall = displayM,
        headlineLarge = displayL,
        headlineMedium = displayM,
        headlineSmall = displayS,
        titleLarge = base.titleLarge.ui(),
        titleMedium = base.titleMedium.ui(),
        titleSmall = base.titleSmall.ui(),
        bodyLarge = body.copy(fontSize = 16.sp),
        bodyMedium = body,
        bodySmall = meta,
        labelLarge = base.labelLarge.ui(),
        labelMedium = base.labelMedium.ui().copy(fontSize = 12.sp),
        labelSmall = label,
    )

    /** Every style that appears on screen, for floor tests. */
    val all: List<TextStyle> = listOf(
        displayXl, displayL, displayM, displayS, numXl, body, meta, eyebrow, label, devaHeadline, sanskrit,
        typography.titleLarge, typography.titleMedium, typography.titleSmall, typography.bodyLarge,
        typography.labelLarge, typography.labelMedium, typography.labelSmall,
    )
}
