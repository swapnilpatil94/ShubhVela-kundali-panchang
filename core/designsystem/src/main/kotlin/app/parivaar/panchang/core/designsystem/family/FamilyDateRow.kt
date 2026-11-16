package app.parivaar.panchang.core.designsystem.family

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType

/**
 * One upcoming family date (DESIGN_V2 `FamilyDateRow`, PNG 16) on a Lacquer card: title, detail line and a
 * days-until pill. [soon] tints the pill gold (within a week). All text arrives formatted from resources.
 */
@Composable
fun FamilyDateRow(
    title: String,
    detail: String?,
    daysText: String,
    soon: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val r = PpTheme.ratna
    Lacquer(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = PpType.body.copy(fontWeight = FontWeight.Bold), color = r.text)
                if (detail != null) Text(detail, style = PpType.meta, color = r.text2)
            }
            Spacer(Modifier.width(PpSpace.s))
            Text(
                daysText,
                style = PpType.label,
                color = if (soon) r.gold else r.text2,
                modifier = Modifier.border(1.dp, if (soon) r.goldFill else r.line, PpShapes.pill)
                    .padding(horizontal = PpSpace.m, vertical = PpSpace.s),
            )
        }
    }
}

