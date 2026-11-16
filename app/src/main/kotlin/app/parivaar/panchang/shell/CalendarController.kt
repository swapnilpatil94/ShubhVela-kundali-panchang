@file:Suppress("MaxLineLength")

package app.parivaar.panchang.shell

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import app.parivaar.panchang.BuildConfig
import app.parivaar.panchang.platform.zoneSafe
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.tzContext
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.feature.calendar.CalInputs
import app.parivaar.panchang.feature.calendar.CalendarEngine
import app.parivaar.panchang.feature.calendar.GlobalWithLocal
import app.parivaar.panchang.feature.calendar.NonexistentCivilDayException
import app.parivaar.panchang.feature.calendar.RegionalViewsGate
import app.parivaar.panchang.feature.calendar.TransitEvent
import app.parivaar.panchang.feature.calendar.R as CalR
import app.parivaar.panchang.feature.calendar.guide.FestivalNames
import app.parivaar.panchang.feature.calendar.ics.IcsEvents
import app.parivaar.panchang.feature.calendar.ics.IcsShare
import app.parivaar.panchang.feature.calendar.shouldi.IntentRule
import app.parivaar.panchang.feature.calendar.shouldi.ShouldIRules
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinDayOfWeek

/**
 * The calendar tab's inputs and loaders. Every engine call goes through [engine] (one instance, one lock) on
 * [compute]; nothing here computes astronomy. Place, zone and settings never leave memory.
 */
class CalendarController(
    places: Flow<List<SavedPlace>>,
    settings: Flow<AppSettings>,
    away: Flow<GeoPlace?>,
    private val offsets: OffsetResolver,
    val engine: CalendarEngine,
    val defs: () -> List<FestivalDef>,
    private val assetText: (String) -> String,
    scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
) {
    /** Null until a home place exists. The M08 session travel place wins when set. */
    val inputs: StateFlow<CalInputs?> =
        combine(places, settings, away) { recs, s, travel -> build(recs, s, travel) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val rules: List<IntentRule> by lazy { ShouldIRules.parse(assetText("rules/should_i.json")) }

    @Suppress("ReturnCount")
    private fun build(items: List<SavedPlace>, s: AppSettings, travel: GeoPlace?): CalInputs? {
        val home = items.firstOrNull { it.id == s.homePlaceId } ?: return null
        val g = travel ?: home.place
        val id = if (travel != null) RecordId("%032x".format(travel.id)) else home.id
        val ctx = g.tzContext()
        val offsetFor = { d: LocalDate ->
            when (val o = offsets.civilDayOffset(d, ctx)) {
                is CivilDayOffset.Valid -> o.offset.offsetSeconds
                CivilDayOffset.Nonexistent -> throw NonexistentCivilDayException()
            }
        }
        // An unknown zone id (stored before validation, or missing from this device's tz data) shows no month, never a crash.
        val zone = zoneSafe(null) { kotlinx.datetime.TimeZone.of(g.zoneId) } ?: return null
        return CalInputs(
            Place(g.latDeg, g.lonDeg, g.elevationM ?: 0.0), id, zone, offsetFor,
            s.defaultCalc, s.monthSystem, s.ekadashi, RegionalViewsGate.effective(s.calendarView, BuildConfig.DEBUG),
        )
    }

    private suspend fun <T> load(empty: T, block: (CalInputs) -> T): T {
        val i = inputs.value ?: return empty
        return withContext(compute) {
            try {
                block(i)
            } catch (_: NonexistentCivilDayException) {
                empty
            }
        }
    }

    suspend fun festivals(year: Int): List<FestivalOccurrence> = load(emptyList()) { engine.festivalsYear(year, it) }

    suspend fun eclipses(year: Int): List<GlobalWithLocal> = load(emptyList()) { engine.eclipsesYear(year, it) }

    suspend fun transits(from: LocalDate, to: LocalDate): List<TransitEvent> =
        load(emptyList()) { engine.transits(from, to, it) }

    suspend fun day(date: LocalDate): List<PanchangDay> = load(emptyList()) { engine.day(date, it) }

    /** Writes the year's public festival dates as `.ics` and opens the share sheet. No place or name is included. */
    fun shareIcs(activity: Context, year: Int, scope: CoroutineScope) {
        scope.launch {
            val uri: Uri?
            val share = IcsShare(
                activity.cacheDir,
                { FileProvider.getUriForFile(activity, activity.packageName + ".share", it) },
                activity.getString(CalR.string.ics_chooser),
            )
            val events = IcsEvents.of(festivals(year)) { FestivalNames.name(activity.resources, it) }
            uri = withContext(Dispatchers.IO) { if (events.isEmpty()) null else share.prepare(events) }
            if (uri == null) {
                Toast.makeText(activity, CalR.string.ics_failed, Toast.LENGTH_SHORT).show()
            } else {
                activity.startActivity(share.chooser(uri))
            }
        }
    }

    companion object {
        fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek.toKotlinDayOfWeek()
    }
}
