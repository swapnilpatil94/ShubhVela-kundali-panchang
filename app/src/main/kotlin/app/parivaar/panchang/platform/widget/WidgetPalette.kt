@file:Suppress("MagicNumber", "MaxLineLength")

package app.parivaar.panchang.platform.widget

import app.parivaar.panchang.core.designsystem.tokens.adjustForContrast
import app.parivaar.panchang.core.designsystem.tokens.gemFor
import kotlinx.datetime.DayOfWeek

/**
 * Ratna colours for RemoteViews, which cannot use Compose. Dark = Ratna Night lacquer, light = Chandan.
 * The static roles mirror `values/colors_widget.xml` and `values-night/colors_widget.xml`; only the weekday gem
 * is applied at run time (`setTextColor`/`setInt`), after being nudged to AA contrast on the card.
 */
internal data class WidgetPalette(
    val card: Long,
    val hairline: Long,
    val text: Long,
    val textDim: Long,
    val gold: Long,
    /** Weekday gem tinted to at least 4.5:1 on [card]; use for the key line and the accent bar. */
    val gem: Long,
)

/** Opaque colour the translucent night card is checked against (the card alpha only lets the wallpaper darken or lighten it). */
internal const val NIGHT_CARD_OPAQUE = 0xFF0E1222L
internal const val NIGHT_CARD = 0xE00E1222L
internal const val CHANDAN_CARD = 0xFFFFFBF2L

internal fun widgetPalette(dark: Boolean, weekday: DayOfWeek): WidgetPalette {
    val base = if (dark) NIGHT_CARD_OPAQUE else CHANDAN_CARD
    val gem = adjustForContrast(0xFF000000L or gemFor(weekday).argb, listOf(base), darken = !dark)
    return if (dark) {
        WidgetPalette(NIGHT_CARD, 0x29EDE6D6L, 0xFFEDE6D6L, 0xFFB8AF9CL, 0xFFE7C85AL, gem)
    } else {
        WidgetPalette(CHANDAN_CARD, 0xFFE3CFA2L, 0xFF2A1A12L, 0xFF5E5145L, 0xFF8C6214L, gem)
    }
}
