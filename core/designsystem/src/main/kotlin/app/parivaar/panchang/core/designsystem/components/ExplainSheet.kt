@file:Suppress("MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.parivaar.panchang.core.designsystem.surfaces.GlassSurface
import app.parivaar.panchang.core.designsystem.surfaces.LocalGlassBackdrop
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

data class ProofInfo(val backend: String, val ayanamsa: String, val sunrise: String, val altitudeM: Double?)

/** "Why this?" sheet with a proof panel (OVERVIEW C4). The footer is mandatory for interpretations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainSheet(
    title: String,
    explanation: String,
    howCalculated: String,
    traditionNote: String?,
    proof: ProofInfo?,
    onChangeConventions: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = PpShapes.sheetTop,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        // Glass sheet: chrome. Solid ink3 plus gold hairline when there is no backdrop or Reduce Transparency is on.
        GlassSurface(LocalGlassBackdrop.current, PpShapes.sheetTop, Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = PpSpace.xxl, vertical = PpSpace.s),
            verticalArrangement = Arrangement.spacedBy(PpSpace.l),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(explanation, style = MaterialTheme.typography.bodyLarge)
            Section(stringResource(R.string.explain_how_calculated), howCalculated)
            if (traditionNote != null) Section(stringResource(R.string.explain_tradition_note), traditionNote)
            if (proof != null) ProofPanel(proof)
            PpButton(stringResource(R.string.explain_change_conventions), onChangeConventions, PpButtonStyle.Outline)
            PpButton(stringResource(R.string.explain_close), onDismiss, PpButtonStyle.Tonal)
            Text(
                stringResource(R.string.explain_footer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
    }
}

@Composable
private fun Section(heading: String, body: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
        Text(
            heading, style = MaterialTheme.typography.titleMedium, color = PpTheme.colors.primaryText,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProofPanel(proof: ProofInfo) {
    PpCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.explain_proof_title), style = MaterialTheme.typography.titleMedium,
            color = PpTheme.colors.primaryText, modifier = Modifier.semantics { heading() },
        )
        ProofRow(stringResource(R.string.explain_proof_backend), proof.backend)
        ProofRow(stringResource(R.string.explain_proof_ayanamsa), proof.ayanamsa)
        ProofRow(stringResource(R.string.explain_proof_sunrise), proof.sunrise)
        proof.altitudeM?.let {
            ProofRow(
                stringResource(R.string.explain_proof_altitude),
                stringResource(R.string.explain_altitude_value, it),
            )
        }
    }
}

@Composable
private fun ProofRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = PpSpace.s), horizontalArrangement = Arrangement.spacedBy(PpSpace.m)) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
