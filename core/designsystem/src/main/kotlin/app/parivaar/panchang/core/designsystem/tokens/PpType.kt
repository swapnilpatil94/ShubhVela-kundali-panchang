@file:Suppress("MagicNumber")
package app.parivaar.panchang.core.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** System fonts only: Serif for display/headline, Default (Noto, all 12 scripts) elsewhere. No fixed heights. */
object PpType {
    private val base = Typography()

    private fun TextStyle.serif() = copy(fontFamily = FontFamily.Serif)
    private fun TextStyle.sans() = copy(fontFamily = FontFamily.Default)

    val typography: Typography = Typography(
        displayLarge = base.displayLarge.serif(),
        displayMedium = base.displayMedium.serif(),
        displaySmall = base.displaySmall.serif(),
        headlineLarge = base.headlineLarge.serif(),
        headlineMedium = base.headlineMedium.serif(),
        headlineSmall = base.headlineSmall.serif(),
        titleLarge = base.titleLarge.sans(),
        titleMedium = base.titleMedium.sans(),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans().copy(fontSize = 16.sp, lineHeight = 1.4.em),
        bodyMedium = base.bodyMedium.sans().copy(fontSize = 16.sp, lineHeight = 1.4.em),
        bodySmall = base.bodySmall.sans().copy(fontSize = 14.sp, lineHeight = 1.4.em), // captions only
        labelLarge = base.labelLarge.sans(),
        labelMedium = base.labelMedium.sans(),
        labelSmall = base.labelSmall.sans(),
    )

    /** Shlokas and mantras: generous line height for stacked Indic marks. */
    val sanskrit: TextStyle = typography.bodyLarge.copy(lineHeight = 1.75.em)
}
