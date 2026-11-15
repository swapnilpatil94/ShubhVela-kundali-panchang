package app.parivaar.panchang.shell

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.components.DockScrollRule
import app.parivaar.panchang.core.designsystem.components.dockVisible
import app.parivaar.panchang.feature.aaj.ui.rememberDockState
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
import app.parivaar.panchang.feature.aaj.AajModel
import app.parivaar.panchang.feature.aaj.ui.AajActions
import app.parivaar.panchang.feature.aaj.ui.AajExtras
import app.parivaar.panchang.feature.aaj.ui.AajScreen
import app.parivaar.panchang.feature.bhakti.BhaktiPlaceholderScreen
import app.parivaar.panchang.feature.bonds.BondsPlaceholderScreen
import app.parivaar.panchang.feature.kundli.KundliPlaceholderScreen
import app.parivaar.panchang.feature.settings.AppearanceModel
import app.parivaar.panchang.feature.settings.ConventionsModel
import app.parivaar.panchang.feature.settings.LanguageChoice
import app.parivaar.panchang.feature.settings.PrivacyScreen
import app.parivaar.panchang.feature.settings.SettingsScreen
import kotlinx.coroutines.CancellationException
import app.parivaar.panchang.core.designsystem.R as DsR

private val NAV_ITEMS =
    listOf(
        NavItem(Tab.CALENDAR.name, DsR.string.tab_calendar, DsR.drawable.ic_tab_calendar),
        NavItem(Tab.KUNDLI.name, DsR.string.tab_kundli, DsR.drawable.ic_tab_kundli),
        NavItem(Tab.AAJ.name, DsR.string.tab_aaj, DsR.drawable.ic_tab_aaj, center = true),
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
    aaj: AajModel,
    aajExtras: AajExtras,
    cal: CalendarWiring,
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
    val dock = rememberDockState(aaj)
    val dockScroll = rememberDockScroll(tab to dest)
    PpNavigationSuite(
        NAV_ITEMS, tab.name, { navigator.select(Tab.valueOf(it)) },
        gem = dock.gem, orb = dock.orb, dockVisible = dockVisible(dest.immersive, dockScroll.visible),
        underDock = runsUnderDock(tab, dest),
    ) {
        Box(modifier.fillMaxSize().nestedScroll(dockScroll.connection)) {
            when (val d = dest) {
                is Dest.Root ->
                    when (tab) {
                        Tab.AAJ -> AajTab(aaj, homePlaceName, navigator, aajExtras, avatar, search)
                        Tab.CALENDAR ->
                            CalendarTab(cal, settings, homePlaceName, navigator, conventions, search)
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
                Dest.Conventions -> ConventionsDest(settings, conventions)
                Dest.Privacy -> PrivacyScreen()
                is Dest.FestivalGuide, Dest.Grahan, Dest.Transits, Dest.ShouldI ->
                    CalendarSubScreen(d, cal) { navigator.back() }
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

/** Hide-on-scroll state of the dock; resets whenever [resetKey] (tab or destination) changes. */
private class DockScroll(
    val connection: NestedScrollConnection,
    val visibleState: androidx.compose.runtime.State<Boolean>,
) {
    val visible: Boolean get() = visibleState.value
}

@Composable
private fun rememberDockScroll(resetKey: Any): DockScroll {
    val thresholdPx = with(LocalDensity.current) { DockScrollRule.THRESHOLD_DP.dp.toPx() }
    val rule = remember(thresholdPx) { DockScrollRule(thresholdPx) }
    val visible = remember { mutableStateOf(true) }
    LaunchedEffect(resetKey) {
        rule.reset()
        visible.value = true
    }
    val connection = remember(rule) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                visible.value = rule.onScroll(available.y)
                return Offset.Zero
            }
        }
    }
    return remember(connection) { DockScroll(connection, visible) }
}

/** Aaj scrolls beneath the dock and pads itself by `LocalDockInset`; every other screen keeps the reserved strip. */
private fun runsUnderDock(tab: Tab, dest: Dest) = tab == Tab.AAJ && dest is Dest.Root
