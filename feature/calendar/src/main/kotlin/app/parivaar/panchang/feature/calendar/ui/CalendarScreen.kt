@file:Suppress("MaxLineLength", "LongParameterList", "LongMethod", "MagicNumber", "CyclomaticComplexMethod", "MatchingDeclarationName")

package app.parivaar.panchang.feature.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.heading
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.RatnaMoodScope
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood
import app.parivaar.panchang.engine.panchang.LimbKind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.ErrorState
import app.parivaar.panchang.core.designsystem.components.LoadingState
import app.parivaar.panchang.core.designsystem.components.PpTopBar
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.feature.calendar.DayDetail
import app.parivaar.panchang.feature.calendar.Marker
import app.parivaar.panchang.feature.calendar.MonthModel
import app.parivaar.panchang.feature.calendar.MonthUi
import app.parivaar.panchang.feature.calendar.R
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

/** What the calendar tab needs from the app: navigation and the two settings it can change. */
class CalendarActions(
    val onAvatar: () -> Unit,
    val onSearch: () -> Unit,
    val onChooseCity: () -> Unit,
    val onShouldI: (LocalDate) -> Unit,
    val onGuide: (String) -> Unit,
    val onGrahan: () -> Unit,
    val onTransits: () -> Unit,
    val onShareIcs: (year: Int) -> Unit,
    val onConventions: () -> Unit,
    val onEkadashi: (Tradition) -> Unit,
    val onView: (CalendarView) -> Unit,
)

/**
 * The calendar tab. [regionalEnabled] is `RegionalViewsGate.enabled(BuildConfig.DEBUG)`; [loadFestivals] runs
 * `CalendarEngine.festivalsYear` off the main thread (under the engine lock). No ads here: a timing screen.
 */
@Composable
fun CalendarScreen(
    model: MonthModel,
    tradition: Tradition,
    view: CalendarView,
    regionalEnabled: Boolean,
    subtitle: String?,
    avatarInitials: String?,
    loadFestivals: suspend (Int) -> List<FestivalOccurrence>,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val ui by model.ui.collectAsState()
    val selected by model.selected.collectAsState()
    val detail = remember(selected, ui) { selected?.let { model.detail(it) } }
    CalendarContent(
        ui, selected, detail, tradition, view, regionalEnabled, subtitle, avatarInitials, loadFestivals, actions,
        onSelect = model::select, onShow = model::show, onPrev = model::prev, onNext = model::next, onToday = model::today,
        onJump = model::jumpTo, modifier = modifier,
    )
}

