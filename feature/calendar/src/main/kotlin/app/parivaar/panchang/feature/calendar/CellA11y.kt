package app.parivaar.panchang.feature.calendar

import kotlinx.datetime.LocalDate

/** Spoken pieces of a cell, backed by resources in the UI (and by fakes in tests). */
interface CalendarNames {
    fun fullDate(date: LocalDate): String
    fun tithiName(tithi: Int): String
    fun festivalName(id: String): String
    fun markerName(marker: Marker): String
    fun todayWord(): String
    fun kshayaWord(): String
    fun vriddhiWord(): String
}

/** One TalkBack sentence per day cell. */
object CellA11y {
    fun describe(cell: DayCell, n: CalendarNames): String {
        val tithi = n.tithiName(cell.tithi)
        val tithiText = when (cell.tithiMark) {
            TithiMark.NONE -> tithi
            TithiMark.VRIDDHI -> "$tithi, ${n.vriddhiWord()}"
            TithiMark.KSHAYA_NEXT -> "$tithi, ${n.kshayaWord()}"
        }
        val parts = buildList {
            add(n.fullDate(cell.date))
            if (cell.isToday) add(n.todayWord())
            add(tithiText)
            cell.festivalIds.firstOrNull()?.let { add(n.festivalName(it)) }
            cell.markers.sortedBy { it.ordinal }.forEach { add(n.markerName(it)) }
        }
        return parts.joinToString(", ")
    }
}
