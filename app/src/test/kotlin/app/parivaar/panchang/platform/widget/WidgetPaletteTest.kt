package app.parivaar.panchang.platform.widget

import app.parivaar.panchang.core.designsystem.tokens.contrastRatio
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetPaletteTest {
    @Test fun everyTextRoleIsAaOnItsCard() {
        for (dark in listOf(true, false)) {
            for (day in DayOfWeek.entries) {
                val p = widgetPalette(dark, day)
                val card = if (dark) NIGHT_CARD_OPAQUE else p.card
                for ((name, fg) in listOf("text" to p.text, "dim" to p.textDim, "gold" to p.gold, "gem" to p.gem)) {
                    assertTrue(contrastRatio(fg, card) >= 4.5, "$name dark=$dark $day ${contrastRatio(fg, card)}")
                }
            }
        }
    }

    @Test fun gemFollowsWeekday() {
        assertEquals(widgetPalette(true, DayOfWeek.THURSDAY).gem, widgetPalette(true, DayOfWeek.THURSDAY).gem)
        assertTrue(widgetPalette(true, DayOfWeek.SUNDAY).gem != widgetPalette(true, DayOfWeek.SATURDAY).gem)
    }
}
