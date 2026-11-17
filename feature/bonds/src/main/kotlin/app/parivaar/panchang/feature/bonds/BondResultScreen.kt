package app.parivaar.panchang.feature.bonds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.BondAxisBar
import app.parivaar.panchang.core.designsystem.components.BondOrbit
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.LoadingState
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.SectionEyebrow
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType

/** Screen 18: score ring, kind label, axes ("growth area" below 5), koota breakdown, Manglik note, footer. */
@Composable
fun BondResultScreen(r: ResultUi, deleteFailed: Boolean, onEvent: (BondsEvent) -> Unit, onBack: () -> Unit) {
    val you = stringResource(R.string.bonds_result_you)
    BondsPage(
        stringResource(R.string.bonds_result_pair, r.nameA ?: you, r.nameB), onBack,
        eyebrow = stringResource(R.string.bonds_result_eyebrow, stringResource(BondTexts.kind(r.kind))),
    ) {
        if (r.bondId != null) KindPicker(r.kind) { onEvent(BondsEvent.ChangeKind(it)) }
        ResultBody(r)
        if (r.bondId != null) {
            if (!r.cardShareAllowed) {
                Text(stringResource(R.string.bonds_card_share_family_note), style = PpType.meta,
                    color = PpTheme.ratna.text3)
            }
            if (deleteFailed) Text(stringResource(R.string.bonds_delete_failed), style = PpType.body,
                color = PpTheme.ratna.kumkum)
            PpButton(
                stringResource(R.string.bonds_delete), { onEvent(BondsEvent.Delete(r.bondId)) }, PpButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.bonds_delete_note), style = PpType.meta, color = PpTheme.ratna.text3)
        }
    }
}

/** The result content, shared with the marriage match page. */
@Composable
internal fun ResultBody(r: ResultUi) {
    val c = PpTheme.ratna
    when (r.status) {
        ResultStatus.LOADING -> LoadingState(stringResource(R.string.bonds_working))
        ResultStatus.NO_SELF ->
            EmptyState(stringResource(R.string.bonds_result_no_self_title),
                stringResource(R.string.bonds_result_no_self_body))
        ResultStatus.UNAVAILABLE -> Text(stringResource(R.string.bonds_err_unavailable), style = PpType.body,
            color = c.text2)
        ResultStatus.SHOWN -> r.outcome?.let { o ->
            val score = BondTexts.gunas(o.totalQ)
            val max = BondTexts.gunas(o.maxQ)
            BondOrbit(
                score, stringResource(R.string.bonds_result_gunas, max),
                stringResource(R.string.bonds_result_score_desc, score, max),
            )
            BondTexts.label(o.labelKey)?.let {
                Text(
                    stringResource(it), style = PpType.displayL, color = c.gold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (o.axes.isNotEmpty()) {
                Lacquer(Modifier.fillMaxWidth(), mergeSemantics = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                        o.axes.forEach { (axis, v) ->
                            val name = stringResource(BondTexts.axis(axis))
                            val growth = v < GROWTH_BELOW
                            BondAxisBar(if (growth) stringResource(R.string.bonds_growth_area, name) else name, v,
                                growth)
                        }
                    }
                }
            }
            if (o.kootas.any { it.excluded }) {
                Text(stringResource(R.string.bonds_friend_excludes_note), style = PpType.meta, color = c.text2)
            }
            SectionEyebrow(stringResource(R.string.bonds_result_kootas))
            Lacquer(Modifier.fillMaxWidth(), mergeSemantics = false) {
                Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) { o.kootas.forEach { KootaRow(it) } }
            }
            Text(stringResource(R.string.bonds_result_base_rule), style = PpType.meta, color = c.text3)
            BondTexts.manglik(r.manglikKey)?.let {
                Lacquer(Modifier.fillMaxWidth()) {
                    Text(stringResource(it), style = PpType.body, color = c.text)
                    Text(stringResource(R.string.bonds_result_variant), style = PpType.meta, color = c.text3)
                }
            }
            if (o.tableStatus != "APPROVED") {
                Text(stringResource(R.string.bonds_result_tables_verify), style = PpType.meta, color = c.text3)
            }
        }
    }
}

@Composable
private fun KootaRow(k: KootaLine) {
    val c = PpTheme.ratna
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(BondTexts.koota(k.key)), style = PpType.body, color = c.text,
                modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.bonds_result_koota_value, BondTexts.gunas(k.q), BondTexts.gunas(k.maxQ)),
                style = PpType.body, color = if (k.excluded) c.text3 else c.text,
            )
        }
        if (k.excluded) Text(stringResource(R.string.bonds_result_excluded), style = PpType.meta, color = c.text3)
        k.noteIds.distinctBy { BondTexts.note(it) }.forEach {
            Text(stringResource(BondTexts.note(it)), style = PpType.meta, color = c.text2)
        }
    }
}

private const val GROWTH_BELOW = 5
