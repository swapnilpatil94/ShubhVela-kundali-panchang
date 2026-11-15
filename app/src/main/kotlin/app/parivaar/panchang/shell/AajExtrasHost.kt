package app.parivaar.panchang.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.feature.aaj.ui.AajExtras

/** Connects the Today tab to travel mode, read-aloud and sharing. Call only while the shell is shown. */
@Composable
fun rememberAajExtras(vm: ShellViewModel, settings: AppSettings, homeName: String?): AajExtras {
    val travel by vm.travel.ui.collectAsStateWithLifecycle()
    val travelPlace by vm.travel.place.collectAsStateWithLifecycle()
    val speech by vm.speaker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.speaker.ensureReady() }
    return AajExtras(
        travel = travel,
        onTravelUpdate = vm.travel::onOfferTapped,
        onTravelDismiss = vm.travel::dismiss,
        onRationaleContinue = vm.travel::continueFromRationale,
        onBackHome = vm.travel::backToHome,
        speech = speech,
        onSpeak = vm.speaker::speak,
        onStopSpeaking = vm.speaker::stop,
        showCityOnShare = settings.showCityOnWidgets,
        cityName = travelPlace?.name ?: homeName,
        onShare = { format, model -> vm.share(context, format, model) },
    )
}
