package app.parivaar.panchang.feature.calendar.ui

import app.parivaar.panchang.feature.calendar.DayCell
import app.parivaar.panchang.feature.calendar.MonthUi

/** Which festival ids are vrat or observance days only. These never switch the hero to Utsav (decision D5). */
object UtsavDays {
    private val VRAT_ONLY = setOf(
        "ekadashi_smarta", "ekadashi_vaishnava", "purnima", "amavasya", "sankranti", "sankashti_chaturthi",
        "pradosh_vrat", "masik_shivratri", "somvati_amavasya", "angaraki_sankashti",
    )

    fun isUtsav(festivalIds: List<String>): Boolean = festivalIds.any { it !in VRAT_ONLY }

    /** Today's cell when it belongs to the shown month. */
    fun todayCell(c: MonthUi.Content): DayCell? = c.weeks.flatten().firstOrNull { it.isToday && it.inMonth }
}
