package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.engine.calendar.FamilyDateFinder
import app.parivaar.panchang.engine.calendar.FamilyDateKind
import app.parivaar.panchang.engine.calendar.FamilyDatePrefs
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.JanmaFacts
import app.parivaar.panchang.engine.calendar.JanmaResult
import app.parivaar.panchang.engine.calendar.NakshatraMonthBasis
import app.parivaar.panchang.engine.calendar.SolarRegion
import app.parivaar.panchang.engine.calendar.factsAtSunrise
import app.parivaar.panchang.core.geo.toEnginePlace
import kotlinx.datetime.LocalDate

/** One family def with the facts it was built from; [id] is `fam_<n>`, never a name (engine rule). */
internal class FamilyDefPlan(val profile: Profile, val kind: FamilyKind, val facts: JanmaFacts, val def: FestivalDef) {
    override fun toString(): String = "FamilyDefPlan(redacted)"
}

/**
 * [FamilyDates] over the engine's [FamilyDateFinder] (M10 rule 8). Not thread-safe (call under the engine lock).
 * - Birth facts: the birth instant when known; an unknown time uses `janmaOfDay` at the birth place and offset, and
 *   an ambiguous day yields no birthday dates (the user has not picked a tithi/nakshatra; never a guess).
 * - Shraddha: the date of passing at sunrise at the home place (rule 8; time of passing is not stored).
 * - Only kinds enabled in `familyPrefs` are generated. Nakshatra birthdays use the lunar month basis (VERIFY).
 */
class FinderFamilyDates(private val finder: FamilyDateFinder) : FamilyDates {
    override fun dates(
        ps: List<Profile>, first: LocalDate, last: LocalDate, home: CalInputsLite,
    ): List<FamilyDateItem> {
        val prefs = FamilyDatePrefs(home.monthSystem, NakshatraMonthBasis.LUNAR, SolarRegion.TAMIL)
        val plans = ps.flatMapIndexed { i, p -> runCatching { plansFor(i, p, prefs, home) }.getOrDefault(emptyList()) }
        if (plans.isEmpty()) return emptyList()
        val byId = plans.associateBy { it.def.id }
        val occ = finder.occurrences(plans.map { it.def }, first, last, home.place, home.calc, home.offsetFor)
        return occ.mapNotNull { o ->
            byId[o.id]?.let { plan ->
                FamilyDateItem(
                    profileId = plan.profile.id,
                    kind = plan.kind,
                    date = o.date,
                    tithi = o.tithi ?: plan.facts.tithi.takeIf { plan.kind != FamilyKind.NAKSHATRA_BIRTHDAY },
                    label = o.label,
                    nakshatra = plan.facts.nakshatra.takeIf { plan.kind == FamilyKind.NAKSHATRA_BIRTHDAY },
                    window = o.kalaWindow.takeIf { plan.kind == FamilyKind.SHRADDHA },
                    flags = o.flags,
                )
            }
        }
    }

    private fun birthFacts(p: Profile, home: CalInputsLite): JanmaFacts? {
        val at = p.place.birthInstant
        if (at != null) return finder.janma(at, home.calc)
        val r = finder.janmaOfDay(p.birth.date, p.place.offset.offsetSeconds, p.place.place.toEnginePlace(), home.calc)
        return (r as? JanmaResult.Exact)?.facts
    }

    private fun plansFor(i: Int, p: Profile, prefs: FamilyDatePrefs, home: CalInputsLite): List<FamilyDefPlan> {
        val out = ArrayList<FamilyDefPlan>()
        val f = p.familyPrefs
        if (f.tithi || f.nakshatra) {
            birthFacts(p, home)?.let { facts ->
                if (f.tithi) out += plan(i, p, FamilyKind.TITHI_BIRTHDAY, facts, prefs)
                if (f.nakshatra) out += plan(i, p, FamilyKind.NAKSHATRA_BIRTHDAY, facts, prefs)
            }
        }
        val passing = p.passing
        if (f.shraddha && passing != null) {
            val day = finder.janmaOfDay(passing.date, home.offsetFor(passing.date), home.place, home.calc)
            out += plan(i, p, FamilyKind.SHRADDHA, day.factsAtSunrise(), prefs)
        }
        return out
    }

    private fun plan(i: Int, p: Profile, kind: FamilyKind, facts: JanmaFacts, prefs: FamilyDatePrefs): FamilyDefPlan {
        val id = idOf(i, kind)
        return FamilyDefPlan(p, kind, facts, finder.def(engineKind(kind), facts, prefs, id))
    }

    companion object {
        /** `fam_<n>`: profile index times the kind count plus the kind; carries no input text. */
        fun idOf(index: Int, kind: FamilyKind): String = "fam_${index * FamilyKind.entries.size + kind.ordinal}"

        fun engineKind(k: FamilyKind): FamilyDateKind = FamilyDateKind.valueOf(k.name)
    }
}
