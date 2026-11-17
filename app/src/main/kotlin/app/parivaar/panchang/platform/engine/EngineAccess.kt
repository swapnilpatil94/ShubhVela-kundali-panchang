package app.parivaar.panchang.platform.engine

import android.content.Context
import app.parivaar.panchang.core.data.DataVault
import app.parivaar.panchang.core.data.PlaceRepository
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.feature.kundli.CalInputsLite
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.tzContext
import app.parivaar.panchang.platform.zoneSafe
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.SelectingEphemeris
import app.parivaar.panchang.engine.calendar.CalendarCalculator
import app.parivaar.panchang.engine.jyotish.ChartCalculator
import app.parivaar.panchang.content.horoscope.EngineTransitSource
import app.parivaar.panchang.content.horoscope.TransitSource
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.jyotish.NakshatraTable
import app.parivaar.panchang.engine.jyotish.Vargas
import app.parivaar.panchang.engine.jyotish.YogaRules
import app.parivaar.panchang.feature.kundli.DirectKundliEngine
import app.parivaar.panchang.feature.kundli.FinderFamilyDates
import app.parivaar.panchang.engine.calendar.FamilyDateFinder
import app.parivaar.panchang.feature.kundli.KundliEngine
import app.parivaar.panchang.feature.calendar.CalendarEngine
import app.parivaar.panchang.feature.calendar.DirectCalendarEngine
import app.parivaar.panchang.feature.calendar.forTradition
import app.parivaar.panchang.engine.calendar.EclipseSearch
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.FestivalEngine
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.FestivalRule
import app.parivaar.panchang.engine.calendar.FestivalRules
import app.parivaar.panchang.engine.calendar.LocalEclipse
import app.parivaar.panchang.engine.panchang.PanchangCalculator
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.calendar.FestivalSet
import app.parivaar.panchang.engine.panchang.PanchangRules
import app.parivaar.panchang.engine.panchang.PanchangTables
import app.parivaar.panchang.engine.panchang.PanchangUnavailableException
import app.parivaar.panchang.feature.aaj.AajEngine
import app.parivaar.panchang.feature.aaj.EngineAajEngine
import app.parivaar.panchang.feature.aaj.PpPerf
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/** The active home place with its zone and calc settings. [name] is only for widgets and only if opted in. */
class ActivePlace(
    val settings: AppSettings,
    val name: String,
    val place: Place,
    val zone: TimeZone,
    val offsetFor: (LocalDate) -> Int,
) {
    val calc: CalcSettings get() = settings.defaultCalc

    override fun toString(): String = "ActivePlace(redacted)"
}

/** The civil date of [now] at the home place. */
fun EngineAccess.today(a: ActivePlace, now: Instant): LocalDate = now.toLocalDateTime(a.zone).date

/** Festival definitions for [s]: the calendar's Ekadashi tradition filter, shared by reminders and widgets. */
internal fun defsFor(defs: List<FestivalDef>, s: AppSettings): List<FestivalDef> = defs.forTradition(s.ekadashi)

/**
 * The one engine instance of the app (shared with the Aaj screen). Calls are serialized by one lock ([onEngine]);
 * callers block, so call from a background thread, never the main thread.
 * Rule files come from packaged assets (`rules/`, copied from the repo-level `rules` dir at build time).
 */
