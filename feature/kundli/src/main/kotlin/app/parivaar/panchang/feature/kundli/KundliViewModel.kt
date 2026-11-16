package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.designsystem.chart.ChartStyle
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.toEnginePlace
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.tzContext
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.engine.jyotish.DashaPeriod
import app.parivaar.panchang.engine.jyotish.Graha
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

enum class ChartTab { D1, D9 }

enum class KundliTab { CHART, PLANETS, DASHA, YOGAS, GOCHAR }

data class KundliState(
    val profiles: List<Profile> = emptyList(),
    val selectedId: RecordId? = null,
    val bundle: KundliBundle? = null,
    val noon: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val chart: ChartTab = ChartTab.D1,
    val style: ChartStyle = ChartStyle.NORTH,
    val tab: KundliTab = KundliTab.CHART,
    val expanded: Set<List<Graha>> = emptySet(),
    val children: Map<List<Graha>, List<DashaPeriod<Graha>>> = emptyMap(),
    val notes: Set<KundliNote> = emptySet(),
    val zone: TimeZone = TimeZone.UTC,
) {
    val selected: Profile? get() = profiles.firstOrNull { it.id == selectedId }

    override fun toString(): String = "KundliState(redacted)"
}

sealed interface KundliEvent {
    data class Select(val id: RecordId) : KundliEvent
    data class Chart(val tab: ChartTab) : KundliEvent
    data class Style(val style: ChartStyle) : KundliEvent
    data class Tab(val tab: KundliTab) : KundliEvent
    data class ToggleDasha(val lords: List<Int>) : KundliEvent
}

/** Offset notes from the stored birth offset (rule 1). */
fun offsetNotes(p: Profile): Set<KundliNote> = buildSet {
    val o = p.place.offset
    if (OffsetFlag.HISTORICAL_VERIFY in o.flags) add(KundliNote.HISTORICAL_OFFSET)
    if (OffsetFlag.PRE_STANDARD_LMT in o.flags || o.source == OffsetSource.LMT) add(KundliNote.LMT_OFFSET)
    if (p.place.manualOffset != null || o.source == OffsetSource.MANUAL) add(KundliNote.MANUAL_OFFSET)
    if (OffsetFlag.AMBIGUOUS in o.flags) add(KundliNote.DST_CHOSEN)
}

/**
 * Kundli tab state holder. Engine calls run on [compute] (Dispatchers.Default in the app; the engine port takes the
 * engine lock). Bundles are cached in memory only ([cache]); nothing here logs or stores personal data.
 */
class KundliViewModel(
    profiles: Flow<List<Profile>>,
    private val homeZone: () -> TimeZone?,
    private val homePlaceId: () -> RecordId?,
    private val engine: KundliEngine,
    private val offsets: OffsetResolver,
    val cache: KundliCache<KundliBundle>,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(KundliState())
    val state: StateFlow<KundliState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        scope.launch {
            profiles.collect { list ->
                if (list.isEmpty()) {
                    // Vault locked or no profiles: drop profiles, bundles and dasha children (rule 10).
                    job?.cancel()
                    _state.update { KundliState(chart = it.chart, style = it.style, tab = it.tab) }
                    return@collect
                }
                val prev = _state.value
                val sel = prev.selectedId?.takeIf { id -> list.any { it.id == id } }
                    ?: list.firstOrNull { it.relation == Relation.SELF }?.id ?: list.firstOrNull()?.id
                _state.update { it.copy(profiles = list, selectedId = sel) }
                val changed = list.firstOrNull { it.id == sel }?.updatedAt != prev.selected?.updatedAt
                if (sel != prev.selectedId || changed || prev.bundle == null) load()
            }
        }
    }

    fun onEvent(e: KundliEvent) {
        when (e) {
            is KundliEvent.Select -> {
                _state.update { it.copy(selectedId = e.id, expanded = emptySet(), children = emptyMap()) }
                load()
            }
            is KundliEvent.Chart -> _state.update { it.copy(chart = e.tab) }
            is KundliEvent.Style -> _state.update { it.copy(style = e.style) }
            is KundliEvent.Tab -> _state.update { it.copy(tab = e.tab) }
            is KundliEvent.ToggleDasha -> toggle(e.lords.map { Graha.entries[it] })
        }
    }

    /** Evicts a deleted or edited profile's bundles (rule 10). */
    fun evict(id: RecordId) = cache.evict(id)

    private fun request(p: Profile): ChartRequest {
        val noon = p.place.birthInstant == null
        val instant = p.place.birthInstant
            ?: offsets.localToInstant(
                LocalDateTime(
                    p.birth.date, LocalTime(NOON, 0),
                ), p.place.place.tzContext(), AmbiguityChoice.EARLIER, p.place.manualOffset,
            ).instant
        return ChartRequest(p.id, p.updatedAt, instant, noon, p.place.place.toEnginePlace(), p.calc, clock.now())
    }

    fun load() {
        val p = _state.value.selected ?: return
        job?.cancel()
        _state.update { it.copy(loading = true, failed = false) }
        job = scope.launch {
            val result = withContext(compute) {
                runCatching {
                    val r = request(p)
                    // The local day is in the key so current dasha, Sade Sati and gochar refresh after midnight.
                    val day = r.now.toLocalDateTime(homeZone() ?: TimeZone.UTC).date
                    val key = BundleKey(
                        p.id, p.updatedAt.toEpochMilliseconds(), p.calc.hashCode(), r.noonChart, homePlaceId(), day,
                    )
                    val b = cache.get(key) ?: engine.bundle(r).also { cache.put(key, it) }
                    val expand = DashaRows.autoExpand(b.currentDasha)
                    val kids = expand.associateWith { path -> engine.dasha(r, path) }
                    Triple(r, b, kids)
                }
            }
            val zone = homeZone() ?: TimeZone.UTC
            result.onSuccess { (r, b, kids) ->
                _state.update {
                    it.copy(
                        bundle = b,
                        noon = r.noonChart,
                        loading = false,
                        failed = false,
                        notes = b.notes + offsetNotes(p),
                        expanded = kids.keys, children = kids, zone = zone,
                        style = if (r.noonChart && it.style == ChartStyle.NORTH) ChartStyle.SOUTH else it.style,
                    )
                }
            }.onFailure { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.update { it.copy(loading = false, failed = true, bundle = null) }
            }
        }
    }

    private fun toggle(path: List<Graha>) {
        val s = _state.value
        if (path in s.expanded) {
            val under = { p: List<Graha> -> p.size >= path.size && p.subList(0, path.size) == path }
            _state.update { st -> st.copy(expanded = st.expanded.filterNot(under).toSet()) }
            return
        }
        val p = s.selected ?: return
        scope.launch {
            val kids = s.children[path] ?: withContext(compute) { runCatching { engine.dasha(
                request(p), path,
            ) }.getOrDefault(emptyList()) }
            _state.update { it.copy(expanded = it.expanded + setOf(path), children = it.children + (path to kids)) }
        }
    }

    private companion object {
        const val NOON = 12
    }
}
