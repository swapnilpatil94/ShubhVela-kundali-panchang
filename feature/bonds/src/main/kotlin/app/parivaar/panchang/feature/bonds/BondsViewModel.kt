package app.parivaar.panchang.feature.bonds

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import kotlinx.coroutines.CancellationException
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Clock

/** The pages of the Bonds tab (switched in place, so the tab keeps the dock). */
enum class BondsPage { LIST, ADD, PREVIEW, RESULT, SHARE, MATCH }

data class BondRow(val id: RecordId, val name: String, val kind: BondKind, val fromProfile: Boolean) {
    override fun toString(): String = "BondRow(redacted)"
}

/** A family profile that can become a bond (traits computed locally; never shareable). */
data class ProfileChoice(val id: RecordId, val name: String) {
    override fun toString(): String = "ProfileChoice(redacted)"
}

enum class AddError { INVALID_CODE, NOT_COMPUTABLE, SAVE_FAILED, UNAVAILABLE }

/** "Add bond?" preview: nothing is saved until the user confirms. */
data class PreviewUi(val traits: BondTraits, val kind: BondKind, val profileRef: RecordId?) {
    override fun toString(): String = "PreviewUi(redacted)"
}

enum class ResultStatus { LOADING, SHOWN, NO_SELF, UNAVAILABLE }

data class ResultUi(
    val bondId: RecordId?,
    val nameA: String?,
    val nameB: String,
    val kind: BondKind,
    val status: ResultStatus,
    val outcome: MatchOutcome? = null,
    val manglikKey: String? = null,
    /** False for bonds made from a family or manual profile (rule 14). */
    val cardShareAllowed: Boolean = false,
) {
    override fun toString(): String = "ResultUi(redacted)"
}

enum class ShareStatus { NO_SELF, LOADING, READY, UNAVAILABLE }

/** The share preview: every field that goes into the code, the lagna toggle (default off) and forced initials. */
data class ShareUi(
    val status: ShareStatus,
    val includeLagna: Boolean = false,
    val initialsForced: Boolean = false,
    val traits: BondTraits? = null,
    val code: String? = null,
) {
    val canShare: Boolean get() = status == ShareStatus.READY && code != null

    override fun toString(): String = "ShareUi(redacted)"
}

/** One side of an F1 marriage match: the user, a family profile or a saved bond. */
data class MatchCandidate(val key: String, val name: String?) {
    override fun toString(): String = "MatchCandidate(redacted)"
}

data class MatchUi(
    val candidates: List<MatchCandidate> = emptyList(),
    val a: String? = null,
    val b: String? = null,
    val roles: MatchRoles = MatchRoles.A_GROOM,
    val result: ResultUi? = null,
) {
    override fun toString(): String = "MatchUi(redacted)"
}

data class BondsState(
    /** True while the vault is locked: no bond, profile or result is held in memory. */
    val locked: Boolean = true,
    val page: BondsPage = BondsPage.LIST,
    val bonds: List<BondRow> = emptyList(),
    val profiles: List<ProfileChoice> = emptyList(),
    val hasSelf: Boolean = false,
    val code: String = "",
    val kind: BondKind = BondKind.BESTIE,
    val busy: Boolean = false,
    val addError: AddError? = null,
    val preview: PreviewUi? = null,
    val result: ResultUi? = null,
    val share: ShareUi? = null,
    val match: MatchUi = MatchUi(),
    val deleteFailed: Boolean = false,
) {
    override fun toString(): String = "BondsState(redacted)"
}

sealed interface BondsEvent {
    data object OpenAdd : BondsEvent
    data object Back : BondsEvent
    data class Code(val text: String) : BondsEvent
    data class Kind(val kind: BondKind) : BondsEvent
    data object PreviewCode : BondsEvent
    data class FromProfile(val id: RecordId) : BondsEvent
    data object ConfirmAdd : BondsEvent
    data class Open(val id: RecordId) : BondsEvent
    data class ChangeKind(val kind: BondKind) : BondsEvent
    data class Delete(val id: RecordId) : BondsEvent
    data object OpenShare : BondsEvent
    data class Lagna(val include: Boolean) : BondsEvent
    data object OpenMatch : BondsEvent
    data class MatchPick(val a: String?, val b: String?, val roles: MatchRoles) : BondsEvent
}

