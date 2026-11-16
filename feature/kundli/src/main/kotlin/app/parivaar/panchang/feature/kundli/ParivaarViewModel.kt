package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.model.profile.Profile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** One upcoming row: the date plus days from today (home zone). */
data class UpcomingRow(val item: FamilyDateItem, val profile: Profile, val daysUntil: Int) {
    override fun toString(): String = "UpcomingRow(redacted)"
}

data class ParivaarState(
    val profiles: List<Profile> = emptyList(),
    val upcoming: List<UpcomingRow> = emptyList(),
    val loading: Boolean = true,
    val notes: Set<FamilyNote> = emptySet(),
) {
    override fun toString(): String = "ParivaarState(redacted)"
}

/** Sorted upcoming family dates within [windowDays] (screen 16). */
object Upcoming {
    fun rows(items: List<FamilyDateItem>, ps: List<Profile>, today: LocalDate): List<UpcomingRow> {
        val byId = ps.associateBy { it.id }
        return items.filter { it.date >= today }.mapNotNull { i -> byId[i.profileId]?.let { UpcomingRow(
            i, it, today.daysUntil(i.date),
        ) } }
            .sortedWith(compareBy({ it.daysUntil }, { it.item.kind.ordinal }))
    }
}

class ParivaarViewModel(
    profiles: Flow<List<Profile>>,
    home: Flow<CalInputsLite?>,
    private val engine: KundliEngine,
    private val clock: Clock,
    scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
    private val windowDays: Int = 400,
) {
    private val _state = MutableStateFlow(ParivaarState())
    val state: StateFlow<ParivaarState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(profiles, home) { ps, h -> ps to h }.collect { (ps, h) ->
                // Vault locked or no home place: nothing is retained on screen (rule 10).
                if (h == null || ps.isEmpty()) {
                    _state.value = ParivaarState(ps, emptyList(), loading = false)
                    return@collect
                }
                _state.value = _state.value.copy(profiles = ps, loading = true)
                val today = clock.now().toLocalDateTime(h.zone).date
                val last = today.plus(DatePeriod(days = windowDays))
                val result = withContext(compute) {
                    runCatching { engine.familyDates(ps, today, last, h) }
                        .getOrElse { FamilyDatesResult(emptyList(), setOf(FamilyNote.SOME_UNAVAILABLE)) }
                }
                _state.value = ParivaarState(
                    ps, Upcoming.rows(result.items, ps, today), loading = false, notes = result.notes,
                )
            }
        }
    }
}
