package app.parivaar.panchang.feature.bonds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType

/** A pushed Bonds page: back action, eyebrow and title, scrolling content. */
@Composable
internal fun BondsPage(
    title: String,
    onBack: () -> Unit,
    eyebrow: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val r = PpTheme.ratna
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PpSpace.screen),
        verticalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        TextButton(onBack, Modifier.heightIn(min = PpSpace.minTouchTarget)) {
            Text(stringResource(R.string.bonds_back), style = PpType.body, color = r.gold)
        }
        eyebrow?.let { Text(it.uppercase(), style = PpType.eyebrow, color = r.text3) }
        Text(title, style = PpType.displayL, color = r.text, modifier = Modifier.semantics { heading() })
        content()
        Footer()
    }
}

@Composable
internal fun Footer() {
    Text(
        stringResource(R.string.bonds_footer), style = PpType.meta, color = PpTheme.ratna.text3,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = PpSpace.xxl),
    )
}

@Composable
internal fun FieldRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PpType.meta, color = PpTheme.ratna.text2, modifier = Modifier.weight(1f))
        Text(value, style = PpType.body, color = PpTheme.ratna.text)
    }
}

/** Kind picker: one tonal chip per bond type, the chosen one gold. */
@Composable
internal fun KindPicker(selected: BondKind, onPick: (BondKind) -> Unit) {
    val r = PpTheme.ratna
    Text(stringResource(R.string.bonds_kind_label), style = PpType.eyebrow, color = r.text3)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
        BondKind.entries.forEach { k ->
            app.parivaar.panchang.core.designsystem.components.PpButton(
                stringResource(BondTexts.kind(k)), { onPick(k) },
                if (k == selected) {
                    app.parivaar.panchang.core.designsystem.components.PpButtonStyle.Primary
                } else {
                    app.parivaar.panchang.core.designsystem.components.PpButtonStyle.Tonal
                },
            )
        }
    }
}
