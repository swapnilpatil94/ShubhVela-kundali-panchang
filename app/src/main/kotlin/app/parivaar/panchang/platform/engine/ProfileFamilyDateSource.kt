package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.feature.aaj.reminders.FamilyDateSource
import app.parivaar.panchang.feature.kundli.CalInputsLite
import app.parivaar.panchang.feature.kundli.FamilyDateItem
import app.parivaar.panchang.feature.kundli.FamilyKind
import app.parivaar.panchang.feature.kundli.KundliEngine
import kotlinx.datetime.LocalDate

/**
 * Family dates for reminders (M10 rule 9): bare dates of every enabled `familyPrefs` kind of every profile, at the
 * home place. No names, relations, ids or kinds leave this class, so notifications cannot show them. Blocking.
 */
class ProfileFamilyDateSource(
    private val profiles: () -> List<Profile>,
    private val home: () -> CalInputsLite?,
    private val engine: KundliEngine,
) : FamilyDateSource {
    override fun dates(first: LocalDate, last: LocalDate): List<LocalDate> {
        val h = home()
        val ps = if (h == null) emptyList() else profiles()
        if (h == null || ps.isEmpty()) return emptyList()
        return enabledDates(engine.familyDates(ps, first, last, h).items, ps)
    }

    companion object {
        /** Dates of items whose kind is enabled for their profile; items of unknown profiles are dropped. */
        fun enabledDates(items: List<FamilyDateItem>, ps: List<Profile>): List<LocalDate> {
            val byId = ps.associateBy { it.id }
            return items.filter { i ->
                val f = byId[i.profileId]?.familyPrefs
                when (i.kind) {
                    FamilyKind.TITHI_BIRTHDAY -> f?.tithi == true
                    FamilyKind.NAKSHATRA_BIRTHDAY -> f?.nakshatra == true
                    FamilyKind.SHRADDHA -> f?.shraddha == true
                }
            }.map { it.date }
        }
    }
}
