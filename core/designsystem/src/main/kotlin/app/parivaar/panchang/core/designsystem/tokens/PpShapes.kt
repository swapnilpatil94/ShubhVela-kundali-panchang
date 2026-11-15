@file:Suppress("MagicNumber", "MaxLineLength")
package app.parivaar.panchang.core.designsystem.tokens

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radii: chip 12, lacquer 20, tile 24, sheet top 30, dock 32, pill 50 %. Corner sizes are direction-neutral (RTL-safe). */
object PpShapes {
    val chip = RoundedCornerShape(12.dp)
    val lacquer = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(24.dp)
    val dock = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(CornerSize(50))
    /** Top corners use start/end semantics, so the shape is RTL-safe. */
    val sheetTop = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

    // M07 names, kept as aliases for existing call sites.
    val small = chip
    val medium = lacquer
    val large = tile
    val extraLarge = RoundedCornerShape(30.dp)

    val material: Shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = chip,
        medium = lacquer,
        large = tile,
        extraLarge = extraLarge,
    )
}
