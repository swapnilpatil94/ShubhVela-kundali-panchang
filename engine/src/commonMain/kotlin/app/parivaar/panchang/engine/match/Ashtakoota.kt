package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.jyotish.NakshatraTable
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.Zodiac

/** Vashya classes (docs/specs/M12.md rule 2); `ordinal` is the Bond Code `vs` value. */
enum class VashyaClass { CHATUSHPADA, MANAVA, JALACHARA, VANACHARA, KEETA }

/**
 * A Moon point: nakshatra 0..26 (0 = Ashwini), pada 1..4 and the Vashya class of the Moon's degree. The class
 * must be one of [vashyaChoices] for the pada (two only for Purva Ashadha pada 1 and Shravana pada 2, which
 * straddle 15 degrees of Dhanu and Makara). Ids only; no birth data.
 */
data class MoonPoint(val nakshatra: Int, val pada: Int, val vashya: VashyaClass) {
    init {
        require(nakshatra in 0..MAX_NAKSHATRA) { "nakshatra out of range" }
        require(pada in 1..PADAS) { "pada out of range" }
        require(vashya in vashyaChoices(nakshatra, pada)) { "vashya does not match the pada" }
    }

    /** Rashi 0..11 (0 = Mesha) of the pada. */
    val rashi: Int get() = (nakshatra * PADAS + pada - 1) / PADAS_PER_RASHI

    override fun toString(): String = "MoonPoint(redacted)"
}

/** The eight kootas with their maximum in quarter-points (Q = 4 x gunas). */
@Suppress("MagicNumber") // the spec's maxima, in quarter-points
enum class Koota(val maxQ: Int) {
    VARNA(4), VASHYA(8), TARA(12), YONI(16), GRAHA_MAITRI(20), GANA(24), BHAKOOT(28), NADI(32),
}

/** One koota's score [q] (quarter-points) and note ids (content keys, never display text). */
data class KootaScore(val koota: Koota, val q: Int, val noteIds: List<String>)

/** Eight [scores] in [Koota] order; [totalQ] of [maxQ] (144 = 36 gunas); [tableStatus] `VERIFY` or `APPROVED`. */
data class AshtakootaResult(val scores: List<KootaScore>, val totalQ: Int, val maxQ: Int, val tableStatus: String) {
    /** The score of [k]. */
    fun of(k: Koota): KootaScore = scores[k.ordinal]
}

private const val MAX_NAKSHATRA = 26
private const val NAKSHATRAS = 27
private const val PADAS = 4
private const val PADAS_PER_RASHI = 9
private const val SIGNS = 12
private const val HALF_SIGN = 15.0
private const val DHANU = 8
private const val MAKARA = 9

/** Fixed class per sign; Dhanu and Makara are split at 15 degrees (see [vashyaOf]). */
private val SIGN_VASHYA = listOf(
    VashyaClass.CHATUSHPADA, VashyaClass.CHATUSHPADA, VashyaClass.MANAVA, VashyaClass.JALACHARA,
    VashyaClass.VANACHARA, VashyaClass.MANAVA, VashyaClass.MANAVA, VashyaClass.KEETA,
    VashyaClass.MANAVA, VashyaClass.CHATUSHPADA, VashyaClass.MANAVA, VashyaClass.JALACHARA,
)

private fun vashyaOfSign(rashi: Int, secondHalf: Boolean): VashyaClass = when {
    rashi == DHANU && secondHalf -> VashyaClass.CHATUSHPADA
    rashi == MAKARA && secondHalf -> VashyaClass.JALACHARA
    else -> SIGN_VASHYA[rashi]
}

/**
 * Vashya class of a sidereal Moon longitude (docs/specs/M12.md rule 2): Dhanu below 15 degrees Manava, else
 * Chatushpada; Makara below 15 degrees Chatushpada, else Jalachara; exactly 15.0 is the second half.
 */
fun vashyaOf(siderealMoonLonDeg: Double): VashyaClass {
    val r = Zodiac.rashiOf(siderealMoonLonDeg).ordinal
    return vashyaOfSign(r, Zodiac.degInRashi(siderealMoonLonDeg) >= HALF_SIGN)
}

/**
 * The Vashya classes a Moon in pada [pada] of nakshatra [nakshatra] can have: one class, or two for the padas
 * that straddle 15 degrees of Dhanu or Makara (first-half class first).
 */
fun vashyaChoices(nakshatra: Int, pada: Int): List<VashyaClass> {
    require(nakshatra in 0..MAX_NAKSHATRA && pada in 1..PADAS) { "moon point out of range" }
    val idx = nakshatra * PADAS + pada - 1 // pada index 0..107; a sign holds 9, 15 degrees = 4.5 padas
    val r = idx / PADAS_PER_RASHI
    val inSign = (idx % PADAS_PER_RASHI) * 2 // start of the pada in half-padas from the sign start
    val first = vashyaOfSign(r, inSign >= PADAS_PER_RASHI)
    val last = vashyaOfSign(r, inSign + 1 >= PADAS_PER_RASHI) // the pada's last half-pada
    return if (first == last) listOf(first) else listOf(first, last)
}

/**
 * Ashtakoota matching (docs/specs/M12.md rules 1-10), exact in quarter-points. Gana, yoni and nadi come from
 * [n] (`rules/nakshatra.json`), the points from [t] (`rules/koota.json`). Exceptions are notes only and never
 * change a score. Immutable and thread-safe.
 */
