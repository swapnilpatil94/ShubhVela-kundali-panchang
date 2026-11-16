package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.geo.toEnginePlace
import app.parivaar.panchang.core.model.profile.PassingData
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.calendar.FamilyDateFinder
import app.parivaar.panchang.engine.calendar.FamilyDateKind
import app.parivaar.panchang.engine.calendar.FamilyDatePrefs
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.JanmaFacts
import app.parivaar.panchang.engine.calendar.JanmaResult
import app.parivaar.panchang.engine.calendar.NakshatraMonthBasis
import app.parivaar.panchang.engine.calendar.SolarRegion
import app.parivaar.panchang.engine.calendar.factsAtSunrise
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.AyanamsaUnavailableException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant

/** One family def with the facts it was built from; [def] id is `fam_<n>`, never a name (engine rule). */
internal class FamilyDefPlan(val profile: Profile, val kind: FamilyKind, val facts: JanmaFacts, val def: FestivalDef) {
    override fun toString(): String = "FamilyDefPlan(redacted)"
}

/**
 * [FamilyDates] over the engine's [FamilyDateFinder] (M10 rule 8). Not thread-safe (call under the engine lock).
 * - Birth facts: the birth instant when known; an unknown time uses `janmaOfDay` at the birth place and offset. An
 *   ambiguous day yields no birthday dates and the note [FamilyNote.AMBIGUOUS_BIRTH_DAY] (never a guess).
 * - Shraddha: the tithi at the passing instant when its time is known (home zone), else at sunrise of the passing
 *   date at the home place.
 * - A disabled ayanamsa falls back to Lahiri with [FamilyNote.AYANAMSA_FALLBACK] (D5); a profile that cannot be
 *   computed is skipped with [FamilyNote.SOME_UNAVAILABLE], so the screen can say so calmly.
 * - Only kinds enabled in `familyPrefs`. Nakshatra birthdays use the lunar month basis (VERIFY).
 */
class FinderFamilyDates(private val finder: FamilyDateFinder) : FamilyDates {
    override fun dates(ps: List<Profile>, first: LocalDate, last: LocalDate, home: CalInputsLite): FamilyDatesResult =
        try {
            compute(ps, first, last, home, home.calc)
        } catch (_: AyanamsaUnavailableException) {
            val r = compute(ps, first, last, home, home.calc.copy(ayanamsa = AyanamsaId.LAHIRI))
            r.copy(notes = r.notes + FamilyNote.AYANAMSA_FALLBACK)
        }

    private fun compute(
        ps: List<Profile>,
        first: LocalDate,
        last: LocalDate,
        home: CalInputsLite,
        calc: CalcSettings,
    ): FamilyDatesResult {
        val run = Run(home, calc)
        val plans = ps.flatMapIndexed { i, p ->
            val r = runCatching { run.plansFor(i, p) }
            when (r.exceptionOrNull()) {
                null -> Unit
                is AyanamsaUnavailableException -> throw checkNotNull(r.exceptionOrNull())
                else -> run.notes += FamilyNote.SOME_UNAVAILABLE
            }
            r.getOrDefault(emptyList())
        }
        if (plans.isEmpty()) return FamilyDatesResult(emptyList(), run.notes)
        val byId = plans.associateBy { it.def.id }
        val occ = finder.occurrences(plans.map { it.def }, first, last, home.place, calc, home.offsetFor)
        val items = occ.mapNotNull { o -> byId[o.id]?.let { plan -> item(plan, o) } }
        return FamilyDatesResult(items, run.notes)
    }

    private fun item(plan: FamilyDefPlan, o: FestivalOccurrence) = FamilyDateItem(
        profileId = plan.profile.id,
        kind = plan.kind,
        date = o.date,
        tithi = o.tithi ?: plan.facts.tithi.takeIf { plan.kind != FamilyKind.NAKSHATRA_BIRTHDAY },
        label = o.label,
        nakshatra = plan.facts.nakshatra.takeIf { plan.kind == FamilyKind.NAKSHATRA_BIRTHDAY },
        window = o.kalaWindow.takeIf { plan.kind == FamilyKind.SHRADDHA },
        flags = o.flags,
    )

    /** One evaluation at [home] with [calc]; collects [notes]. */
    private inner class Run(val home: CalInputsLite, val calc: CalcSettings) {
        val notes = mutableSetOf<FamilyNote>()
        private val prefs = FamilyDatePrefs(home.monthSystem, NakshatraMonthBasis.LUNAR, SolarRegion.TAMIL)

        private fun birthFacts(p: Profile): JanmaFacts? {
            val at = p.place.birthInstant
            if (at != null) return finder.janma(at, calc)
            val r = finder.janmaOfDay(p.birth.date, p.place.offset.offsetSeconds, p.place.place.toEnginePlace(), calc)
            if (r is JanmaResult.Ambiguous) notes += FamilyNote.AMBIGUOUS_BIRTH_DAY
            return (r as? JanmaResult.Exact)?.facts
        }

        private fun passingFacts(d: PassingData): JanmaFacts {
            val offset = home.offsetFor(d.date)
            val time = d.time ?: return finder.janmaOfDay(d.date, offset, home.place, calc).factsAtSunrise()
            return finder.janma(LocalDateTime(d.date, time).toInstant(UtcOffset(seconds = offset)), calc)
        }

        fun plansFor(i: Int, p: Profile): List<FamilyDefPlan> {
            val out = ArrayList<FamilyDefPlan>()
            val f = p.familyPrefs
            if (f.tithi || f.nakshatra) {
                birthFacts(p)?.let { facts ->
                    if (f.tithi) out += plan(i, p, FamilyKind.TITHI_BIRTHDAY, facts)
                    if (f.nakshatra) out += plan(i, p, FamilyKind.NAKSHATRA_BIRTHDAY, facts)
                }
            }
            val passing = p.passing
            if (f.shraddha && passing != null) out += plan(i, p, FamilyKind.SHRADDHA, passingFacts(passing))
            return out
        }

        private fun plan(i: Int, p: Profile, kind: FamilyKind, facts: JanmaFacts): FamilyDefPlan =
            FamilyDefPlan(p, kind, facts, finder.def(engineKind(kind), facts, prefs, idOf(i, kind)))
    }

    companion object {
        /** `fam_<n>`: profile index times the kind count plus the kind; carries no input text. */
        fun idOf(index: Int, kind: FamilyKind): String = "fam_${index * FamilyKind.entries.size + kind.ordinal}"

        fun engineKind(k: FamilyKind): FamilyDateKind = FamilyDateKind.valueOf(k.name)
    }
}
