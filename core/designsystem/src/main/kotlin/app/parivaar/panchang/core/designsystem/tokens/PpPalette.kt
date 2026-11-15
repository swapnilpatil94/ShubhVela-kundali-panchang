@file:Suppress("MagicNumber")
package app.parivaar.panchang.core.designsystem.tokens

/** Brand palette (ARGB Long, no Android types so JVM tests run plain). Light "Prabhat", dark "Ratri". */
object PpPalette {
    object Light {
        const val PRIMARY = 0xFFE8772EL
        const val ON_PRIMARY = 0xFF2A1E17L
        /** Seed saffron is ~2.8:1 on ivory; text in the primary colour must use this (D10). */
        const val PRIMARY_TEXT = 0xFFAC4F13L
        const val SECONDARY = 0xFF8C2F39L
        const val ON_SECONDARY = 0xFFFFFFFFL
        const val TERTIARY = 0xFF1F7A74L
        const val ON_TERTIARY = 0xFFFFFFFFL
        const val BACKGROUND = 0xFFFFF8EEL
        const val SURFACE = BACKGROUND
        const val ON_SURFACE = 0xFF2A1E17L
        const val SURFACE_CONTAINER = 0xFFFFFFFFL
        const val SURFACE_VARIANT = 0xFFF6EBDDL
        const val ON_SURFACE_VARIANT = 0xFF7A6A5EL
        const val OUTLINE = 0xFF7A6A5EL
        const val OUTLINE_VARIANT = 0xFFEADBC6L
        const val GOLD = 0xFFC9A227L
        const val AUSPICIOUS_BG = 0xFFDDF0EEL
        /** Seed teal is 4.34:1 on the chip; lowered 1 % lightness for AA. */
        const val AUSPICIOUS_FG = 0xFF1E7670L
        const val INAUSPICIOUS_BG = 0xFFF5E1E3L
        const val INAUSPICIOUS_FG = 0xFF8C2F39L
        const val ERROR = 0xFFBA1A1AL
        const val ON_ERROR = 0xFFFFFFFFL
    }

    object Dark {
        const val PRIMARY = 0xFFFFB25BL
        const val ON_PRIMARY = 0xFF121331L
        const val PRIMARY_TEXT = PRIMARY
        const val SECONDARY = 0xFFF2A7B0L
        const val ON_SECONDARY = 0xFF121331L
        const val TERTIARY = 0xFF7FD6CCL
        const val ON_TERTIARY = 0xFF121331L
        const val BACKGROUND = 0xFF121331L
        const val SURFACE = BACKGROUND
        const val ON_SURFACE = 0xFFECE9FFL
        const val SURFACE_CONTAINER = 0xFF1C1E4AL
        const val SURFACE_VARIANT = 0xFF2C2F66L
        const val ON_SURFACE_VARIANT = 0xFFA9A6D9L
        const val OUTLINE = 0xFFA9A6D9L
        const val OUTLINE_VARIANT = 0xFF2C2F66L
        const val GOLD = 0xFFE7C85AL
        const val AUSPICIOUS_BG = 0xFF1E4A4AL
        const val AUSPICIOUS_FG = 0xFF7FD6CCL
        const val INAUSPICIOUS_BG = 0xFF4A2433L
        const val INAUSPICIOUS_FG = 0xFFF2A7B0L
        const val ERROR = 0xFFFFB4ABL
        const val ON_ERROR = 0xFF690005L
    }
}