/**
 * Bonds tab state holder (M12). Inputs come from [data] (null while the vault is locked: everything is cleared, an
 * explicit empty state, never a pending flow). Engine and codec calls run on [compute]; nothing is logged, and
 * nothing leaves the device here: the share text is built on screen from the code the user chooses to share.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class BondsViewModel(
    data: Flow<BondsData?>,
    private val store: BondStore,
    private val engine: BondEngine,
    private val newId: () -> RecordId,
    private val today: () -> LocalDate,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(BondsState())
    val state: StateFlow<BondsState> = _state.asStateFlow()

    private var current: BondsData? = null
    @Volatile
    private var selfCache: Pair<Pair<RecordId, kotlin.time.Instant>, BondTraits?>? = null
    private var job: Job? = null

    init {
        scope.launch {
            data.collect { d ->
                if (d == null) lock() else onData(d)
            }
        }
    }

    private fun lock() {
        job?.cancel()
        current = null
        selfCache = null
        _state.value = BondsState(locked = true)
    }

    private fun onData(d: BondsData) {
        current = d
        val self = selfOf(d.profiles)
        if (self == null || selfCache?.first?.first != self.id) selfCache = null
        val rows = d.bonds.sortedByDescending { it.createdAt }
            .map { BondRow(it.id, it.traits.name, BondKind.of(it.kindKey), it.profileRef != null) }
        val choices = d.profiles.filter { it.id != self?.id && it.passing == null }
            .mapNotNull { p -> BondNames.clip(p.displayName)?.let { ProfileChoice(p.id, it) } }
        _state.update { s ->
            val stillOpen = s.result?.bondId?.let { id -> d.bonds.any { it.id == id } } ?: true
            s.copy(
                locked = false, bonds = rows, profiles = choices, hasSelf = self != null,
                page = if (!stillOpen && s.page == BondsPage.RESULT) BondsPage.LIST else s.page,
                result = if (stillOpen) s.result else null,
                match = s.match.copy(candidates = candidates(d, self)),
            )
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun onEvent(e: BondsEvent) {
        if (_state.value.locked) return
        when (e) {
            BondsEvent.OpenAdd -> _state.update {
                it.copy(page = BondsPage.ADD, code = "", addError = null, preview = null, kind = BondKind.BESTIE)
            }
            BondsEvent.Back -> back()
            is BondsEvent.Code -> _state.update { it.copy(code = e.text.take(MAX_INPUT), addError = null) }
            is BondsEvent.Kind -> _state.update { s -> s.copy(kind = e.kind, preview = s.preview?.copy(kind = e.kind)) }
            BondsEvent.PreviewCode -> previewCode()
            is BondsEvent.FromProfile -> previewProfile(e.id)
            BondsEvent.ConfirmAdd -> confirmAdd()
            is BondsEvent.Open -> open(e.id)
            is BondsEvent.ChangeKind -> changeKind(e.kind)
            is BondsEvent.Delete -> delete(e.id)
            BondsEvent.OpenShare -> openShare(includeLagna = false)
            is BondsEvent.Lagna -> openShare(includeLagna = e.include)
            BondsEvent.OpenMatch -> _state.update { it.copy(page = BondsPage.MATCH,
                match = it.match.copy(result = null)) }
            is BondsEvent.MatchPick -> matchPick(e)
        }
    }

    private fun back() {
        job?.cancel()
        _state.update { s ->
            when (s.page) {
                BondsPage.PREVIEW -> s.copy(page = BondsPage.ADD, preview = null, busy = false)
                else -> s.copy(page = BondsPage.LIST, preview = null, result = null, share = null, busy = false)
            }
        }
    }

    private fun previewCode() {
        val input = _state.value.code.trim()
        if (input.isEmpty() || input.length > MAX_INPUT) {
            _state.update { it.copy(addError = AddError.INVALID_CODE) }
            return
        }
        offMain {
            val t = runCatching { engine.decode(input) }.getOrNull()
            _state.update { s ->
                if (t == null) {
                    s.copy(busy = false, addError = AddError.INVALID_CODE)
                } else {
                    s.copy(busy = false, page = BondsPage.PREVIEW, preview = PreviewUi(t, s.kind, null))
                }
            }
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun previewProfile(id: RecordId) {
        val d = current ?: return
        d.bonds.firstOrNull { it.profileRef == id }?.let { return open(it.id) }
        val p = d.profiles.firstOrNull { it.id == id } ?: return
        val name = BondNames.clip(p.displayName) ?: return
        offMain {
            val t = runCatching { engine.traitsOf(p, name) }.getOrNull()
            _state.update { s ->
                if (t == null) {
                    s.copy(busy = false, addError = AddError.NOT_COMPUTABLE)
                } else {
                    s.copy(busy = false, page = BondsPage.PREVIEW, preview = PreviewUi(t, s.kind, p.id))
                }
            }
        }
    }

    private fun confirmAdd() {
        val pv = _state.value.preview ?: return
        val bond = Bond(newId(), pv.traits, pv.kind.key, clock.now(), pv.profileRef, BOND_CODE_VERSION)
        _state.update { it.copy(busy = true) }
        job?.cancel()
        job = scope.launch {
            val ok = runCatching { store.save(bond) }.getOrElse { if (it is CancellationException) throw it else false }
            if (!ok) {
                _state.update { it.copy(busy = false, addError = AddError.SAVE_FAILED) }
                return@launch
            }
            _state.update { it.copy(busy = false, preview = null, code = "") }
            showResult(bond)
        }
    }

    private fun open(id: RecordId) {
        val b = current?.bonds?.firstOrNull { it.id == id } ?: return
        showResult(b)
    }

    private fun changeKind(kind: BondKind) {
        val id = _state.value.result?.bondId ?: return
        val b = current?.bonds?.firstOrNull { it.id == id } ?: return
        val updated = b.copy(kindKey = kind.key)
        showResult(updated)
        scope.launch { runCatching { store.save(updated) } }
    }

    private fun showResult(b: Bond) {
        val d = current ?: return
        val self = selfOf(d.profiles)
        val kind = BondKind.of(b.kindKey)
        val base = ResultUi(b.id, null, b.traits.name, kind, ResultStatus.LOADING,
            cardShareAllowed = b.profileRef == null)
        _state.update { it.copy(page = BondsPage.RESULT, result = base) }
        if (self == null) {
            _state.update { it.copy(result = base.copy(status = ResultStatus.NO_SELF)) }
            return
        }
        offMain {
            val mine = selfTraits(self)
            val out = mine?.let { runCatching { engine.match(it, b.traits, kind, null) }.getOrNull() }
            _state.update { s ->
                if (s.result?.bondId != b.id) return@update s
                s.copy(busy = false, result = toResult(base, out, mine, b.traits))
            }
        }
    }

    private fun toResult(base: ResultUi, out: MatchOutcome?, a: BondTraits?, b: BondTraits): ResultUi =
        if (out == null || a == null) {
            base.copy(status = ResultStatus.UNAVAILABLE)
        } else {
            base.copy(status = ResultStatus.SHOWN, outcome = out, manglikKey = ManglikNote.pair(a.manglik, b.manglik))
        }

    private fun delete(id: RecordId) {
        scope.launch {
            val ok = runCatching { store.delete(id) }.getOrElse { if (it is CancellationException) throw it else false }
            _state.update { s ->
                if (!ok) {
                    s.copy(deleteFailed = true)
                } else {
                    s.copy(page = BondsPage.LIST, result = null, deleteFailed = false)
                }
            }
        }
    }

    private fun openShare(includeLagna: Boolean) {
        val d = current ?: return
        val self = selfOf(d.profiles)
        if (self == null || self.origin != ProfileOrigin.OWN) {
            _state.update { it.copy(page = BondsPage.SHARE, share = ShareUi(ShareStatus.NO_SELF)) }
            return
        }
        val minor = isMinor(self.birth.date)
        val lagna = includeLagna && self.birth.timeKnown
        _state.update {
            it.copy(page = BondsPage.SHARE, share = ShareUi(ShareStatus.LOADING, lagna, minor))
        }
        offMain {
            val full = selfTraits(self)
            val name = if (minor) BondNames.initials(self.displayName) else BondNames.clip(self.displayName)
            val t = if (full == null || name == null) null else full.copy(name = name,
                lagna = full.lagna.takeIf { lagna })
            val code = t?.let { runCatching { engine.encode(it) }.getOrNull() }
            _state.update { s ->
                val status = if (code == null) ShareStatus.UNAVAILABLE else ShareStatus.READY
                s.copy(busy = false, share = ShareUi(status, lagna, minor, t, code))
            }
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun matchPick(e: BondsEvent.MatchPick) {
        val d = current ?: return
        _state.update { it.copy(match = it.match.copy(a = e.a, b = e.b, roles = e.roles, result = null)) }
        val ka = e.a ?: return
        val kb = e.b ?: return
        if (ka == kb) return
        val roles = e.roles
        val pick = _state.value.match
        val nameA = pick.candidates.firstOrNull { it.key == ka }?.name
        val nameB = pick.candidates.firstOrNull { it.key == kb }?.name ?: return
        val base = ResultUi(null, nameA, nameB, BondKind.LOVE, ResultStatus.LOADING)
        _state.update { it.copy(match = it.match.copy(result = base)) }
        offMain {
            val ta = traitsFor(d, ka)
            val tb = traitsFor(d, kb)
            val out = if (ta != null && tb != null) {
                runCatching { engine.match(ta, tb, BondKind.LOVE, roles) }.getOrNull()
            } else {
                null
            }
            _state.update { s ->
                if (s.match.a != ka || s.match.b != kb || s.match.roles != roles) return@update s
                s.copy(busy = false, match = s.match.copy(result = toResult(base, out, ta, tb ?: return@update s)))
            }
        }
    }

    private fun traitsFor(d: BondsData, key: String): BondTraits? {
        val id = key.substringAfter(':')
        return when {
            key == KEY_SELF -> selfOf(d.profiles)?.let(::selfTraits)
            key.startsWith(PREFIX_PROFILE) -> d.profiles.firstOrNull { it.id.hex == id }?.let { p ->
                BondNames.clip(p.displayName)?.let { n -> runCatching { engine.traitsOf(p, n) }.getOrNull() }
            }
            key.startsWith(PREFIX_BOND) -> d.bonds.firstOrNull { it.id.hex == id }?.traits
            else -> null
        }
    }

    private fun candidates(d: BondsData, self: Profile?): List<MatchCandidate> =
        buildList {
            if (self != null) add(MatchCandidate(KEY_SELF, null))
            d.profiles.filter { it.id != self?.id && it.passing == null }.forEach { p ->
                BondNames.clip(p.displayName)?.let { add(MatchCandidate(PREFIX_PROFILE + p.id.hex, it)) }
            }
            d.bonds.filter { it.profileRef == null }.forEach { add(MatchCandidate(PREFIX_BOND + it.id.hex,
                it.traits.name)) }
        }

    /** The user's own traits, computed once per self profile version (memory only, cleared on lock). */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun selfTraits(p: Profile): BondTraits? {
        val key = p.id to p.updatedAt
        selfCache?.takeIf { it.first == key }?.let { return it.second }
        val name = BondNames.clip(p.displayName) ?: return null
        val t = runCatching { engine.traitsOf(p, name) }.getOrNull()
        selfCache = key to t
        return t
    }

    private fun isMinor(birth: LocalDate): Boolean = birth.plus(DatePeriod(years = ADULT_AGE)) > today()

    /** Runs [block] on [compute], replacing any pending engine job; the screen shows [BondsState.busy] meanwhile. */
    private fun offMain(block: () -> Unit) {
        job?.cancel()
        _state.update { it.copy(busy = true, addError = null) }
        job = scope.launch {
            try {
                withContext(compute) { block() }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        /** A link plus a code stays well below this; longer input is rejected before decoding. */
        const val MAX_INPUT = 200
        const val BOND_CODE_VERSION = 1
        private const val ADULT_AGE = 18
        private const val KEY_SELF = "self"
        private const val PREFIX_PROFILE = "p:"
        private const val PREFIX_BOND = "b:"

        /** The user's own profile: relation SELF and entered by the user (never a manual profile). */
        fun selfOf(ps: List<Profile>): Profile? =
            ps.firstOrNull { it.relation == Relation.SELF && it.origin == ProfileOrigin.OWN && it.passing == null }
    }
}
