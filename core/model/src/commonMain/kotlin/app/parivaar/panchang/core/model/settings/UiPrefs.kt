package app.parivaar.panchang.core.model.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ChartStyle { NORTH, SOUTH, EAST }

/** M09 B6: which calendar presentation. MARATHI and GUJARATI stay hidden until owner sign-off. */
enum class CalendarView { STANDARD, MARATHI, GUJARATI }

enum class ReminderKind { VRAT_EVE, FAMILY_DATES, GRAHAN, SUNRISE_BRAHMA }

/** Where the month system came from; NOT_SURE_DEFAULT shows "Default, change anytime" in Conventions. */
enum class PresetSource { USER, NOT_SURE_DEFAULT }
