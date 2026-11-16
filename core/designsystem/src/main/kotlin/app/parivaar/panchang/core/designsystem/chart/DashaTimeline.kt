package app.parivaar.panchang.core.designsystem.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import kotlinx.datetime.LocalDate

/** Indent per dasha level (maha flush left). */
private val LEVEL_INDENT = 14.dp

/**
 * Vimshottari rows to sookshma (M10 rule 5). Current rows are gold with a dot; tapping an expandable row calls
 * [onToggle] with its lord path. Dates come pre-formatted by [formatDate] (home zone, date only).
 */
@Composable
fun DashaTimeline(
    rows: List<DashaRow>,
    names: (Int) -> String,
    onToggle: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    formatDate: (LocalDate) -> String = { it.toString() },
) {
    val r = PpTheme.ratna
    val expandCd = stringResource(R.string.dasha_cd_expand)
    val collapseCd = stringResource(R.string.dasha_cd_collapse)
    val currentText = stringResource(R.string.dasha_current)
    Column(modifier.fillMaxWidth()) {
        for (row in rows) {
            val title = row.lords.joinToString(" › ") { names(it) }
            val range = stringResource(R.string.dasha_range, formatDate(row.start), formatDate(row.end))
            val color = if (row.current) r.gold else r.text
            var m = Modifier.fillMaxWidth().heightIn(min = PpSpace.minTouchTarget)
            if (row.expandable) {
                val label = if (row.expanded) collapseCd else expandCd
                m = m.clickable(role = Role.Button, onClickLabel = label) { onToggle(row.lords) }
            }
            Row(
                m.padding(start = LEVEL_INDENT * (row.level - 1), top = PpSpace.xs, bottom = PpSpace.xs)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$title, $range"
                        if (row.current) stateDescription = currentText
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(if (row.current) r.goldFill else r.line, CircleShape))
                Spacer(Modifier.width(PpSpace.s10))
                Column(Modifier.weight(1f)) {
                    Text(title, style = if (row.level == 1) PpType.displayS else PpType.body, color = color)
                    Text(range, style = PpType.meta, color = r.text2)
                }
                if (row.expandable) {
                    Text(if (row.expanded) "−" else "+", style = PpType.body, color = r.text3)
                }
            }
        }
    }
}
