package app.parivaar.panchang.feature.bonds

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Profile

/** F3 bond types (M12). [key] is the stored `Bond.kindKey`; names match the engine's `BondType`. */
enum class BondKind(val key: String) {
    LOVE("love"), CRUSH("crush"), BESTIE("bestie"), SIBLING("sibling"), PARENT("parent"), WORK("work"),
    ROOMMATE("roommate"),
    ;

    companion object {
        /** Unknown or legacy keys (for example `friend`) read as [BESTIE]. */
        fun of(key: String): BondKind = entries.firstOrNull { it.key == key } ?: BESTIE
    }
}

/** One koota line of a result. [key] is the engine's `Koota` name; [noteIds] are content ids (text in resources). */
data class KootaLine(val key: String, val q: Int, val maxQ: Int, val noteIds: List<String>, val excluded: Boolean)

/**
 * A computed compatibility result (codes and numbers only). [percent] and [labelKey] come from the engine scorer and
 * `bond_weights.json`; [axes] keeps the axis order VIBE, TALK, TRUST, FIGHTS, GROWTH (0..10 each, empty for F1
 * marriage matching).
 */
data class MatchOutcome(
    val kootas: List<KootaLine>,
    val totalQ: Int,
    val maxQ: Int,
    val percent: Int?,
    val labelKey: String?,
    val axes: List<Pair<String, Int>>,
    val tableStatus: String,
) {
    override fun toString(): String = "MatchOutcome(redacted)"
}

/** F1 marriage matching roles; null for the symmetric F3 bond types (D6). */
enum class MatchRoles { A_GROOM, B_GROOM }

/**
 * The Bonds engine port. Blocking and not thread-safe: the app implementation runs every call under the one engine
 * lock; the view model calls it on its compute dispatcher, never on the main thread. Nothing is logged.
 */
interface BondEngine {
    /** Null when the koota tables are unavailable (the UI shows a calm error, never a guess). */
    fun match(a: BondTraits, b: BondTraits, kind: BondKind, roles: MatchRoles?): MatchOutcome?

    /** The Bond Code of [t], or null when the codec is unavailable. */
    fun encode(t: BondTraits): String?

    /** Traits from a pasted code or `https://<share_host>/b#<code>` link; null on any rejection. */
    fun decode(input: String): BondTraits?

    /** Traits computed locally from [p] (name = [name]); null when the chart cannot be computed. */
    fun traitsOf(p: Profile, name: String): BondTraits?
}

/** The encrypted bond store (the app adapts `BondRepository`). Returns false on any failure (limit, locked, I/O). */
interface BondStore {
    suspend fun save(b: Bond): Boolean

    suspend fun delete(id: RecordId): Boolean
}

/** Inputs while the vault is open. The app emits null while it is locked, so the view model clears everything. */
data class BondsData(
    val bonds: List<Bond>,
    val profiles: List<Profile>,
) {
    override fun toString(): String = "BondsData(redacted)"
}

/** Manglik pair note (rule 12): both at least partial, or both none, is balanced; otherwise a calm consult note. */
object ManglikNote {
    const val BALANCED = "manglik.pair.balanced"
    const val CONSULT = "manglik.pair.consult_note"

    fun pair(a: Int, b: Int): String = if ((a >= 1) == (b >= 1)) BALANCED else CONSULT
}

/** Display names that may leave the device in a Bond Code: 1..12 characters, initials for under-18s (rule 14). */
object BondNames {
    const val MAX = 12
    private const val MAX_INITIALS = 3

    /** [name] trimmed to [MAX] UTF-16 units without splitting a surrogate pair; blank becomes null. */
    fun clip(name: String): String? {
        val t = name.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        var end = minOf(t.length, MAX)
        if (end < t.length && Character.isHighSurrogate(t[end - 1])) end--
        return t.substring(0, end).trim().ifEmpty { null }
    }

    /** "Riya Sharma" -> "R.S."; at most three initials. */
    fun initials(name: String): String? {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(MAX_INITIALS)
        if (parts.isEmpty()) return null
        return parts.joinToString("") { p ->
            val cp = p.codePointAt(0)
            String(Character.toChars(Character.toUpperCase(cp))) + "."
        }.let(::clip)
    }
}
