package app.parivaar.panchang.feature.bonds

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.LoadingState
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.ToggleRow
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpType

/**
 * Share my Bond Code (rule 14): only the user's own profile; every field previewed; lagna off by default; initials
 * under 18. The text goes to the system share sheet (the user picks the app); copying marks the clip sensitive.
 */
@Composable
fun ShareMyCodeScreen(s: ShareUi, onEvent: (BondsEvent) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    BondsPage(stringResource(R.string.bonds_share_title), onBack) {
        when (s.status) {
            ShareStatus.NO_SELF -> EmptyState(
                stringResource(R.string.bonds_share_no_self_title), stringResource(R.string.bonds_share_no_self_body),
            )
            ShareStatus.LOADING -> LoadingState(stringResource(R.string.bonds_working))
            ShareStatus.UNAVAILABLE, ShareStatus.READY -> {
                Text(stringResource(R.string.bonds_share_body), style = PpType.body, color = PpTheme.ratna.text2)
                if (s.initialsForced) {
                    Text(stringResource(R.string.bonds_share_minor_note), style = PpType.meta,
                        color = PpTheme.ratna.text2)
                }
                s.traits?.let { t -> Lacquer(Modifier.fillMaxWidth(), mergeSemantics = false) { TraitFields(t) } }
                ToggleRow(stringResource(R.string.bonds_share_lagna_toggle), null, s.includeLagna,
                    { onEvent(BondsEvent.Lagna(it)) })
                if (s.status == ShareStatus.UNAVAILABLE) {
                    Text(stringResource(R.string.bonds_err_unavailable), style = PpType.body,
                        color = PpTheme.ratna.text2)
                }
                val code = s.code
                if (s.canShare && code != null) {
                    val text = stringResource(R.string.bonds_share_text, code)
                    val chooser = stringResource(R.string.bonds_share_chooser)
                    PpButton(stringResource(R.string.bonds_share_send), { shareText(ctx, text, chooser) },
                        modifier = Modifier.fillMaxWidth())
                    PpButton(
                        stringResource(if (copied) R.string.bonds_share_copied else R.string.bonds_share_copy),
                        { copySensitive(ctx,
                            code); copied = true }, PpButtonStyle.Outline, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun shareText(ctx: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copySensitive(ctx: Context, code: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("", code)
    val extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clip.description.extras = extras
    cm.setPrimaryClip(clip)
}
