package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/**
 * Month state holder, one per activity (so a rotation never recomputes). A cached month is shown at once; a miss
 * shows [MonthUi.Loading] with the last grid, computes on [compute] and then prefetches the neighbours.
 * Inputs that do not change the month (theme, font scale) compare equal and do not recompute.
 */
@Suppress("TooManyFunctions")
class MonthModel(
    private val inputs: Flow<CalInputs?>,
    private val engine: CalendarEngine,
    private val cache: MonthCache,
    private val clock: Clock,
    private val firstDow: () -> DayOfWeek,
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
) {
    private val _ui = MutableStateFlow<MonthUi>(MonthUi.Loading(null))
    val ui: StateFlow<MonthUi> = _ui

    private val _selected = MutableStateFlow<LocalDate?>(null)
    val selected: StateFlow<LocalDate?> = _selected

    // null = the current month of the place's zone.
    private val shown = MutableStateFlow<YearMonth?>(null)

    private var last: MonthUi.Content? = null
    private var lastInputs: CalInputs? = null

    init {
        scope.launch {
            combine(inputs.distinctUntilChanged(), shown) { i, ym -> i to ym }.collectLatest { (i, ym) -> run(i, ym) }
        }
    }

    fun show(ym: YearMonth) { shown.value = ym }

    fun next() { shown.value = current().plus(1, DateTimeUnit.MONTH) }

    fun prev() { shown.value = current().plus(-1, DateTimeUnit.MONTH) }

    fun today() { shown.value = null }

    fun select(date: LocalDate?) { _selected.value = date }

    fun jumpTo(occ: FestivalOccurrence) {
        _selected.value = occ.date
        show(occ.date.yearMonth)
    }

    /** The detail of [date] in the month on screen (a leading or trailing grid day included), or null. */
    @Suppress("ReturnCount")
    fun detail(date: LocalDate): DayDetail? {
        val c = last ?: return null
        val i = lastInputs ?: return null
        val cm = cache.get(c.key) ?: return null
        val idx = (date.toEpochDays() - c.weeks.first().first().date.toEpochDays()).toInt()
        if (idx !in cm.bundle.pan.indices) return null
        val b = cm.bundle
        val fests = b.fests.filter { it.date == date && MonthBuilder.keep(it, b, i) }
        val eclipses = b.eclipses.filter { e -> MonthBuilder.eclipseDate(e, i) == date }
        val pan = b.pan[idx]
        val offset = pan?.utcOffsetSeconds ?: i.offsetFor(date)
        return DayDetail(date, pan, b.cal[idx + 1], fests, b.defs, eclipses, offset, cm.proof)
    }

    private fun current(): YearMonth =
        shown.value ?: (last?.key?.ym ?: clock.now().toLocalDateTime(TimeZone.UTC).date.yearMonth)

    @Suppress("TooGenericExceptionCaught", "ReturnCount", "SwallowedException")
    private suspend fun run(i: CalInputs?, requested: YearMonth?) {
        if (i == null) { last = null; _ui.value = MonthUi.NoPlace; return }
        val today = clock.now().toLocalDateTime(i.zone).date
        val ym = requested ?: today.yearMonth
        val dow = firstDow()
        try {
            val cached = cache.get(keyOf(i, ym))?.takeIf { it.firstDow == dow }
            if (cached != null) {
                publish(i, ym, today, dow, cached)
            } else {
                _ui.value = MonthUi.Loading(last)
                val fresh = withContext(compute) { load(i, ym, dow) }
                publish(i, ym, today, dow, fresh)
            }
            coroutineScope { launch(compute) { prefetch(i, ym, dow) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NonexistentCivilDayException) {
            _ui.value = MonthUi.Error(nonexistent = true)
        } catch (e: Exception) {
            _ui.value = MonthUi.Error(nonexistent = false)
        }
    }

    private fun keyOf(i: CalInputs, ym: YearMonth) =
        MonthKey(ym, i.placeId, i.settingsHash, i.monthSystem, i.ekadashi, i.view)

    // Stored inside the block, so a cancelled run (a newer request) does not throw finished work away.
    private fun load(i: CalInputs, ym: YearMonth, dow: DayOfWeek): CachedMonth {
        val n = MonthBuilder.rows(ym, dow) * MonthBuilder.DAYS_PER_WEEK
        val bundle = engine.month(MonthBuilder.gridFirst(ym, dow), n, i)
        val proof = ProofInfo(
            backend = bundle.pan.firstNotNullOfOrNull { it?.backend }?.name ?: "",
            ayanamsa = i.calc.ayanamsa.name,
            sunrise = i.calc.sunrise.name,
            altitudeM = i.place.elevationM,
        )
        return CachedMonth(bundle, dow, proof).also { cache.put(keyOf(i, ym), it) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun prefetch(i: CalInputs, ym: YearMonth, dow: DayOfWeek) {
        for (step in listOf(1, -1)) {
            val m = ym.plus(step, DateTimeUnit.MONTH)
            if (cache.get(keyOf(i, m))?.firstDow != dow) quietly { load(i, m, dow) }
        }
    }

    // A prefetch failure surfaces when the user opens that month.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
    }

    private fun publish(i: CalInputs, ym: YearMonth, today: LocalDate, dow: DayOfWeek, c: CachedMonth) {
        val content = MonthBuilder.build(ym, today, dow, i, c.bundle, keyOf(i, ym), c.proof)
        last = content
        lastInputs = i
        _ui.value = content
    }
}
