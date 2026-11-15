package app.parivaar.panchang.core.model.settings

import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition

data class AppSettings(
    val defaultCalc: CalcSettings = CalcSettings(),
    val selfProfileId: RecordId? = null,
    val localeTag: String? = null,
    val privacyScreen: Boolean = false,
    val homePlaceId: RecordId? = null,
    val monthSystem: MonthSystem = MonthSystem.AMANTA,
    val presetSource: PresetSource = PresetSource.USER,
    val ekadashi: Tradition = Tradition.SMARTA,
    val chartStyle: ChartStyle = ChartStyle.NORTH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val badaText: Boolean = false,
    val reduceMotion: Boolean = false,
    /** M07R D4: solid surfaces instead of glass blur. Also forced by battery saver. */
    val reduceTransparency: Boolean = false,
    val reminders: Set<ReminderKind> = emptySet(),
    val onboardingDone: Boolean = false,
    /** M08 D2: the city appears on widgets only when this is on. Never in notifications. */
    val showCityOnWidgets: Boolean = false,
    /** M08 M3: offer a timings update when the time zone changes. */
    val travelMode: Boolean = false,
    /** M09 B6: calendar presentation (standard unless the regional views are enabled). */
    val calendarView: CalendarView = CalendarView.STANDARD,
) {
    override fun toString(): String = "AppSettings(redacted)"
}
