package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.OccFlag
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.AyanamsaUnavailableException
import app.parivaar.panchang.engine.jyotish.Avakhada
import app.parivaar.panchang.engine.jyotish.ChartCalculator
import app.parivaar.panchang.engine.jyotish.ChartFacts
import app.parivaar.panchang.engine.jyotish.DashaPeriod
import app.parivaar.panchang.engine.jyotish.DashaTree
import app.parivaar.panchang.engine.jyotish.Dashas
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.NakshatraTable
import app.parivaar.panchang.engine.jyotish.SadeSatiPhase
import app.parivaar.panchang.engine.jyotish.TransitFact
import app.parivaar.panchang.engine.jyotish.Varga
import app.parivaar.panchang.engine.jyotish.VargaChart
import app.parivaar.panchang.engine.jyotish.Vargas
import app.parivaar.panchang.engine.jyotish.YogaFinding
import app.parivaar.panchang.engine.jyotish.YogaRules
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The Kundli tab's engine port. Implementations are not thread-safe; the app wraps one under the engine lock. */
interface KundliEngine {
    fun bundle(r: ChartRequest): KundliBundle

    /** Sub-periods of the Vimshottari period at [path] (empty path: the maha periods). */
    fun dasha(r: ChartRequest, path: List<Graha>): List<DashaPeriod<Graha>>

    fun familyDates(ps: List<Profile>, first: LocalDate, last: LocalDate, home: CalInputsLite): List<FamilyDateItem>
}

/** One chart computation. [instant] is the birth instant, or local noon at the birth place when [noonChart]. */
data class ChartRequest(
    val profileId: RecordId,
    val updatedAt: Instant,
    val instant: Instant,
    val noonChart: Boolean,
    val place: Place,
    val calc: CalcSettings,
    val now: Instant,
) {
    override fun toString(): String = "ChartRequest(redacted)"
}

/** Notes shown above the chart (codes; text in resources). */
enum class KundliNote {
    NOON_CHART, AYANAMSA_FALLBACK, HOUSE_FALLBACK, HISTORICAL_OFFSET, LMT_OFFSET, MANUAL_OFFSET, DST_CHOSEN,
}

/** One Saturn ingress mapped through Sade Sati (rule 7). */
data class SadeSatiStep(val at: Instant, val phase: SadeSatiPhase) {
    override fun toString(): String = "SadeSatiStep(redacted)"
}

/** Everything the Kundli tab shows for one profile. Personal data: memory only, never logged or stored. */
data class KundliBundle(
    val facts: ChartFacts,
    val d9: VargaChart,
    val avakhada: Avakhada,
    val yogas: List<YogaFinding>,
    val currentDasha: List<DashaPeriod<Graha>>,
    val maha: List<DashaPeriod<Graha>>,
    val sadeSati: SadeSatiPhase,
    val transits: List<TransitFact>,
    val notes: Set<KundliNote>,
    val sadeSatiTimeline: List<SadeSatiStep> = emptyList(),
    val calcUsed: CalcSettings = CalcSettings(),
) {
    override fun toString(): String = "KundliBundle(redacted)"
}

/** The yearly family date kinds (mirrors the engine's `FamilyDateKind`, M10 section "Files"). */
enum class FamilyKind { TITHI_BIRTHDAY, NAKSHATRA_BIRTHDAY, SHRADDHA }

/** The home place the yearly family dates are evaluated at (D4). */
class CalInputsLite(
    val place: Place,
    val zone: TimeZone,
    val offsetFor: (LocalDate) -> Int,
    val calc: CalcSettings,
    val monthSystem: MonthSystem,
    val homePlaceId: RecordId?,
) {
    override fun toString(): String = "CalInputsLite(redacted)"
}

/** One upcoming family date. Display text is built from these codes and the profile name on screen only. */
data class FamilyDateItem(
    val profileId: RecordId,
    val kind: FamilyKind,
    val date: LocalDate,
    val tithi: Int?,
    val label: LunarLabel?,
    val nakshatra: Int?,
    val window: TimeRange?,
    val flags: Set<OccFlag>,
) {
    override fun toString(): String = "FamilyDateItem(redacted)"
}

