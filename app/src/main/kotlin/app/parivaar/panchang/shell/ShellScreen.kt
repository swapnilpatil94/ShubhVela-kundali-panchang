package app.parivaar.panchang.shell

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parivaar.panchang.core.designsystem.components.NavItem
import app.parivaar.panchang.core.designsystem.components.PpNavigationSuite
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.feature.aaj.AajPlaceholderScreen
import app.parivaar.panchang.feature.bhakti.BhaktiPlaceholderScreen
import app.parivaar.panchang.feature.bonds.BondsPlaceholderScreen
import app.parivaar.panchang.feature.calendar.CalendarPlaceholderScreen
import app.parivaar.panchang.feature.kundli.KundliPlaceholderScreen
import app.parivaar.panchang.feature.settings.AppearanceModel
import app.parivaar.panchang.feature.settings.ConventionsModel
import app.parivaar.panchang.feature.settings.ConventionsScreen
import app.parivaar.panchang.feature.settings.LanguageChoice
import app.parivaar.panchang.feature.settings.PrivacyScreen
import app.parivaar.panchang.feature.settings.SettingsScreen
import kotlinx.coroutines.CancellationException
import app.parivaar.panchang.core.designsystem.R as DsR

private val NAV_ITEMS =
    listOf(
        NavItem(Tab.AAJ.name, DsR.string.tab_aaj, DsR.drawable.ic_tab_aaj),
        NavItem(Tab.CALENDAR.name, DsR.string.tab_calendar, DsR.drawable.ic_tab_calendar),
        NavItem(Tab.KUNDLI.name, DsR.string.tab_kundli, DsR.drawable.ic_tab_kundli),
        NavItem(Tab.BONDS.name, DsR.string.tab_bonds, DsR.drawable.ic_tab_bonds),
        NavItem(Tab.BHAKTI.name, DsR.string.tab_bhakti, DsR.drawable.ic_tab_bhakti),
    )

/** The 5-tab shell: navigation suite, tab placeholders, Settings destinations, search palette and predictive back. */
@Composable
fun ShellScreen(
    settings: AppSettings,
    homePlaceName: String?,
    languages: List<LanguageChoice>,
    conventions: ConventionsModel,
    appearance: AppearanceModel,
    modifier: Modifier = Modifier,
) {
    val navigator =
        rememberSaveable(saver = listSaver(save = { it.save() }, restore = { AppNavigator.restore(it) })) {
            AppNavigator()
        }
    val dest by navigator.current.collectAsStateWithLifecycle()
    val tab by navigator.tab.collectAsStateWithLifecycle()
    val canGoBack by navigator.canGoBack.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }

    PredictiveBackHandler(enabled = canGoBack) { progress ->
        try {
            progress.collect { }
            navigator.back()
        } catch (_: CancellationException) {
            // gesture cancelled: stay where we are
        }
    }

    val avatar = { navigator.push(Dest.Settings) }
    val search = { searching = true }
    PpNavigationSuite(NAV_ITEMS, tab.name, { navigator.select(Tab.valueOf(it)) }) {
        Box(modifier.fillMaxSize()) {
            when (dest) {
                is Dest.Root ->
                    when (tab) {
                        Tab.AAJ -> AajPlaceholderScreen(homePlaceName, null, avatar, search)
                        Tab.CALENDAR -> CalendarPlaceholderScreen(homePlaceName, null, avatar, search)
                        Tab.KUNDLI -> KundliPlaceholderScreen(homePlaceName, null, avatar, search)
                        Tab.BONDS -> BondsPlaceholderScreen(homePlaceName, null, avatar, search)
                        Tab.BHAKTI -> BhaktiPlaceholderScreen(homePlaceName, null, avatar, search)
                    }
                Dest.Settings ->
                    SettingsScreen(
                        settings, languages, appearance,
                        onOpenConventions = { navigator.push(Dest.Conventions) },
                        onOpenPrivacy = { navigator.push(Dest.Privacy) },
                    )
                Dest.Conventions -> ConventionsScreen(settings, conventions)
                Dest.Privacy -> PrivacyScreen()
            }
        }
    }
    if (searching) {
        SearchPalette(
            onPick = { d ->
                searching = false
                if (d is Dest.Root) navigator.select(d.tab) else navigator.push(d)
            },
            onDismiss = { searching = false },
        )
    }
}
