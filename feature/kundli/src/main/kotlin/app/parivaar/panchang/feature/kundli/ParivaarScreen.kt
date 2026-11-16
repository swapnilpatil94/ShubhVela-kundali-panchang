package app.parivaar.panchang.feature.kundli

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.family.FamilyAvatars
import app.parivaar.panchang.core.designsystem.family.FamilyDateRow
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.model.profile.Relation

/** Title of an upcoming row, for example "Maa · tithi birthday" (on screen only, never in notifications). */
@Composable
fun familyTitle(row: UpcomingRow, me: String): String {
    val who = if (row.profile.relation == Relation.SELF) me else row.profile.displayName
    val kind = stringResource(
        when (row.item.kind) {
            FamilyKind.TITHI_BIRTHDAY -> R.string.family_kind_tithi
            FamilyKind.NAKSHATRA_BIRTHDAY -> R.string.family_kind_nakshatra
            FamilyKind.SHRADDHA -> R.string.family_kind_shraddha
        },
    )
    return stringResource(R.string.family_row_title, who, kind)
}

/** "3 days" / "Today" for an upcoming row. */
@Composable
fun familyDaysText(row: UpcomingRow): String =
    if (row.daysUntil == 0) {
        stringResource(R.string.family_today)
    } else {
        pluralStringResource(R.plurals.family_in_days, row.daysUntil, row.daysUntil.toString())
    }

/** Parivaar (PNG 16): avatars, upcoming family dates with days-until, privacy line. No ads (shraddha is sacred). */
@Composable
fun ParivaarScreen(
    state: ParivaarState,
    onAdd: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    val ctx = LocalContext.current
    val names = remember(ctx) { KundliNames(ctx.resources) }
    val me = stringResource(R.string.kundli_me)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PpSpace.screen),
        verticalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        Spacer(Modifier.height(PpSpace.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(
                        R.plurals.parivaar_eyebrow, state.profiles.size, state.profiles.size.toString(),
                    ),
                    style = PpType.eyebrow, color = r.text2,
                )
                Text(
                    stringResource(R.string.parivaar_title), style = PpType.displayL, color = r.text,
                    modifier = Modifier.semantics { heading() },
                )
            }
            PpButton(stringResource(R.string.family_add_button), onAdd, PpButtonStyle.Outline)
        }
        if (state.profiles.isNotEmpty()) {
            Lacquer(mergeSemantics = false) { FamilyAvatars(avatarsOf(state.profiles, me), null, onOpenProfile) }
        }
        Text(stringResource(R.string.parivaar_upcoming), style = PpType.eyebrow, color = r.text3)
        for (n in state.notes.sortedBy { it.ordinal }) {
            Text(stringResource(familyNoteText(n)), style = PpType.meta, color = r.text2)
        }
        if (state.upcoming.isEmpty() && !state.loading) {
            Text(stringResource(R.string.parivaar_none), style = PpType.body, color = r.text2)
        }
        for (row in state.upcoming.take(MAX_ROWS)) {
            val i = row.item
            val detail = listOfNotNull(
                names.date(i.date),
                i.tithi?.let { t -> listOfNotNull(
                    i.label?.let { names.masa(it.masa.ordinal) }, names.tithi(t),
                ).joinToString(" ") },
                i.nakshatra?.let { names.nakshatra(it) },
            ).joinToString(" · ")
            val days = familyDaysText(row)
            FamilyDateRow(
                familyTitle(row, me), detail, days, soon = row.daysUntil <= SOON_DAYS,
                onClick = { onOpenProfile(row.profile.id.hex) },
            )
        }
        Text(stringResource(R.string.parivaar_privacy), style = PpType.meta, color = r.text3)
        Spacer(Modifier.height(BOTTOM_INSET))
    }
}

private fun familyNoteText(n: FamilyNote): Int = when (n) {
    FamilyNote.AYANAMSA_FALLBACK -> R.string.family_note_ayanamsa
    FamilyNote.AMBIGUOUS_BIRTH_DAY -> R.string.family_note_ambiguous
    FamilyNote.SOME_UNAVAILABLE -> R.string.family_note_unavailable
}

private const val MAX_ROWS = 30
private const val SOON_DAYS = 7
