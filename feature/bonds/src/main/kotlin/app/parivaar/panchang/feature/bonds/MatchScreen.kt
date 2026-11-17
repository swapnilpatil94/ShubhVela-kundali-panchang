package app.parivaar.panchang.feature.bonds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.SectionEyebrow
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType

/** F1 marriage matching: two people with the groom and bride roles named explicitly (no axes or bond label). */
@Composable
fun MatchScreen(m: MatchUi, onEvent: (BondsEvent) -> Unit, onBack: () -> Unit) {
    val you = stringResource(R.string.bonds_result_you)
    BondsPage(stringResource(R.string.bonds_match_title), onBack) {
        Text(stringResource(R.string.bonds_match_body), style = PpType.body, color = PpTheme.ratna.text2)
        Picker(stringResource(R.string.bonds_match_first), m.candidates, m.a,
            you) { onEvent(BondsEvent.MatchPick(it, m.b, m.roles)) }
        Picker(stringResource(R.string.bonds_match_second), m.candidates, m.b,
            you) { onEvent(BondsEvent.MatchPick(m.a, it, m.roles)) }
        SectionEyebrow(stringResource(R.string.bonds_match_groom))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            MatchRoles.entries.forEach { role ->
                val label =
                    if (role == MatchRoles.A_GROOM) {
                        R.string.bonds_match_first_groom
                    } else {
                        R.string.bonds_match_second_groom
                    }
                PpButton(
                    stringResource(label), { onEvent(BondsEvent.MatchPick(m.a, m.b, role)) },
                    if (role == m.roles) PpButtonStyle.Primary else PpButtonStyle.Tonal,
                )
            }
        }
        val res = m.result
        if (res == null) {
            Text(stringResource(R.string.bonds_match_pick_two), style = PpType.meta, color = PpTheme.ratna.text3)
        } else {
            ResultBody(res)
        }
    }
}

@Composable
private fun Picker(title: String, all: List<MatchCandidate>, chosen: String?, you: String, onPick: (String) -> Unit) {
    SectionEyebrow(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
        all.forEach { c ->
            PpButton(c.name ?: you, { onPick(c.key) },
                if (c.key == chosen) PpButtonStyle.Primary else PpButtonStyle.Tonal)
        }
    }
}