@Singleton
@Suppress("TooManyFunctions") // M12 added the Bonds port; one shared engine, one lock
class EngineAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val placesRepo: PlaceRepository,
    private val vault: DataVault,
    private val resolver: Lazy<OffsetResolver>,
    private val profilesRepo: ProfileRepository,
) {
    /**
     * Calculators are not thread-safe. Every use, here and in [EngineAajEngine] (whose methods synchronize on
     * itself), locks the same monitor: the [Engine.aaj] instance. One ephemeris and one set of calculators per process.
     */
    private fun <T> onEngine(block: () -> T): T {
        val e = engine
        return synchronized(e.aaj) { block() }
    }

    private class Engine(
        val calc: PanchangCalculator,
        val festivals: FestivalEngine,
        val eclipses: EclipseSearch,
        val defs: List<FestivalDef>,
        val aaj: EngineAajEngine,
        val tables: PanchangTables,
        val sets: List<FestivalSet>,
        val calendar: CalendarEngine,
        val kundli: KundliEngine,
        val chart: ChartCalculator,
        val gochar: Gochar,
    )

    private val engine: Engine by lazy {
        PpPerf.time("engine.build(total)") { buildEngine() }
    }

    private fun buildEngine(): Engine {
        val eph = PpPerf.time("engine.ephemeris") { SelectingEphemeris(null, AstronomyEngineEphemeris()) }
        // Gowri and varjyam tables are not shipped yet: those windows stay "not available".
        val tables =
            PpPerf.time("engine.rules.tables") {
                PanchangRules.parse(asset("rules/durmuhurta.json"), asset("rules/choghadiya.json"))
            }
        val sets =
            PpPerf.time("engine.rules.festivals") {
                context.assets.list("rules/festivals").orEmpty().sorted()
                    .map { FestivalRules.parse(it, asset("rules/festivals/$it")) }
            }
        // Pan-Indian definitions only (region "all"); regional sets come with the region setting.
        val all = sets.flatMap { it.forRegion(REGION) }
        val ids = all.map { it.id }.toSet()
        val defs = all.filter { d -> (d.rule as? FestivalRule.Relative)?.let { it.to in ids } ?: true }
        val calc = PanchangCalculator(eph, tables)
        val fest = FestivalEngine(eph)
        val ecl = EclipseSearch(eph)
        val cal = CalendarCalculator(eph)
        val aaj = EngineAajEngine(calc, cal, fest, ecl, sets, eph)
        val recurring = sets.filter { it.file.startsWith("recurring") }.flatMap { s -> s.defs.map { it.id } }.toSet()
        // Same calculators as everything else; calls are serialised by the lock on `aaj` (see onEngine).
        val direct = DirectCalendarEngine(calc, cal, fest, ecl, ChartCalculator(eph), Gochar(eph), defs, recurring)
        // M10: Kundli over the same ephemeris and lock; rule files from the packaged assets.
        val kundli = DirectKundliEngine(
            ChartCalculator(eph), Vargas.parse(asset("rules/vargas.json")), YogaRules.parse(asset("rules/yogas.json")),
            NakshatraTable.parse(asset("rules/nakshatra.json")), Gochar(eph), FinderFamilyDates(FamilyDateFinder(eph)),
        )
        val calendar = EngineCalendarEngine(direct, aaj)
        return Engine(
            calc, fest, ecl, defs, aaj, tables, sets, calendar, EngineKundliEngine(kundli, aaj), ChartCalculator(eph),
            Gochar(eph),
        )
    }

    /** Builds the engine (ephemeris, rule files, festival sets) once, off the main thread. Call from app start. */
    fun warm() {
        onEngine { }
        // Class loading and JIT for Today's calculators, on a fixed non-personal point and date.
        val today = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        PpPerf.time("engine.warmup") { interactive.days(today, 1, Place(0.0, 0.0), CalcSettings()) { 0 } }
    }

    /**
     * The Aaj screen's own calculators (own ephemeris and caches) over the same immutable tables and festival sets.
     * Results are bit-identical to [engine]'s; the point is that Today never queues behind a 14-day reminder or widget
     * run holding the shared lock.
     */
    private val interactive: EngineAajEngine by lazy {
        val e = engine
        val eph = SelectingEphemeris(null, AstronomyEngineEphemeris())
        EngineAajEngine(
            PanchangCalculator(eph, e.tables), CalendarCalculator(eph), FestivalEngine(eph), EclipseSearch(eph),
            e.sets, eph,
        )
    }

    /** The Aaj screen's engine port (separate calculators, see [interactive]). */
    val aajEngine: AajEngine get() = interactive

    private fun asset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    /** The calendar tab's engine port: the one engine instance, every call under the single engine lock. */
    val calendarEngine: CalendarEngine get() = engine.calendar

    /** The Kundli tab's engine port: the one engine instance, every call under the single engine lock. */
    val kundliEngine: KundliEngine get() = engine.kundli

    /**
     * M12: the Bonds port over the same engine lock: koota tables, axis weights, traits from the Kundli engine and the
     * Bond Code codec (links only for [shareHost]). Blocking; nothing is logged.
     */
    fun bondEngine(shareHost: String): app.parivaar.panchang.feature.bonds.BondEngine =
        EngineBondEngine({ kundliEngine }, resolver.get(), ::asset, { engine.aaj }, BondCodeAdapter(shareHost))

    /** The shipped festival definitions (immutable), for the festival guide. */
    val festivalDefs: List<FestivalDef> get() = engine.defs

    /** Festival definitions that have vrat rules (Ekadashi included). */
    val vratIds: Set<String> get() = onEngine { engine.defs.filter { it.vratRules.isNotEmpty() }.map { it.id }.toSet() }

    /** True when the last [active] call found a home place whose time zone this device does not know. */
    @Volatile
    var homeZoneUnknown: Boolean = false
        private set

    /** Null until the vault is open and a home place is chosen. Blocking. */
    fun active(): ActivePlace? =
        runBlocking {
            if (vault.state.value != VaultState.Ready) return@runBlocking null
            val s = settingsRepo.settings.first()
            val id = s.homePlaceId ?: return@runBlocking null
            val saved = placesRepo.observeAll().first().items.firstOrNull { it.id == id } ?: return@runBlocking null
            val g = saved.place
            val ctx = g.tzContext()
            val r = resolver.get()
            // An unknown zone degrades to "no home place" (flagged), never a crash (Fable review item 2).
            val zone = zoneSafe(null) { TimeZone.of(g.zoneId).also { r.offsetAt(Instant.fromEpochSeconds(0), ctx) } }
            homeZoneUnknown = zone == null
            if (zone == null) return@runBlocking null
            val offset = { d: LocalDate ->
                when (val o = r.civilDayOffset(d, ctx)) {
                    is CivilDayOffset.Valid -> o.offset.offsetSeconds
                    CivilDayOffset.Nonexistent ->
                        r.offsetAt(d.atStartOfDayIn(TimeZone.UTC), ctx).offsetSeconds
                }
            }
            ActivePlace(s, g.name, Place(g.latDeg, g.lonDeg, g.elevationM ?: 0.0), zone, offset)
        }

    /** Panchang days from [first]; a day without a sunrise or sunset is skipped. */
    fun days(a: ActivePlace, first: LocalDate, count: Int): List<PanchangDay> =
        onEngine {
            (0 until count).mapNotNull { i ->
                val d = first.plus(DatePeriod(days = i))
                try {
                    engine.calc.day(d, a.offsetFor(d), a.place, a.calc)
                } catch (_: PanchangUnavailableException) {
                    null
                }
            }
        }

    /** Occurrences for reminders and widgets, with only the user's Ekadashi tradition (as Aaj and the calendar). */
    fun festivals(a: ActivePlace, first: LocalDate, last: LocalDate): List<FestivalOccurrence> =
        onEngine {
            engine.festivals.occurrences(defsFor(engine.defs, a.settings), first, last, a.place, a.calc, a.offsetFor)
        }

    /** Eclipses of local dates [first]..[last] that are visible from the place. */
    fun eclipses(a: ActivePlace, first: LocalDate, last: LocalDate): List<LocalEclipse> =
        onEngine {
            val from = first.atStartOfDayIn(a.zone)
            val to = last.plus(DatePeriod(days = 1)).atStartOfDayIn(a.zone)
            engine.eclipses.global(from, to).mapNotNull { engine.eclipses.local(it, a.place, a.calc) }
                .filter { it.visible != null }
        }

    /** Profiles while the vault is open (empty otherwise). Blocking; memory only. */
    fun profiles(): List<Profile> =
        runBlocking {
            if (vault.state.value != VaultState.Ready) return@runBlocking emptyList()
            profilesRepo.observeAll().first().items
        }

    /** The home place as the Kundli family-date input (D4), or null without one. Blocking. */
    fun familyHome(): CalInputsLite? =
        active()?.let { a ->
            CalInputsLite(a.place, a.zone, a.offsetFor, a.calc, a.settings.monthSystem, a.settings.homePlaceId)
        }

    /** Bare family dates for reminders (no names). */
    val familySource: ProfileFamilyDateSource by lazy {
        ProfileFamilyDateSource(::profiles, ::familyHome, kundliEngine)
    }

    /**
     * M11: runs [block] under the engine lock with a transit source at the home place and the panchang day start
     * (sunrise per settings; the engine's high-latitude fallback applies; a day with no sun events starts at local
     * midnight). Blocking; nothing is logged.
     */
    fun <T> rashifal(a: ActivePlace, block: (TransitSource, (LocalDate) -> Instant) -> T): T =
        onEngine {
            val src = EngineTransitSource(engine.chart, engine.gochar, a.place, a.calc)
            val starts = HashMap<LocalDate, Instant>()
            val dayStart = { d: LocalDate ->
                starts.getOrPut(d) {
                    try {
                        engine.calc.day(d, a.offsetFor(d), a.place, a.calc).sunrise
                    } catch (_: PanchangUnavailableException) {
                        d.atStartOfDayIn(a.zone)
                    }
                }
            }
            block(src, dayStart)
        }

    private companion object {
        const val REGION = "all"
    }
}
