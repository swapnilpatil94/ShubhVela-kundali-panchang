@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.ErrorState
import app.parivaar.panchang.core.designsystem.components.LoadingState
import app.parivaar.panchang.core.designsystem.components.LocalDockInset
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.components.PpTopBar
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.feature.aaj.AajModel
import app.parivaar.panchang.feature.aaj.AajUi
import app.parivaar.panchang.feature.aaj.HoraSky
import app.parivaar.panchang.feature.aaj.PlaceClock
import app.parivaar.panchang.feature.aaj.PpPerf
import app.parivaar.panchang.feature.aaj.R
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import app.parivaar.panchang.core.designsystem.R as DsR

/** Hooks the screen needs from the shell. */
class AajActions(
    val onAvatar: () -> Unit,
    val onSearch: () -> Unit,
    val onChooseCity: () -> Unit,
    val onOpenConventions: () -> Unit,
    val onRetry: () -> Unit,
)

/** The Today tab. Collects the model; re-computes when the clock, date or time zone changes while visible. */
@Composable
fun AajScreen(
    model: AajModel,
    subtitle: String?,
    avatarInitials: String?,
    actions: AajActions,
    extras: AajExtras,
    modifier: Modifier = Modifier,
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val now by model.tick.collectAsStateWithLifecycle()
    val sky by model.sky.collectAsStateWithLifecycle()
    ClockChangeEffect(model::refresh)
    LaunchedEffect(ui is AajUi.Content) { if (ui is AajUi.Content) PpPerf.mark("aaj.firstContentComposed") }
    AajScreenContent(ui, now, subtitle, avatarInitials, actions, modifier, extras, sky)
}

@Composable
private fun ClockChangeEffect(onChange: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = onChange()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }
}

/** Stateless, so previews can show every state. */
@Composable
fun AajScreenContent(
    ui: AajUi,
    now: Instant,
    subtitle: String?,
    avatarInitials: String?,
    actions: AajActions,
    modifier: Modifier = Modifier,
    extras: AajExtras = AajExtras.None,
    sky: HoraSky? = null,
) {
    Column(modifier.fillMaxSize()) {
        // The day content draws its own sky header with search and profile over it; every other state keeps the bar.
        if (ui !is AajUi.Content) PpTopBar(stringResource(DsR.string.tab_aaj), subtitle, avatarInitials, actions.onAvatar, actions.onSearch)
        when (ui) {
            AajUi.Loading -> LoadingState(stringResource(R.string.aaj_loading))
            AajUi.NoPlace -> EmptyState(
                stringResource(R.string.aaj_no_place_title), stringResource(R.string.aaj_no_place_body),
                action = stringResource(R.string.aaj_no_place_action) to actions.onChooseCity,
            )
            AajUi.NonexistentDate -> EmptyState(
                stringResource(R.string.aaj_nonexistent_title), stringResource(R.string.aaj_nonexistent_body),
            )
            AajUi.Failed -> ErrorState(
                stringResource(R.string.aaj_failed_title), stringResource(R.string.aaj_failed_body),
                listOf(stringResource(R.string.aaj_retry) to actions.onRetry),
            )
            is AajUi.PolarDay -> PolarDayContent(ui, actions)
            is AajUi.Content -> DayContent(ui, now, sky, subtitle, avatarInitials, actions, extras)
        }
    }
}

@Composable
internal fun rememberNames(): AajNames {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    return remember(config) { AajNames(context.resources, context.packageName) }
}

@Composable
internal fun rememberFmt(offsetSeconds: Int, shown: LocalDate, zoneId: String? = null): TimeFmt {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(offsetSeconds, shown, zoneId, locale) {
        TimeFmt(PlaceClock(zoneId, offsetSeconds, shown), shown, DateFormat.is24HourFormat(context), locale)
    }
}

@Composable
internal fun InfoCard(title: String, body: String, modifier: Modifier = Modifier) {
    PpCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PolarDayContent(ui: AajUi.PolarDay, actions: AajActions) {
    val names = rememberNames()
    val fmt = rememberFmt(ui.offsetSeconds, ui.date, ui.zoneId)
    val res = LocalContext.current.resources
    LazyColumn(contentPadding = PaddingValues(start = PpSpace.l, end = PpSpace.l, top = PpSpace.m, bottom = LocalDockInset.current + PpSpace.l), verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
        item { InfoCard(stringResource(R.string.aaj_polar_title), stringResource(R.string.aaj_polar_body)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
                ui.limbs.forEach { (kind, spans) ->
                    PpCard(Modifier.fillMaxSize()) {
                        Text(names.limbTitle(kind), style = MaterialTheme.typography.titleMedium)
                        spans.forEach {
                            Text(
                                res.getString(R.string.aaj_limb_until, names.limbSpan(kind, it.index), fmt.stamp(res, it.end)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
        item { PpButton(stringResource(R.string.aaj_how_calculated), actions.onOpenConventions, PpButtonStyle.Outline) }
    }
}
