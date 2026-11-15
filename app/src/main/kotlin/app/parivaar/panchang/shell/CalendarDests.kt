package app.parivaar.panchang.shell

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parivaar.panchang.BuildConfig
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.feature.calendar.RegionalViewsGate
import app.parivaar.panchang.feature.calendar.ui.CalendarActions
import app.parivaar.panchang.feature.calendar.ui.CalendarScreen
import app.parivaar.panchang.feature.settings.ConventionsModel
import app.parivaar.panchang.feature.settings.ConventionsScreen
import app.parivaar.panchang.feature.aaj.AajModel
import app.parivaar.panchang.feature.aaj.ui.AajActions
import app.parivaar.panchang.feature.aaj.ui.AajExtras
import app.parivaar.panchang.feature.aaj.ui.AajNames
import app.parivaar.panchang.feature.aaj.ui.AajScreen
import app.parivaar.panchang.feature.calendar.MonthModel
import app.parivaar.panchang.feature.calendar.MonthUi
import app.parivaar.panchang.feature.calendar.TransitEvent
import app.parivaar.panchang.feature.calendar.common.CalFormat
import app.parivaar.panchang.feature.calendar.grahan.GrahanItem
import app.parivaar.panchang.feature.calendar.grahan.GrahanItems
import app.parivaar.panchang.feature.calendar.grahan.GrahanScreen
import app.parivaar.panchang.feature.calendar.guide.FestivalGuide
import app.parivaar.panchang.feature.calendar.guide.FestivalGuideScreen
import app.parivaar.panchang.feature.calendar.guide.FestivalGuideUi
import app.parivaar.panchang.feature.calendar.shouldi.ShouldI
import app.parivaar.panchang.feature.calendar.shouldi.ShouldIAnswer
import app.parivaar.panchang.feature.calendar.shouldi.ShouldILabels
import app.parivaar.panchang.feature.calendar.shouldi.ShouldIScreen
import app.parivaar.panchang.feature.calendar.transits.TransitRows
import app.parivaar.panchang.feature.calendar.transits.TransitsScreen
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.time.Clock

private const val MONTHS = 12

/** The Today tab root, wired to navigation. */
@Composable
internal fun AajTab(
    aaj: AajModel,
    name: String?,
    navigator: AppNavigator,
    extras: AajExtras,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
) = AajScreen(
    aaj, name, null,
    AajActions(
        onAvatar = onAvatar, onSearch = onSearch,
        onChooseCity = { navigator.push(Dest.Settings) },
        onOpenConventions = { navigator.push(Dest.Conventions) },
        onRetry = aaj::refresh,
    ),
    extras,
)

/** What the shell needs for the calendar: the month model, the loaders and the `.ics` share action. */
class CalendarWiring(val month: MonthModel, val controller: CalendarController, val onShareIcs: (Int) -> Unit)

/** The Calendar tab root, with every action wired to navigation and the Conventions settings. */
@Composable
internal fun CalendarTab(
    cal: CalendarWiring,
    settings: AppSettings,
    subtitle: String?,
    navigator: AppNavigator,
    conventions: ConventionsModel,
    onSearch: () -> Unit,
) {
    val debug = BuildConfig.DEBUG
    CalendarScreen(
        cal.month, settings.ekadashi, RegionalViewsGate.effective(settings.calendarView, debug),
        RegionalViewsGate.enabled(debug), subtitle, null, cal.controller::festivals,
        CalendarActions(
            onAvatar = { navigator.push(Dest.Settings) }, onSearch = onSearch,
            onChooseCity = { navigator.push(Dest.Settings) },
            onShouldI = { navigator.push(Dest.ShouldI) },
            onGuide = { navigator.push(Dest.FestivalGuide(it)) },
            onGrahan = { navigator.push(Dest.Grahan) },
            onTransits = { navigator.push(Dest.Transits) },
            onShareIcs = cal.onShareIcs,
            onConventions = { navigator.push(Dest.Conventions) },
            onEkadashi = conventions::setEkadashi,
            onView = conventions::setCalendarView,
        ),
    )
}

/** Conventions, with the regional calendar views offered only while the gate is open. */
@Composable
internal fun ConventionsDest(settings: AppSettings, conventions: ConventionsModel) =
    ConventionsScreen(settings, conventions, regionalEnabled = RegionalViewsGate.enabled(BuildConfig.DEBUG))