/** Stateless body, used by previews. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarContent(
    ui: MonthUi,
    selected: LocalDate?,
    detail: DayDetail?,
    tradition: Tradition,
    view: CalendarView,
    regionalEnabled: Boolean,
    subtitle: String?,
    avatarInitials: String?,
    loadFestivals: suspend (Int) -> List<FestivalOccurrence>,
    actions: CalendarActions,
    onSelect: (LocalDate?) -> Unit,
    onShow: (kotlinx.datetime.YearMonth) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onJump: (FestivalOccurrence) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale: Locale = LocalConfiguration.current.locales[0]
    val names = remember(locale, context.resources) { ResCalendarNames(context.resources, context.packageName, locale) }
    var picker by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf(false) }
    val paper = regionalEnabled && view != CalendarView.STANDARD
    val screen: @Composable () -> Unit = {
    Column(modifier.fillMaxSize().background(PpTheme.ratna.bg0)) {
        PpTopBar(stringResource(R.string.cal_title), subtitle, avatarInitials, actions.onAvatar, actions.onSearch)
        when (ui) {
            MonthUi.NoPlace -> EmptyState(
                stringResource(R.string.cal_no_place_title), stringResource(R.string.cal_no_place_body),
                action = stringResource(R.string.cal_no_place_action) to actions.onChooseCity,
            )
            is MonthUi.Loading -> if (ui.stale == null) LoadingState(stringResource(R.string.cal_loading)) else Body(ui.stale, true, selected, names, view, locale, tradition, regionalEnabled, actions, onSelect, onPrev, onNext, onToday, { picker = true }, { jump = true })
            is MonthUi.Error -> ErrorState(
                stringResource(if (ui.nonexistent) R.string.cal_nonexistent_title else R.string.cal_error_title),
                stringResource(if (ui.nonexistent) R.string.cal_nonexistent_body else R.string.cal_error_body),
                listOf(stringResource(R.string.cal_today) to onToday),
            )
            is MonthUi.Content -> Body(ui, false, selected, names, view, locale, tradition, regionalEnabled, actions, onSelect, onPrev, onNext, onToday, { picker = true }, { jump = true })
        }
    }
    }
    // Regional wall views use the Paper mood, scoped to this screen only (DESIGN_V2 section 3).
    if (paper) RatnaMoodScope(RatnaMood.PAPER, screen) else screen()
    val shown = (ui as? MonthUi.Content) ?: (ui as? MonthUi.Loading)?.stale
    if (picker && shown != null) {
        MonthYearPicker(shown.key.ym, shown.today.year, locale, { picker = false; onShow(it) }, { picker = false })
    }
    if (jump && shown != null) {
        FestivalJumpSheet(shown.key.ym.year, names, locale, loadFestivals, { jump = false; onJump(it) }, { jump = false })
    }
    if (selected != null && detail != null) {
        DaySheet(
            detail, names, locale, onShouldI = { actions.onShouldI(selected) }, onGuide = actions.onGuide,
            onConventions = actions.onConventions, onDismiss = { onSelect(null) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Body(
    c: MonthUi.Content,
    dimmed: Boolean,
    selected: LocalDate?,
    names: ResCalendarNames,
    view: CalendarView,
    locale: Locale,
    tradition: Tradition,
    regionalEnabled: Boolean,
    actions: CalendarActions,
    onSelect: (LocalDate?) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPick: () -> Unit,
    onJump: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
        MonthHeaderBar(c.header, view, names, locale, c.today.yearMonth != c.key.ym, onPrev, onNext, onToday, onPick)
        val traditionGroup = stringResource(R.string.cal_tradition_group)
        FlowRow(Modifier.padding(horizontal = PpSpace.l).semantics { contentDescription = traditionGroup }, horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            FilterChip(tradition == Tradition.SMARTA, { actions.onEkadashi(Tradition.SMARTA) }, { Text(stringResource(R.string.cal_tradition_smarta)) })
            FilterChip(tradition == Tradition.VAISHNAVA, { actions.onEkadashi(Tradition.VAISHNAVA) }, { Text(stringResource(R.string.cal_tradition_vaishnava)) })
        }
        if (regionalEnabled) {
            val viewGroup = stringResource(R.string.cal_view_group)
            FlowRow(Modifier.padding(horizontal = PpSpace.l).semantics { contentDescription = viewGroup }, horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                CalendarView.entries.forEach { v ->
                    FilterChip(view == v, { actions.onView(v) }, {
                        Text(stringResource(when (v) { CalendarView.STANDARD -> R.string.cal_view_standard; CalendarView.MARATHI -> R.string.cal_view_marathi; CalendarView.GUJARATI -> R.string.cal_view_gujarati }))
                    })
                }
            }
        }
        MonthGrid(c, dimmed, selected, names, view, locale, { onSelect(it) }, onPrev, onNext)
        Legend(names)
        if (view != CalendarView.STANDARD && regionalEnabled) {
            FestivalList(c, names, view)
        } else {
            TodayHero(c, names, locale, onOpenDay = { onSelect(it) })
        }
        FlowRow(Modifier.padding(horizontal = PpSpace.l), horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            PpButton(stringResource(R.string.cal_jump_festival), onJump, PpButtonStyle.Outline)
            PpButton(stringResource(R.string.cal_open_grahan), actions.onGrahan, PpButtonStyle.Outline)
            PpButton(stringResource(R.string.cal_open_transits), actions.onTransits, PpButtonStyle.Outline)
            PpButton(stringResource(R.string.cal_share_ics), { actions.onShareIcs(c.key.ym.year) }, PpButtonStyle.Outline)
        }
        Text(stringResource(R.string.cal_footer), Modifier.padding(horizontal = PpSpace.l), style = PpType.meta, color = PpTheme.ratna.text3)
        Spacer(Modifier.height(PpSpace.xxl))
    }
}

/** Key to the glyphs in each cell, so the grid never relies on colour alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(names: ResCalendarNames) {
    val vriddha = stringResource(R.string.cal_mark_glyph_vriddhi)
    val kshaya = stringResource(R.string.cal_mark_glyph_kshaya)
    FlowRow(Modifier.padding(horizontal = PpSpace.l), horizontalArrangement = Arrangement.spacedBy(PpSpace.m)) {
        Marker.entries.forEach {
            Text(stringResource(R.string.cal_legend_item, names.markerGlyph(it), names.markerName(it)), style = PpType.label, color = PpTheme.ratna.text3)
        }
        Text(stringResource(R.string.cal_legend_item, vriddha, stringResource(R.string.cal_flag_vriddhi)), style = PpType.label, color = PpTheme.ratna.text3)
        Text(stringResource(R.string.cal_legend_item, kshaya, stringResource(R.string.cal_flag_kshaya)), style = PpType.label, color = PpTheme.ratna.text3)
    }
}

/**
 * Today hero (one `hero` Lacquer on the screen). Utsav mood only when today has a festival occurrence, not a vrat-only
 * day (D5). Everything is read from the already built month; no engine call.
 */
