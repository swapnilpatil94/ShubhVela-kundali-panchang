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
    val reminders: Set<ReminderKind> = emptySet(),
    val onboardingDone: Boolean = false,
) {
    override fun toString(): String = "AppSettings(redacted)"
}
