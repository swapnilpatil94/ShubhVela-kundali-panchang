@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code

package app.parivaar.panchang.feature.aaj.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.feature.aaj.R
import app.parivaar.panchang.feature.aaj.share.ShareCardModel
import app.parivaar.panchang.feature.aaj.share.ShareFormat
import app.parivaar.panchang.feature.aaj.speech.SpeechUi
import app.parivaar.panchang.feature.aaj.travel.TravelUi

/** Travel, read-aloud and share hooks supplied by the shell. [None] is for previews. */
class AajExtras(
    val travel: TravelUi = TravelUi.Idle,
    val onTravelUpdate: () -> Unit = {},
    val onTravelDismiss: () -> Unit = {},
    val onRationaleContinue: () -> Unit = {},
    val onBackHome: () -> Unit = {},
    val speech: SpeechUi = SpeechUi.Checking,
    val onSpeak: (List<String>) -> Unit = {},
    val onStopSpeaking: () -> Unit = {},
    val showCityOnShare: Boolean = false,
    val cityName: String? = null,
    val onShare: (ShareFormat, ShareCardModel) -> Unit = { _, _ -> },
) {
    companion object {
        val None = AajExtras()
    }
}

@Composable
internal fun TravelSection(travel: TravelUi, extras: AajExtras) {
    when (travel) {
        TravelUi.Idle -> Unit
        TravelUi.Offer -> TravelCard(
            stringResource(R.string.aaj_travel_offer_title), stringResource(R.string.aaj_travel_offer_body),
            stringResource(R.string.aaj_travel_update) to extras.onTravelUpdate,
            stringResource(R.string.aaj_travel_not_now) to extras.onTravelDismiss,
        )
        TravelUi.Rationale -> AlertDialog(
            onDismissRequest = extras.onTravelDismiss,
            title = { Text(stringResource(R.string.aaj_travel_rationale_title)) },
            text = { Text(stringResource(R.string.aaj_travel_rationale_body)) },
            confirmButton = { TextButton(extras.onRationaleContinue) { Text(stringResource(R.string.aaj_travel_continue)) } },
            dismissButton = { TextButton(extras.onTravelDismiss) { Text(stringResource(R.string.aaj_travel_cancel)) } },
        )
        TravelUi.Locating -> TravelCard(stringResource(R.string.aaj_travel_locating), null)
        is TravelUi.InPlace -> TravelCard(
            stringResource(R.string.aaj_travel_in_place, travel.name), null,
            stringResource(R.string.aaj_travel_back_home) to extras.onBackHome,
        )
        TravelUi.Denied -> TravelMessage(R.string.aaj_travel_denied, extras)
        TravelUi.NoFix -> TravelMessage(R.string.aaj_travel_no_fix, extras)
        TravelUi.NoPlaceNearby -> TravelMessage(R.string.aaj_travel_no_place, extras)
    }
}

@Composable
private fun TravelMessage(res: Int, extras: AajExtras) =
    TravelCard(stringResource(res), null, stringResource(R.string.aaj_travel_dismiss) to extras.onTravelDismiss)

@Composable
private fun TravelCard(
    title: String,
    body: String?,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            primary?.let { PpButton(it.first, it.second, PpButtonStyle.Tonal) }
            secondary?.let { PpButton(it.first, it.second, PpButtonStyle.Outline) }
        }
    }
}

/** Read-aloud (only with an offline voice) and share. */
@Composable
internal fun ActionsRow(extras: AajExtras, script: () -> List<String>, card: () -> ShareCardModel) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
        when (extras.speech) {
            SpeechUi.Ready -> PpButton(stringResource(R.string.aaj_speak), { extras.onSpeak(script()) }, PpButtonStyle.Outline)
            SpeechUi.Speaking -> {
                val state = stringResource(R.string.aaj_speak_state_speaking)
                PpButton(
                    stringResource(R.string.aaj_speak_stop), extras.onStopSpeaking, PpButtonStyle.Tonal,
                    modifier = Modifier.semantics { stateDescription = state },
                )
            }
            SpeechUi.Unavailable -> Text(
                stringResource(R.string.aaj_speak_unavailable), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpeechUi.Checking -> Unit
        }
        PpButton(stringResource(R.string.aaj_share), { choosing = true }, PpButtonStyle.Outline)
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.aaj_share_title)) },
            text = {
                Column {
                    TextButton({ choosing = false; extras.onShare(ShareFormat.SQUARE_1080, card()) }) {
                        Text(stringResource(R.string.aaj_share_square))
                    }
                    TextButton({ choosing = false; extras.onShare(ShareFormat.STORY_1080x1920, card()) }) {
                        Text(stringResource(R.string.aaj_share_story))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ choosing = false }) { Text(stringResource(R.string.aaj_travel_cancel)) } },
        )
    }
}
