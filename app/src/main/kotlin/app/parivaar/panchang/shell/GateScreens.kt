package app.parivaar.panchang.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.R
import app.parivaar.panchang.core.designsystem.components.ErrorState
import app.parivaar.panchang.core.designsystem.components.LoadingState

@Composable
fun LoadingGate(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingState(stringResource(R.string.gate_loading))
    }
}

/**
 * Key lost: the data cannot be read here. Recovery is the user's choice; "Start fresh" always goes through a
 * confirm dialog and both actions are disabled while [busy] (single-flight in [AppGateModel]).
 */
@Composable
fun KeyLostGate(
    busy: Boolean,
    onStartFreshConfirmed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ObscuredTouchGuard()
    var confirming by rememberSaveable { mutableStateOf(false) }
    GateBody(modifier) {
        ErrorState(
            title = stringResource(R.string.gate_key_lost_title),
            body = stringResource(R.string.gate_key_lost_body),
            actions =
                if (busy) {
                    emptyList()
                } else {
                    listOf(
                        stringResource(R.string.gate_start_fresh) to { confirming = true },
                        stringResource(R.string.gate_close_app) to onClose,
                    )
                },
        )
    }
    if (confirming && !busy) {
        ConfirmStartFresh(
            onConfirm = {
                confirming = false
                onStartFreshConfirmed()
            },
            onCancel = { confirming = false },
        )
    }
}

@Composable
fun KeyUnavailableGate(
    busy: Boolean,
    onRetry: () -> Unit,
    onStartFreshConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ObscuredTouchGuard()
    var confirming by rememberSaveable { mutableStateOf(false) }
    GateBody(modifier) {
        ErrorState(
            title = stringResource(R.string.gate_key_unavailable_title),
            body = stringResource(R.string.gate_key_unavailable_body),
            actions =
                if (busy) {
                    emptyList()
                } else {
                    listOf(
                        stringResource(R.string.gate_try_again) to onRetry,
                        stringResource(R.string.gate_start_fresh) to { confirming = true },
                    )
                },
        )
    }
    if (confirming && !busy) {
        ConfirmStartFresh(
            onConfirm = {
                confirming = false
                onStartFreshConfirmed()
            },
            onCancel = { confirming = false },
        )
    }
}

@Composable
private fun GateBody(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { content() }
}

/** Ignores taps while another window covers this one (tap-jacking guard for the destructive button). */
@Composable
private fun ObscuredTouchGuard() {
    val view = LocalView.current
    SideEffect { view.filterTouchesWhenObscured = true }
}

@Composable
private fun ConfirmStartFresh(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.gate_confirm_title)) },
        text = {
            ObscuredTouchGuard()
            Text(stringResource(R.string.gate_confirm_body))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.gate_confirm_yes)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.gate_confirm_cancel)) } },
    )
}
