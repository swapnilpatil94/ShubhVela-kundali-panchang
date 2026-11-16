package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.data.newRecordId
import app.parivaar.panchang.core.geo.OffsetPickerModel
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.PlaceHit
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.geo.tzContext
import app.parivaar.panchang.core.model.profile.BirthData
import app.parivaar.panchang.core.model.profile.FamilyPrefs
import app.parivaar.panchang.core.model.profile.PassingData
import app.parivaar.panchang.core.model.profile.PlaceSnapshot
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.engine.CalcSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Instant

/** A clock time the zone skipped: Save is blocked (rule 1). */
fun isGap(o: ResolvedOffset): Boolean =
    o.flags.any { it == OffsetFlag.GAP_SHIFTED || it == OffsetFlag.NONEXISTENT_DATE }

private fun EditorState.failed(): EditorState = copy(errors = errors + EditorError.SAVE_FAILED)

enum class EditorError { NAME, DATE, TIME, PLACE, GAP_TIME, OFFSET, PASSING, SAVE_FAILED }

/** Offset resolution shown in the editor (rule 1): offset, its source and flags; [alternative] for a DST fold. */
data class OffsetView(
    val offset: ResolvedOffset, val instant: Instant?, val earlierSeconds: Int?, val laterSeconds: Int?,
) {
    override fun toString(): String = "OffsetView(redacted)"
}

data class EditorState(
    val editingId: String? = null,
    val name: String = "",
    val relation: Relation = Relation.PARENT,
    val dateText: String = "",
    val timeText: String = "",
    val timeUnknown: Boolean = false,
    val query: String = "",
    val hits: List<PlaceHit> = emptyList(),
    val place: GeoPlace? = null,
    val manualText: String = "",
    val ambiguity: AmbiguityChoice = AmbiguityChoice.EARLIER,
    val resolved: OffsetView? = null,
    val passingText: String = "",
    val prefs: FamilyPrefs = FamilyPrefs(),
    val errors: Set<EditorError> = emptySet(),
    val confirmDelete: Boolean = false,
    val done: Boolean = false,
) {
    val ambiguous: Boolean get() = resolved?.offset?.flags?.contains(OffsetFlag.AMBIGUOUS) == true
    val gap: Boolean get() = resolved?.offset?.let(::isGap) == true
    val canSave: Boolean get() = errors.isEmpty() && !gap && place != null && name.isNotBlank()

    override fun toString(): String = "EditorState(redacted)"
}

/**
 * Add/edit/delete one profile through the encrypted [ProfileRepository] only (acceptance 3). Geo calls run on [io].
 * Birth data never leaves memory except into the repository; nothing is logged.
 */