class AshtakootaCalculator(private val t: KootaTables, private val n: NakshatraTable) {
    private val gana = IntArray(NAKSHATRAS) { lookup(t.ganaIndex, n.gana(it)) }
    private val yoni = IntArray(NAKSHATRAS) { lookup(t.yoniIndex, n.yoni(it)) }
    private val nadi = Array(NAKSHATRAS) { n.nadi(it) }
    private val varna = IntArray(SIGNS) { lookup(t.varnaRank, n.varna(it)) }
    private val lords = List(SIGNS) { Zodiac.lordOf(Rashi.entries[it]) }

    private fun lookup(m: Map<String, Int>, key: String): Int =
        requireNotNull(m[key]) { "koota table does not cover a nakshatra.json code" }

    /** Scores [groom] against [bride]; the orientation matters for Varna, Vashya and Gana. */
    fun match(groom: MoonPoint, bride: MoonPoint): AshtakootaResult {
        val rg = groom.rashi
        val rb = bride.rashi
        val scores = listOf(
            KootaScore(Koota.VARNA, if (varna[rg] >= varna[rb]) Koota.VARNA.maxQ else 0, emptyList()),
            KootaScore(Koota.VASHYA, t.vashyaQ(groom.vashya, bride.vashya), emptyList()),
            KootaScore(Koota.TARA, t.taraQ[tara(groom.nakshatra, bride.nakshatra)], emptyList()),
            KootaScore(Koota.YONI, t.yoniQ(yoni[groom.nakshatra], yoni[bride.nakshatra]), emptyList()),
            KootaScore(Koota.GRAHA_MAITRI, t.grahaMaitriQ(lords[rg], lords[rb]), emptyList()),
            KootaScore(Koota.GANA, t.ganaQ(gana[groom.nakshatra], gana[bride.nakshatra]), emptyList()),
            bhakoot(rg, rb),
            nadi(groom, bride),
        )
        return result(scores)
    }

    /**
     * Role-free score for friend and other bond types (docs/specs/M12.md D6): per koota
     * q = (q(a,b) + q(b,a)) / 2, exact because [KootaTables.parse] rejects tables with an odd sum. Notes are the
     * union of both orientations.
     */
    fun matchSymmetric(a: MoonPoint, b: MoonPoint): AshtakootaResult {
        val x = match(a, b).scores
        val y = match(b, a).scores
        val scores = x.zip(y) { s1, s2 ->
            KootaScore(s1.koota, (s1.q + s2.q) / 2, s1.noteIds + s2.noteIds.filter { it !in s1.noteIds })
        }
        return result(scores)
    }

    /** Good taras of both directions, 0..2. */
    private fun tara(g: Int, b: Int): Int = taraGood(g, b) + taraGood(b, g)

    private fun result(scores: List<KootaScore>) =
        AshtakootaResult(scores, scores.sumOf { it.q }, Koota.entries.sumOf { it.maxQ }, t.tableStatus)

    /** 1 when the tara counted from [from] to [to] is good: c = ((to - from) mod 27) + 1, t = ((c - 1) mod 9) + 1. */
    private fun taraGood(to: Int, from: Int): Int {
        val c = (to - from).mod(NAKSHATRAS) + 1
        return if (((c - 1) % PADAS_PER_RASHI + 1) in t.taraBad) 0 else 1
    }

    private fun bhakoot(rg: Int, rb: Int): KootaScore {
        val d1 = (rg - rb).mod(SIGNS) + 1
        val d2 = (rb - rg).mod(SIGNS) + 1
        if (setOf(d1, d2) !in t.bhakootDosha) return KootaScore(Koota.BHAKOOT, Koota.BHAKOOT.maxQ, emptyList())
        val lg = lords[rg]
        val lb = lords[rb]
        val friendly = lg == lb ||
            (t.relation(lg, lb) == Relation.FRIEND && t.relation(lb, lg) == Relation.FRIEND)
        val notes = if (friendly) listOf(NOTE_BHAKOOT_DOSHA, NOTE_BHAKOOT_EXCEPTION) else listOf(NOTE_BHAKOOT_DOSHA)
        return KootaScore(Koota.BHAKOOT, 0, notes)
    }

    private fun nadi(g: MoonPoint, b: MoonPoint): KootaScore {
        if (nadi[g.nakshatra] != nadi[b.nakshatra]) return KootaScore(Koota.NADI, Koota.NADI.maxQ, emptyList())
        val exception = (g.rashi == b.rashi && g.nakshatra != b.nakshatra) ||
            (g.nakshatra == b.nakshatra && g.pada != b.pada)
        val notes = if (exception) listOf(NOTE_NADI_SAME, NOTE_NADI_EXCEPTION) else listOf(NOTE_NADI_SAME)
        return KootaScore(Koota.NADI, 0, notes)
    }

    companion object {
        const val NOTE_BHAKOOT_DOSHA = "koota.bhakoot.dosha"
        const val NOTE_BHAKOOT_EXCEPTION = "koota.bhakoot.exception_possible"
        const val NOTE_NADI_SAME = "koota.nadi.same"
        const val NOTE_NADI_EXCEPTION = "koota.nadi.exception_possible"
    }
}
