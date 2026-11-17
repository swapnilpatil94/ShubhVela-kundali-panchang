package app.parivaar.panchang.feature.bonds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpTopBar
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.R as DsR

/** The Bonds tab root (M12): routes between the list, add, preview, result, share and marriage match pages. No ads. */
@Composable
fun BondsTab(
    state: BondsState,
    onEvent: (BondsEvent) -> Unit,
    subtitle: String?,
    avatarInitials: String?,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
) {
    val back = { onEvent(BondsEvent.Back) }
    when (state.page) {
        BondsPage.ADD -> AddBondScreen(state, onEvent, back)
        BondsPage.PREVIEW -> state.preview?.let { PreviewScreen(it, state.busy, state.addError, onEvent, back) }
        BondsPage.RESULT -> state.result?.let { BondResultScreen(it, state.deleteFailed, onEvent, back) }
        BondsPage.SHARE -> state.share?.let { ShareMyCodeScreen(it, onEvent, back) }
        BondsPage.MATCH -> MatchScreen(state.match, onEvent, back)
        BondsPage.LIST -> BondsListScreen(state, onEvent, subtitle, avatarInitials, onAvatar, onSearch)
    }
}

@Composable
fun BondsListScreen(
    state: BondsState,
    onEvent: (BondsEvent) -> Unit,
    subtitle: String?,
    avatarInitials: String?,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
) {
    val r = PpTheme.ratna
    Column(Modifier.fillMaxSize()) {
        PpTopBar(stringResource(DsR.string.tab_bonds), subtitle, avatarInitials, onAvatar, onSearch)
        if (state.locked) {
            EmptyState(stringResource(R.string.bonds_locked_title), stringResource(R.string.bonds_locked_body))
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PpSpace.screen),
            verticalArrangement = Arrangement.spacedBy(PpSpace.m),
        ) {
            PpButton(stringResource(R.string.bonds_add), { onEvent(BondsEvent.OpenAdd) },
                modifier = Modifier.fillMaxWidth())
            PpButton(
                stringResource(R.string.bonds_share_my_code), { onEvent(BondsEvent.OpenShare) }, PpButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
            PpButton(
                stringResource(R.string.bonds_match_open), { onEvent(BondsEvent.OpenMatch) }, PpButtonStyle.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.bonds.isEmpty()) {
                EmptyState(stringResource(R.string.bonds_empty_title), stringResource(R.string.bonds_empty_body))
            }
            state.bonds.forEach { b ->
                val desc = stringResource(R.string.bonds_open_bond, b.name)
                Lacquer(Modifier.fillMaxWidth().semantics { contentDescription = desc },
                    onClick = { onEvent(BondsEvent.Open(b.id)) }) {
                    Text(b.name, style = PpType.displayS, color = r.text)
                    Text(
                        stringResource(BondTexts.kind(b.kind)) + " · " +
                            stringResource(if (b.fromProfile) R.string.bonds_row_family else R.string.bonds_row_code),
                        style = PpType.meta, color = r.text2,
                    )
                }
            }
            Text(stringResource(R.string.bonds_privacy_note), style = PpType.meta, color = r.text3)
            Footer()
        }
    }
}