/** The four calendar sub-screens. */
@Composable
internal fun CalendarSubScreen(d: Dest, cal: CalendarWiring, onBack: () -> Unit) {
    val controller = cal.controller
    when (d) {
        is Dest.FestivalGuide -> FestivalGuideDest(d.id, controller, onBack)
        Dest.Grahan -> GrahanDest(controller, onBack)
        Dest.Transits -> TransitsDest(controller, cal.month, onBack)
        Dest.ShouldI -> ShouldIDest(controller, onBack)
        else -> Unit
    }
}

@Composable
private fun rememberFormat(controller: CalendarController): Pair<CalFormat, AajNames> {
    val ctx = LocalContext.current
    val zone = controller.inputs.collectAsStateWithLifecycle().value?.zone ?: TimeZone.currentSystemDefault()
    val is24 = DateFormat.is24HourFormat(ctx)
    val fmt = remember(zone, is24) { CalFormat(zone, Locale.getDefault(), is24) }
    val names = remember(ctx) { AajNames(ctx.resources, ctx.packageName) }
    return fmt to names
}

/** Festival guide page: the next three dates come from the engine for this year and the next. */
@Composable
fun FestivalGuideDest(id: String, controller: CalendarController, onBack: () -> Unit) {
    val (fmt, names) = rememberFormat(controller)
    val inputs by controller.inputs.collectAsStateWithLifecycle()
    val ui by produceState<FestivalGuideUi?>(null, id, inputs) {
        val def = controller.defs().firstOrNull { it.id == id } ?: return@produceState
        val today = fmt.dateOf(Clock.System.now())
        value = FestivalGuide.build(def, controller.festivals(today.year), controller.festivals(today.year + 1), today)
    }
    ui?.let { FestivalGuideScreen(it, names, fmt, onBack) }
}

/** Eclipse list for a year (the year is kept across rotation). */
@Composable
fun GrahanDest(controller: CalendarController, onBack: () -> Unit) {
    val (fmt, names) = rememberFormat(controller)
    val inputs by controller.inputs.collectAsStateWithLifecycle()
    var year by rememberSaveable { mutableIntStateOf(fmt.dateOf(Clock.System.now()).year) }
    val items by produceState<List<GrahanItem>>(emptyList(), year, inputs) {
        value = GrahanItems.of(controller.eclipses(year))
    }
    GrahanScreen(year, items, names, fmt, { year = it }, onBack)
}

/** Month transits and retrogrades, with the Adhik maas notice of the calendar's shown month. */
@Composable
fun TransitsDest(controller: CalendarController, month: MonthModel, onBack: () -> Unit) {
    val (fmt, names) = rememberFormat(controller)
    val inputs by controller.inputs.collectAsStateWithLifecycle()
    val ui by month.ui.collectAsStateWithLifecycle()
    var index by rememberSaveable {
        val t = fmt.dateOf(Clock.System.now())
        mutableIntStateOf(t.year * MONTHS + t.monthNumber - 1)
    }
    val ym = YearMonth(index / MONTHS, index % MONTHS + 1)
    val events by produceState<List<TransitEvent>>(emptyList(), index, inputs) {
        val first = LocalDate(ym.year, (ym.month.ordinal + 1), 1)
        val last = first.plus(DatePeriod(months = 1)).plus(DatePeriod(days = -1))
        value = TransitRows.sorted(controller.transits(first, last), fmt::dateOf)
    }
    val label = remember(index) {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "LLLLyyyy")
        val jym = java.time.YearMonth.of(ym.year, ym.month.ordinal + 1)
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(jym)
    }
    val adhika = (ui as? MonthUi.Content)?.header?.takeIf { it.ym == ym }?.adhika
    TransitsScreen(label, events, adhika, names, fmt, { index += it }, onBack)
}

/** "Should I...?": rule-based answer for now; the choice survives rotation. */
@Composable
fun ShouldIDest(controller: CalendarController, onBack: () -> Unit) {
    val (fmt, _) = rememberFormat(controller)
    val inputs by controller.inputs.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val intents = remember { ShouldILabels.intentIds.toList() }
    val answer by produceState<ShouldIAnswer?>(null, selected, inputs) {
        val id = selected ?: return@produceState
        val rule = controller.rules.firstOrNull { it.id == id } ?: return@produceState
        val days = controller.day(fmt.dateOf(Clock.System.now()))
        val tithi = days.firstOrNull()?.let { runCatching { it.atSunrise(LimbKind.TITHI).index }.getOrNull() } ?: 0
        value = ShouldI.answer(rule, Clock.System.now(), days, tithi)
    }
    ShouldIScreen(intents, selected, answer, fmt, { selected = it }, onBack)
}
