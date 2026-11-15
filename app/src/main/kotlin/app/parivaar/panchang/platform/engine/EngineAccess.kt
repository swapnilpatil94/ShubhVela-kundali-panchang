package app.parivaar.panchang.platform.engine

import android.content.Context
import app.parivaar.panchang.core.data.DataVault
import app.parivaar.panchang.core.data.PlaceRepository
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.tzContext
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.astro.SelectingEphemeris
import app.parivaar.panchang.engine.calendar.CalendarCalculator
import app.parivaar.panchang.engine.jyotish.ChartCalculator
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.feature.calendar.CalendarEngine
import app.parivaar.panchang.feature.calendar.DirectCalendarEngine
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

/**
 * The one engine instance of the app (shared with the Aaj screen). Calls are serialized by one lock ([onEngine]);
 * callers block, so call from a background thread, never the main thread.
 * Rule files come from packaged assets (`rules/`, copied from the repo-level `rules` dir at build time).
 */
@Singleton
class EngineAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val placesRepo: PlaceRepository,
    private val vault: DataVault,
    private val resolver: Lazy<OffsetResolver>,
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
        return Engine(calc, fest, ecl, defs, aaj, tables, sets, EngineCalendarEngine(direct, aaj))
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

    /** The shipped festival definitions (immutable), for the festival guide. */
    val festivalDefs: List<FestivalDef> get() = engine.defs

    /** Festival definitions that have vrat rules (Ekadashi included). */
    val vratIds: Set<String> get() = onEngine { engine.defs.filter { it.vratRules.isNotEmpty() }.map { it.id }.toSet() }

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
            val offset = { d: LocalDate ->
                when (val o = r.civilDayOffset(d, ctx)) {
                    is CivilDayOffset.Valid -> o.offset.offsetSeconds
                    CivilDayOffset.Nonexistent ->
                        r.offsetAt(d.atStartOfDayIn(TimeZone.UTC), ctx).offsetSeconds
                }
            }
            ActivePlace(s, g.name, Place(g.latDeg, g.lonDeg, g.elevationM ?: 0.0), TimeZone.of(g.zoneId), offset)
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

    fun festivals(a: ActivePlace, first: LocalDate, last: LocalDate): List<FestivalOccurrence> =
        onEngine { engine.festivals.occurrences(engine.defs, first, last, a.place, a.calc, a.offsetFor) }

    /** Eclipses of local dates [first]..[last] that are visible from the place. */
    fun eclipses(a: ActivePlace, first: LocalDate, last: LocalDate): List<LocalEclipse> =
        onEngine {
            val from = first.atStartOfDayIn(a.zone)
            val to = last.plus(DatePeriod(days = 1)).atStartOfDayIn(a.zone)
            engine.eclipses.global(from, to).mapNotNull { engine.eclipses.local(it, a.place, a.calc) }
                .filter { it.visible != null }
        }

    fun today(a: ActivePlace, now: Instant): LocalDate = now.toLocalDateTime(a.zone).date

    private companion object {
        const val REGION = "all"
    }
}
