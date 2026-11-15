@file:Suppress("MagicNumber")
package app.parivaar.panchang.core.designsystem.tokens

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object PpShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(20.dp) // cards
    val large = RoundedCornerShape(24.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    /** Top corners use start/end semantics, so the shape is RTL-safe. */
    val sheetTop = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
    val pill = RoundedCornerShape(CornerSize(50))

    val material: Shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge,
    )
}
