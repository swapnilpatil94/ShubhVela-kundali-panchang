@file:Suppress("MagicNumber")
package app.parivaar.panchang.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/** Scale 4, 8, 10, 12, 14, 16, 24 (DESIGN_V2); xl/xxxl are M07 leftovers kept for existing call sites. */
object PpSpace {
    val xs = 4.dp
    val s = 8.dp
    val s10 = 10.dp
    val m = 12.dp
    val s14 = 14.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    /** Horizontal screen padding. */
    val screen = 16.dp
    val minTouchTarget = 48.dp
}
