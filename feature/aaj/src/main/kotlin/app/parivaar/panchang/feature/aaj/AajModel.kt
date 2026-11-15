package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.toEnginePlace
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.tzContext
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.LocalEclipse
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.PanchangError
import app.parivaar.panchang.engine.panchang.PanchangUnavailableException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** What is cached per [AajKey]: engine output only (never the header, which depends on `now`). */
data class AajLoaded(
    val days: List<PanchangDay>,
    val calendar: CalendarDay?,
    val festivals: List<FestivalOccurrence>?,
    val eclipse: LocalEclipse?,
)

/**
 * Aaj state holder. Engine work runs on [compute] (use a single-thread dispatcher), the UTC offset on [io].
 * Header and next window are recomputed from the cached days at every transition boundary; the 1 s [tick]
 * only drives countdown text.
 */
class AajModel(
    private val inputs: Flow<AajInputs?>,
    private val engine: AajEngine,
    private val offsets: OffsetResolver,
    private val cache: AajCache<AajLoaded>,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
) {
    private val _ui = MutableStateFlow<AajUi>(AajUi.Loading)
    val ui: StateFlow<AajUi> = _ui
    private val refreshes = MutableStateFlow(0)

    val tick: StateFlow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MS)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), clock.now())

    /** Hora planet lighting for the sky header; computed on [compute], cached per minute, never on a config change. */
    private val _sky = MutableStateFlow<HoraSky?>(null)
    val sky: StateFlow<HoraSky?> = _sky

    @Volatile private var skyPlace: Place? = null

    init {
        scope.launch { runSky() }
        // Inputs that do not change the calculation (theme, font scale, ...) compare equal and are skipped.
        scope.launch { combine(inputs.distinctUntilChanged(), refreshes) { i, _ -> i }.collectLatest { guarded(it) } }
    }

    fun refresh() { refreshes.value += 1 }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun runSky() {
        var last: Pair<app.parivaar.panchang.core.designsystem.planets.PlanetBody, Long>? = null
        val minutes = tick.map { it.epochSeconds / SECONDS_PER_MINUTE }.distinctUntilChanged()
        combine(ui, minutes) { u, minute -> u to minute }
            .collectLatest { (u, minute) ->
                val place = skyPlace
                val body = if (u is AajUi.Content && place != null) {
                    horaBodyAt(u.days, Instant.fromEpochSeconds(minute * SECONDS_PER_MINUTE))
                } else {
                    null
                }
                if (body == null || place == null) { _sky.value = null; last = null; return@collectLatest }
                if (last == body to minute) return@collectLatest
                _sky.value = try {
                    val at = Instant.fromEpochSeconds(minute * SECONDS_PER_MINUTE)
                    withContext(compute) { horaSkyOf(engine, body, at, place) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                last = body to minute
            }
    }

    // Any engine failure becomes the Failed state, never a crash.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun guarded(i: AajInputs?) {
        try {
            run(i)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.value = AajUi.Failed
        }
    }

    @Suppress("ReturnCount")
    private suspend fun run(i: AajInputs?) {
        PpPerf.mark("aaj.run.start")
        if (i == null) { skyPlace = null; _ui.value = AajUi.NoPlace; return }
        skyPlace = i.place.toEnginePlace()
        while (true) {
            val now = clock.now()
            val ctx = i.place.tzContext()
            val base = withContext(io) { PpPerf.time("aaj.offsetAt") { offsets.offsetAt(now, ctx).offsetSeconds } }
            val date = LocalDate.fromEpochDays(Math.floorDiv(now.epochSeconds + base, SECONDS_PER_DAY).toInt())
            val offs = withContext(io) { PpPerf.time("aaj.dayOffsets") { dayOffsets(i, date) } }
            if (offs == null) { _ui.value = AajUi.NonexistentDate; return }
            val offFn: (LocalDate) -> Int = { offs[it] ?: offs.getValue(date) }
            val hash = listOf(i.settings, i.region, i.monthSystem, i.ekadashi).hashCode()
            val key = AajKey(date, i.placeId, offs.getValue(date), hash)
            val loaded = cache.get(key) ?: load(i, date, offFn, key)
                ?: return
            publish(i, date, loaded, now)
            PpPerf.mark("aaj.firstContentPublished")
            if (loaded.festivals == null || loaded.calendar == null) {
                // Stored inside the block, so a cancelled run (new emission) does not throw the work away.
                val full = withContext(compute) { extras(i, date, offs, loaded).also { cache.put(key, it) } }
                publish(i, date, full, clock.now())
            }
            val latest = cache.get(key) ?: loaded
            val wake = withContext(io) {
                Transitions.nextBoundary(clock.now(), latest.days) { offsets.offsetAt(it, ctx).offsetSeconds }
            }
            delay((wake - clock.now()).inWholeMilliseconds.coerceAtLeast(MIN_WAIT_MS))
        }
    }

    private fun dayOffsets(i: AajInputs, date: LocalDate): Map<LocalDate, Int>? {
        val ctx = i.place.tzContext()
        val mine = (offsets.civilDayOffset(date, ctx) as? CivilDayOffset.Valid)?.offset?.offsetSeconds ?: return null
        val map = HashMap<LocalDate, Int>()
        map[date] = mine
        for (d in listOf(date.plus(DatePeriod(days = -1)), date.plus(DatePeriod(days = 1)))) {
            map[d] = (offsets.civilDayOffset(d, ctx) as? CivilDayOffset.Valid)?.offset?.offsetSeconds ?: mine
        }
        return map
    }

    /** Returns null after publishing PolarDay. */
    private suspend fun load(i: AajInputs, date: LocalDate, off: (LocalDate) -> Int, key: AajKey): AajLoaded? =
        withContext(compute) {
            val place = i.place.toEnginePlace()
            try {
                val days = PpPerf.time("aaj.days(total incl. lock wait)") { dayAndNeighbours(date, place, i, off) }
                AajLoaded(days, null, null, null).also { cache.put(key, it) }
            } catch (e: PanchangUnavailableException) {
                check(e.code == PanchangError.NO_SUN_DAY)
                val limbs = engine.limbSpans(date, off(date), place, i.settings)
                _ui.value = AajUi.PolarDay(date, limbs, proofOf(null, i))
                null
            }
        }

    /**
     * D-1, D and D+1, each fetched on its own. A neighbour without a sunrise or sunset is left out; if D itself has
     * none the engine's exception propagates and the caller shows the polar state.
     */
    private fun dayAndNeighbours(
        date: LocalDate, place: Place, i: AajInputs, off: (LocalDate) -> Int,
    ): List<PanchangDay> = listOf(-1, 0, 1).mapNotNull { k ->
        val d = date.plus(DatePeriod(days = k))
        try {
            engine.days(d, 1, place, i.settings, off).single()
        } catch (e: PanchangUnavailableException) {
            if (k == 0) throw e else null
        }
    }

    private fun extras(i: AajInputs, date: LocalDate, offs: Map<LocalDate, Int>, l: AajLoaded): AajLoaded {
        val place = i.place.toEnginePlace()
        val off: (LocalDate) -> Int = { offs[it] ?: offs.getValue(date) }
        val cal = PpPerf.time("aaj.calendar(total)") { engine.calendar(date, off(date), place, i.settings) }
        val f = PpPerf.time("aaj.festivals(total)") {
            engine.festivals(date, place, i.settings, i.region, i.ekadashi, off)
        }
        val e = PpPerf.time("aaj.eclipse(total)") { engine.eclipse(date, offs.getValue(date), place, i.settings) }
        return l.copy(calendar = cal, festivals = f, eclipse = e)
    }

    private fun publish(i: AajInputs, date: LocalDate, l: AajLoaded, now: Instant) {
        val today = l.days.first { it.date == date }
        val unavailable = today.unavailable.mapTo(HashSet()) { it.name } + UnavailableIds.ALWAYS
        _ui.value = AajUi.Content(
            date = date, days = l.days, calendar = l.calendar,
            header = HeaderRule.evaluate(now, l.days), next = NextWindowRule.next(now, l.days),
            festivals = l.festivals, eclipse = l.eclipse, unavailable = unavailable,
            highLatitude = today.flags, proof = proofOf(today, i), travel = i.travel,
        )
    }

    /** Technical ids only (backend, ayanamsa and convention names); the UI maps them to labels. */
    private fun proofOf(day: PanchangDay?, i: AajInputs) = ProofInfo(
        backend = day?.backend?.name ?: "",
        ayanamsa = i.settings.ayanamsa.name,
        sunrise = i.settings.sunrise.name,
        altitudeM = i.place.elevationM,
    )

    private companion object {
        const val DAYS = 3
        const val SECONDS_PER_DAY = 86_400L
        const val SECONDS_PER_MINUTE = 60L
        const val TICK_MS = 1_000L
        const val STOP_MS = 5_000L
        const val MIN_WAIT_MS = 1_000L
    }
}