/** Computes family dates; backed by the engine's `FamilyDateFinder` in the app (not thread-safe, like the engine). */
fun interface FamilyDates {
    fun dates(ps: List<Profile>, first: LocalDate, last: LocalDate, home: CalInputsLite): List<FamilyDateItem>

    companion object {
        /** Used until the engine's family-date finder is wired: no dates (never a guessed date). */
        val NONE = FamilyDates { _, _, _, _ -> emptyList() }
    }
}

/** The engine calls behind [KundliEngine]. No lock here: the caller serialises (see the app's EngineKundliEngine). */
class DirectKundliEngine(
    private val chart: ChartCalculator,
    private val vargas: Vargas,
    private val yogas: YogaRules,
    private val nak: NakshatraTable,
    private val gochar: Gochar,
    private val fam: FamilyDates = FamilyDates.NONE,
) : KundliEngine {
    /** D5: a disabled ayanamsa falls back to Lahiri with a visible note, never a crash. */
    private fun natal(r: ChartRequest): Pair<ChartFacts, CalcSettings> =
        try {
            chart.compute(r.instant, r.place, r.calc) to r.calc
        } catch (_: AyanamsaUnavailableException) {
            val c = r.calc.copy(ayanamsa = AyanamsaId.LAHIRI)
            chart.compute(r.instant, r.place, c) to c
        }

    private fun tree(r: ChartRequest, facts: ChartFacts, calc: CalcSettings): DashaTree<Graha> =
        Dashas.vimshottari(facts.of(Graha.MOON).lon, r.instant, calc.dashaYear)

    override fun bundle(r: ChartRequest): KundliBundle {
        val (facts, calc) = natal(r)
        val notes = buildSet {
            if (calc != r.calc) add(KundliNote.AYANAMSA_FALLBACK)
            if (facts.houseFallback) add(KundliNote.HOUSE_FALLBACK)
            if (r.noonChart) add(KundliNote.NOON_CHART)
        }
        val tree = tree(r, facts, calc)
        val inRange = r.now >= tree.maha.first().start && r.now < tree.maha.last().end
        val current = if (inRange) tree.at(r.now, DASHA_DEPTH) else emptyList()
        val transit = chart.compute(r.now, r.place, calc)
        val moon = facts.of(Graha.MOON).rashi
        val timeline =
            gochar.ingresses(Graha.SATURN, r.now - SADE_SATI_BACK, r.now + SADE_SATI_AHEAD, calc)
                .map { SadeSatiStep(it.at, gochar.sadeSati(moon, it.into)) }
        return KundliBundle(
            facts = facts,
            d9 = vargas.chart(Varga.D9, facts, calc),
            avakhada = nak.avakhada(facts),
            yogas = yogas.evaluate(facts),
            currentDasha = current,
            maha = tree.maha,
            sadeSati = gochar.sadeSati(moon, transit.of(Graha.SATURN).rashi),
            transits = gochar.snapshot(facts, transit),
            notes = notes,
            sadeSatiTimeline = timeline,
            calcUsed = calc,
        )
    }

    override fun dasha(r: ChartRequest, path: List<Graha>): List<DashaPeriod<Graha>> {
        val (facts, calc) = natal(r)
        val tree = tree(r, facts, calc)
        var level: List<DashaPeriod<Graha>> = tree.maha
        for (lord in path) {
            val p = level.firstOrNull { it.lords.last() == lord && it.lords.size < DASHA_DEPTH }
            level = if (p == null) emptyList() else tree.children(p)
        }
        return level
    }

    override fun familyDates(
        ps: List<Profile>,
        first: LocalDate,
        last: LocalDate,
        home: CalInputsLite,
    ): List<FamilyDateItem> = fam.dates(ps, first, last, home)

    companion object {
        /** D2: maha > antar > pratyantar > sookshma. */
        const val DASHA_DEPTH = 4
        private val SADE_SATI_BACK = (3 * 365 + 1).days
        private val SADE_SATI_AHEAD = (10 * 365 + 2).days
    }
}
