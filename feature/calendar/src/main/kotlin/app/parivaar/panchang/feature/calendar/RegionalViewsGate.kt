package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.core.model.settings.CalendarView

/** The Marathi and Gujarati views stay hidden in release until the regional pandit review (TODO(owner)). */
object RegionalViewsGate {
    /** Flip only after the owner's written sign-off. */
    const val OWNER_SIGNED_OFF: Boolean = false

    fun enabled(isDebug: Boolean): Boolean = isDebug || OWNER_SIGNED_OFF

    /** A stored regional view reads as STANDARD while the gate is closed. */
    fun effective(view: CalendarView, isDebug: Boolean): CalendarView =
        if (view == CalendarView.STANDARD || enabled(isDebug)) view else CalendarView.STANDARD
}
