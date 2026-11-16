package app.parivaar.panchang.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parivaar.panchang.core.model.settings.AppSettings
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.feature.aaj.ui.AajExtras
import app.parivaar.panchang.feature.aaj.ui.AajFamilyRow
import app.parivaar.panchang.feature.kundli.familyDaysText
import app.parivaar.panchang.feature.kundli.familyTitle
import app.parivaar.panchang.feature.kundli.R as KundliR

/** Aaj shows the next family date only within this many days (one card, no clutter). */
private const val AAJ_FAMILY_DAYS = 30
private const val SOON_DAYS = 7

/** Connects the Today tab to travel mode, read-aloud and sharing. Call only while the shell is shown. */
@Composable
fun rememberAajExtras(vm: ShellViewModel, settings: AppSettings, homeName: String?): AajExtras {
    val travel by vm.travel.ui.collectAsStateWithLifecycle()
    val travelPlace by vm.travel.place.collectAsStateWithLifecycle()
    val speech by vm.speaker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The app language (per-app locale) drives the voice: a language change re-initialises it.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { vm.speaker.ensureReady(locale) }
    val familyState by vm.kundliWiring.parivaar.state.collectAsStateWithLifecycle()
    val next = familyState.upcoming.firstOrNull { it.daysUntil <= AAJ_FAMILY_DAYS }
    val me = stringResource(KundliR.string.kundli_me)
    val family = next?.let { AajFamilyRow(familyTitle(it, me), null, familyDaysText(it), it.daysUntil <= SOON_DAYS) }
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
        family = family,
    )
}
