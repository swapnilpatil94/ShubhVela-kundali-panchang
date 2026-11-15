package app.parivaar.panchang.feature.aaj.reminders

import kotlinx.datetime.LocalDate

/**
 * Port for family dates (birthdays, anniversaries, shraddha). It returns bare dates only: no names, no
 * relations, so nothing downstream can leak them. M10 supplies the real source; until then [None].
 */
fun interface FamilyDateSource {
    /** Dates in [first]..[last] inclusive, in any order, duplicates allowed. */
    fun dates(first: LocalDate, last: LocalDate): List<LocalDate>

    companion object {
        val None: FamilyDateSource = FamilyDateSource { _, _ -> emptyList() }
    }
}
