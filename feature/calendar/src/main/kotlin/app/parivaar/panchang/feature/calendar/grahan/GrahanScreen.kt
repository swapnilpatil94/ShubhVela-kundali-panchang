@file:Suppress("MaxLineLength", "MatchingDeclarationName", "MagicNumber") // Compose layout code and preview data

package app.parivaar.panchang.feature.calendar.grahan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.engine.calendar.Contact
import app.parivaar.panchang.engine.calendar.EclipseKind
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.feature.aaj.ui.AajNames
import app.parivaar.panchang.feature.calendar.GlobalWithLocal
import app.parivaar.panchang.feature.calendar.R
import app.parivaar.panchang.feature.calendar.common.CalFormat
import app.parivaar.panchang.feature.calendar.common.ScreenFrame
import app.parivaar.panchang.feature.calendar.common.previewFormat
import app.parivaar.panchang.feature.calendar.common.previewNames
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import kotlin.time.Instant

/** One eclipse row. [local] is null or has no visible span when it cannot be seen from the place (shown greyed). */
data class GrahanItem(
    val kind: EclipseKind?,
    val solar: Boolean,
    val greatest: Instant,
    val magnitude: Double?,
    val contacts: List<Pair<Contact, Instant>>,
    val visible: TimeRange?,
    val sutak: TimeRange?,
    val seenHere: Boolean,
)

/** Pure mapping of the engine year list: local values when the place sees it, global greatest time otherwise. */
object GrahanItems {
    fun of(list: List<GlobalWithLocal>): List<GrahanItem> = list.map { gl ->
        val l = gl.local
        val g0 = gl.global
        val seen = l != null && l.visible != null
        if (l != null) {
            GrahanItem(
                l.kind, g0.solar, l.maxAt, l.magnitude, l.contacts.entries.sortedBy { it.value }.map { it.key to it.value },
                l.visible, l.sutak, seen,
            )
        } else {
            val g = gl.global
            GrahanItem(g.lunarKind, g.solar, g.greatest, null, emptyList(), null, null, false)
        }
    }.sortedBy { it.greatest }
}

/** Stateless eclipse list for one year (Dest.Grahan). Year changes are reported through [onYear]. */
@Composable
fun GrahanScreen(
    year: Int,
    items: List<GrahanItem>,
    names: AajNames,
    fmt: CalFormat,
    onYear: (Int) -> Unit,
    onBack: () -> Unit,
) {
    ScreenFrame(stringResource(R.string.grahan_title), stringResource(R.string.guide_back), onBack) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onYear(year - 1) }, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget)) {
                Text(stringResource(R.string.grahan_prev_year))
            }
            Text(stringResource(R.string.grahan_year, year), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onYear(year + 1) }, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget)) {
                Text(stringResource(R.string.grahan_next_year))
            }
        }
        if (items.isEmpty()) Text(stringResource(R.string.grahan_empty))
        for (it in items) EclipseCard(it, names, fmt)
        if (items.any { it.sutak != null }) {
            Text(stringResource(R.string.grahan_footer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun title(e: GrahanItem, names: AajNames): String = when {
    e.kind != null -> names.eclipse(e.kind)
    e.solar -> stringResource(R.string.grahan_solar_generic)
    else -> stringResource(R.string.grahan_lunar_generic)
}

@Composable
private fun EclipseCard(e: GrahanItem, names: AajNames, fmt: CalFormat) {
    val tone = if (e.seenHere) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    PpCard(Modifier.fillMaxWidth()) {
        Text(title(e, names), style = MaterialTheme.typography.titleSmall, color = tone)
        Text(stringResource(R.string.grahan_greatest, fmt.dateTime(e.greatest)), color = tone)
        e.magnitude?.let { Text(stringResource(R.string.grahan_magnitude, fmt.number(it, 2)), color = tone) }
        if (!e.seenHere) {
            Text(stringResource(R.string.grahan_not_visible), color = tone, style = MaterialTheme.typography.bodyMedium)
        } else {
            for ((c, at) in e.contacts) Text(stringResource(R.string.grahan_contact, c.name, fmt.time(at)))
            e.visible?.let {
                Text(stringResource(R.string.grahan_visible, stringResource(R.string.guide_range, fmt.time(it.start), fmt.time(it.end))))
            }
            e.sutak?.let {
                Text(stringResource(R.string.grahan_sutak, stringResource(R.string.guide_range, fmt.dateTime(it.start), fmt.dateTime(it.end))))
            }
        }
    }
}

@PpPreviews
@Composable
private fun GrahanPreview() = PanchangTheme {
    val t = Instant.parse("2026-08-28T04:00:00Z")
    val items = listOf(
        GrahanItem(EclipseKind.LUNAR_PARTIAL, false, t, 0.93, listOf(Contact.P1 to t), TimeRange(t, t), null, true),
        GrahanItem(null, true, Instant.parse("2026-08-12T17:00:00Z"), null, emptyList(), null, null, false),
    )
    GrahanScreen(
        2026, items, previewNames(),
        previewFormat(), {}, {},
    )
}
