package app.parivaar.panchang.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

/** The static privacy promise. Shared by the full screen and the sheet. */
@Composable
fun PrivacyContent(modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(PpSpace.l),
        verticalArrangement = Arrangement.spacedBy(PpSpace.l),
    ) {
        ScreenTitle(R.string.privacy_title)
        Text(
            stringResource(R.string.privacy_promise), style = MaterialTheme.typography.titleMedium,
            color = PpTheme.ratna.text,
        )
        listOf(
            R.string.privacy_point_device,
            R.string.privacy_point_offline,
            R.string.privacy_point_share,
            R.string.privacy_point_location,
            R.string.privacy_point_logs,
        ).forEach {
            PpCard(Modifier.fillMaxWidth()) {
                Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = PpTheme.ratna.text)
            }
        }
    }
}

/** Settings → Privacy (full screen; system back leaves it). */
@Composable
fun PrivacyScreen(modifier: Modifier = Modifier) = PrivacyContent(modifier)

/** The same promise as a bottom sheet, for the onboarding footer link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, shape = PpShapes.sheetTop, containerColor = PpTheme.ratna.ink3,
        contentColor = PpTheme.ratna.text, tonalElevation = 0.dp,
    ) {
        PrivacyContent()
    }
}

@PpPreviews
@Composable
private fun PrivacyPreview() = PanchangTheme { PrivacyScreen() }