@Suppress("TooManyFunctions")
class ProfileEditorViewModel(
    private val repo: ProfileRepository,
    private val search: PlaceSearch,
    private val offsets: OffsetResolver,
    private val defaultCalc: () -> CalcSettings,
    private val onDeleted: (Profile) -> Unit,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val rng: SecureRandom = SecureRandom(),
) {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    private var original: Profile? = null
    private var searchJob: Job? = null

    fun start(p: Profile?) {
        original = p
        _state.value = if (p == null) {
            EditorState()
        } else {
            EditorState(
                editingId = p.id.hex, name = p.displayName, relation = p.relation, dateText = p.birth.date.toString(),
                timeText = p.birth.time?.let { "%02d:%02d".format(
                    it.hour, it.minute,
                ) } ?: "", timeUnknown = !p.birth.timeKnown,
                place = p.place.place, query = p.place.place.name, ambiguity = p.place.ambiguity,
                manualText = p.place.manualOffset?.let { OffsetPickerModel.format(it.seconds) } ?: "",
                passingText = p.passing?.date?.toString() ?: "", prefs = p.familyPrefs,
            )
        }
        resolve()
    }

    fun onName(v: String) = edit { it.copy(name = v.take(NAME_MAX)) }
    fun onRelation(r: Relation) = edit { it.copy(
        relation = r, passingText = if (r == Relation.SELF) "" else it.passingText,
    ) }
    fun onDate(v: String) = edit { it.copy(dateText = v) }
    fun onTime(v: String) = edit { it.copy(timeText = v) }
    fun onTimeUnknown(v: Boolean) = edit { it.copy(timeUnknown = v) }
    fun onManual(v: String) = edit { it.copy(manualText = v) }
    fun onAmbiguity(c: AmbiguityChoice) = edit { it.copy(ambiguity = c) }
    fun onPassing(v: String) = edit { it.copy(passingText = v) }
    fun onPrefs(p: FamilyPrefs) = _state.update { it.copy(prefs = p) }
    fun askDelete(v: Boolean) = _state.update { it.copy(confirmDelete = v) }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.trim().length < 2) return _state.update { it.copy(hits = emptyList()) }
        searchJob = scope.launch {
            val hits = withContext(io) { runCatching { search.search(q.trim(), HITS) }.getOrDefault(emptyList()) }
            _state.update { it.copy(hits = hits) }
        }
    }

    fun onPick(p: GeoPlace) = edit { it.copy(place = p, query = p.name, hits = emptyList()) }

    private fun edit(f: (EditorState) -> EditorState) {
        _state.update(f)
        resolve()
    }

    private fun parseDate(t: String): LocalDate? =
        runCatching { LocalDate.parse(t.trim()) }.getOrNull()?.takeIf { it.year in YEAR_MIN..YEAR_MAX }

    private fun parseTime(t: String): LocalTime? =
        runCatching { LocalTime.parse(t.trim()) }.getOrNull()?.let { LocalTime(it.hour, it.minute, it.second) }

    private fun manual(s: EditorState): Pair<ManualOffset?, Boolean> {
        if (s.manualText.isBlank()) return null to true
        return when (val r = OffsetPickerModel.parse(s.manualText)) {
            is OffsetPickerModel.Result.Ok -> r.offset to true
            is OffsetPickerModel.Result.Error -> null to false
        }
    }

    /** Validates and resolves the offset synchronously (pure geo maths over the bundled tz data). */
    private fun resolve() {
        val s = _state.value
        val date = parseDate(s.dateText)
        val time = if (s.timeUnknown) null else parseTime(s.timeText)
        val (manual, manualOk) = manual(s)
        val errs = fieldErrors(s, date, time, manualOk)
        val place = s.place
        val resolvable = date != null && place != null && manualOk
        val view = if (manualOk && date != null && place != null) {
            runCatching { offsetView(place, date, time, s.ambiguity, manual) }.getOrNull()
        } else {
            null
        }
        if (resolvable && view == null) errs += EditorError.PLACE
        if (view != null && isGap(view.offset)) errs += EditorError.GAP_TIME
        _state.update { it.copy(errors = errs, resolved = view) }
    }

    private fun fieldErrors(
        s: EditorState,
        date: LocalDate?,
        time: LocalTime?,
        manualOk: Boolean,
    ): MutableSet<EditorError> {
        val errs = mutableSetOf<EditorError>()
        if (s.name.isBlank()) errs += EditorError.NAME
        if (date == null) errs += EditorError.DATE
        if (!s.timeUnknown && time == null) errs += EditorError.TIME
        if (s.place == null) errs += EditorError.PLACE
        if (!manualOk) errs += EditorError.OFFSET
        if (s.passingText.isNotBlank() && s.relation != Relation.SELF) {
            val pd = parseDate(s.passingText)
            if (pd == null || (date != null && pd < date)) errs += EditorError.PASSING
        }
        return errs
    }

    private fun offsetView(
        place: GeoPlace, date: LocalDate, time: LocalTime?, choice: AmbiguityChoice, manual: ManualOffset?,
    ): OffsetView? {
        val ctx = place.tzContext()
        if (time == null) {
            return when (val r = offsets.civilDayOffset(date, ctx, manual)) {
                is CivilDayOffset.Valid -> OffsetView(r.offset, null, null, null)
                CivilDayOffset.Nonexistent -> null
            }
        }
        val local = LocalDateTime(date, time)
        val e = offsets.localToInstant(local, ctx, AmbiguityChoice.EARLIER, manual)
        val r = if (choice == AmbiguityChoice.EARLIER) e else offsets.localToInstant(local, ctx, choice, manual)
        val ambiguous = OffsetFlag.AMBIGUOUS in e.offset.flags
        val later = if (ambiguous) offsets.localToInstant(
            local, ctx, AmbiguityChoice.LATER, manual,
        ).offset.offsetSeconds else null
        return OffsetView(r.offset, r.instant, if (ambiguous) e.offset.offsetSeconds else null, later)
    }

    /** Builds the profile, or null when Save is blocked (gap time, invalid field). */
    fun build(): Profile? {
        resolve()
        val s = _state.value
        val date = parseDate(s.dateText)
        val time = if (s.timeUnknown) null else parseTime(s.timeText)
        val view = s.resolved
        val place = s.place
        val ready = s.canSave && (s.timeUnknown || time != null) && view != null
        if (!ready || date == null || place == null) return null
        checkNotNull(view)
        val now = clock.now()
        val o = original
        val passing = if (s.relation == Relation.SELF) null else parseDate(s.passingText)?.let { PassingData(it, null) }
        return Profile(
            id = o?.id ?: newRecordId(rng),
            displayName = s.name.trim(),
            relation = s.relation,
            origin = o?.origin ?: ProfileOrigin.MANUAL_OTHER,
            birth = BirthData(date, time, time != null),
            place = PlaceSnapshot(
                place, view.offset, manual(s).first, s.ambiguity, view.instant.takeIf { time != null },
            ),
            calc = o?.calc ?: defaultCalc(),
            notes = o?.notes,
            createdAt = o?.createdAt ?: now,
            updatedAt = now,
            passing = passing,
            familyPrefs = s.prefs,
        )
    }

    fun save() {
        val p = build() ?: return
        scope.launch {
            val r = withContext(io) { repo.upsert(p) }
            _state.update { if (r is DataResult.Ok) it.copy(done = true) else it.failed() }
        }
    }

    fun delete() {
        val p = original ?: return
        scope.launch {
            val r = withContext(io) { repo.delete(p.id) }
            if (r is DataResult.Ok) onDeleted(p)
            _state.update { if (r is DataResult.Ok) it.copy(done = true, confirmDelete = false) else it.failed() }
        }
    }

    private companion object {
        const val NAME_MAX = 60
        const val HITS = 8
        const val YEAR_MIN = 1800
        const val YEAR_MAX = 2200
    }
}