@Composable
private fun TodayHero(c: MonthUi.Content, names: ResCalendarNames, locale: Locale, onOpenDay: (LocalDate) -> Unit) {
    val cell = UtsavDays.todayCell(c) ?: return
    val fmt = remember(locale) { java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMEd"), locale) }
    val date = fmt.format(java.time.LocalDate.ofEpochDay(cell.date.toEpochDays()))
    val tithi = names.aaj.limbSpan(LimbKind.TITHI, cell.tithi)
    val festivals = cell.festivalIds.joinToString(", ") { names.festivalName(it) }
    val utsav = UtsavDays.isUtsav(cell.festivalIds)
    val body: @Composable () -> Unit = {
        val r = PpTheme.ratna
        Lacquer(Modifier.fillMaxWidth().padding(horizontal = PpSpace.l), hero = true) {
            Text(stringResource(R.string.cal_hero_eyebrow, date, tithi), style = PpType.eyebrow, color = r.gold)
            Text(festivals.ifEmpty { tithi }, style = PpType.displayM, color = r.text, modifier = Modifier.padding(top = PpSpace.xs).semantics { heading() })
            PpButton(stringResource(R.string.cal_hero_day), { onOpenDay(cell.date) }, PpButtonStyle.Outline, modifier = Modifier.padding(top = PpSpace.m))
        }
    }
    if (utsav) RatnaMoodScope(RatnaMood.UTSAV, body) else body()
}

/** Wall-calendar festival list under the Paper grid: day number and name, from the cached month. */
@Composable
private fun FestivalList(c: MonthUi.Content, names: ResCalendarNames, view: CalendarView) {
    val locale = LocalConfiguration.current.locales[0]
    val items = c.weeks.flatten().filter { it.inMonth && it.festivalIds.isNotEmpty() }.flatMap { cell ->
        cell.festivalIds.map { stringResource(R.string.cal_regional_festival_item, RegionalText.number(cell.date.day, view, locale), names.festivalName(it)) }
    }
    if (items.isEmpty()) return
    val r = PpTheme.ratna
    Lacquer(Modifier.fillMaxWidth().padding(horizontal = PpSpace.l)) {
        Text(stringResource(if (view == CalendarView.GUJARATI) R.string.cal_regional_festivals_gu else R.string.cal_regional_festivals_mr), style = PpType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = r.kumkum)
        Text(items.joinToString(" · "), style = PpType.body, color = r.text, modifier = Modifier.padding(top = PpSpace.xs))
    }
}
