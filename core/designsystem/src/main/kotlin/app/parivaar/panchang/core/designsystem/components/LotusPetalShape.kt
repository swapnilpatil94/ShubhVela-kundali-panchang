package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.shape.GenericShape

/** Hero brand mark only. */
val LotusPetalShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w / 2f, 0f)
    quadraticTo(w, 0.55f * h, w / 2f, h)
    quadraticTo(0f, 0.55f * h, w / 2f, 0f)
    close()
}
