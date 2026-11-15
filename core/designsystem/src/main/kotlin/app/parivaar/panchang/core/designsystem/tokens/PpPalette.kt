@file:Suppress("MagicNumber", "MaxLineLength")
package app.parivaar.panchang.core.designsystem.tokens

/**
 * Brand palette (ARGB Long, no Android types so JVM tests run plain).
 * Ratna Glass (DESIGN_V2 section 3) is the live palette: [night], [chandan], [utsav], [paper].
 * [Light] and [Dark] are the retired M07 Prabhat/Ratri constants, kept only for the share-card renderer until its restyle.
 */
object PpPalette {
    // ---- Ratna Glass raw tokens (DESIGN_V2 section 3, verbatim) ----
    const val INK0 = 0xFF0A0D18L
    const val INK1 = 0xFF0E1222L
    const val INK2 = 0xFF151A2EL
    const val INK3 = 0xFF1C2238L
    /** rgba(237,230,214,.10) */
    const val LINE = 0x1AEDE6D6L
    const val CHANDAN = 0xFFEDE6D6L
    const val CHANDAN2 = 0xFFB8AF9CL
    const val CHANDAN3 = 0xFF8C8572L
    const val WHITE = 0xFFF6F1E7L
    const val HALDI = 0xFFE2B33CL
    const val HALDI_DEEP = 0xFFB8862AL
    const val KUMKUM = 0xFFE8636AL
    const val OKAY = 0xFF9AA0B8L

    /** Gold button gradient stops, top to bottom (plus a 1 px top highlight drawn by the button). */
    const val GOLD_GRAD_TOP = 0xFFF2CF6EL
    const val GOLD_GRAD_MID = 0xFFD9A52BL
    const val GOLD_GRAD_BOTTOM = 0xFFB8862AL

    /** Glass tint gradient and border (DESIGN_V2 section 3; M07R section 6). */
    const val GLASS_TINT_TOP = 0x941C2238L
    const val GLASS_TINT_BOTTOM = 0xA80E1222L
    const val GLASS_BORDER = 0x29EDE6D6L
    const val GLASS_TINT_TOP_CHANDAN = 0xD9FFFBF2L
    const val GLASS_BORDER_CHANDAN = 0xFFE3CFA2L

    // Aaj orb (orbSVG / .orb in design/shubhvela-screens.html)
    const val FLAME_OUTER = 0xFFFFE0A0L
    const val FLAME_INNER = 0xFFFF8A2AL
    const val END_DOT_GOOD = 0xFFFFE3A3L
    const val END_DOT_AVOID = 0xFFFFB3B8L
    const val ORB_CHANDAN_TOP = 0xFFFFFDF6L
    const val ORB_CHANDAN_BOTTOM = 0xFFF3E6CCL
    const val ORB_HALO_CHANDAN = 0xE6FBF4E6L
    const val DIYA_BRASS_DARK = 0xFF7A4A12L
    const val SHEEN_WHITE = 0xFFFFFFFFL
    const val BTN_INSET_TOP = 0xCCFFF4D2L
    const val BTN_INSET_BOTTOM = 0x8078500AL
    /** rgba(255,240,200,.10): inset highlight of the hero lacquer. */
    const val HERO_HIGHLIGHT = 0x1AFFF0C8L
    const val BTN_ON_GOLD = 0xFF2A1C05L
    /** Aaj orb ring on Chandan (ivory orb). */
    const val ORB_RING_CHANDAN = 0xFFC99A3AL

    // Vaar Ratna gems (weekday lord).
    const val MANIKYA = 0xFFD2463FL
    const val MOTI = 0xFFEDE6D6L
    const val MOONGA = 0xFFE06A4EL
    const val PANNA = 0xFF2FA36BL
    const val PUKHRAJ = 0xFFE2B33CL
    const val HEERA = 0xFFDDE7F2L
    const val NEELAM = 0xFF4C78E0L
    /** Rahu; graha screens only. */
    const val GOMED = 0xFFB07A4EL
    /** Ketu; graha screens only. */
    const val LEHSUNIA = 0xFFB9C2A8L

    // Utsav
    const val UTSAV_GROUND_TOP = 0xFF5A1020L
    const val UTSAV_GROUND_BOTTOM = 0xFF16040AL
    const val UTSAV_LACQUER_TOP = 0xFF3A0C17L
    const val UTSAV_LACQUER_BOTTOM = 0xFF260810L
    // Chandan (light)
    const val CHANDAN_BG_TOP = 0xFFFBF4E6L
    const val CHANDAN_BG_BOTTOM = 0xFFF3E6CCL
    const val CHANDAN_LACQUER = 0xFFFFFBF2L
    const val CHANDAN_BORDER = 0xFFE3CFA2L
    const val CHANDAN_TEXT = 0xFF2A1A12L
    const val CHANDAN_GOLD_TEXT = 0xFF8C6214L
    const val CHANDAN_KUMKUM = 0xFFB3202AL
    // Paper (regional views)
    const val PAPER_BG = 0xFFFFFDF6L
    const val PAPER_HOLIDAY = 0xFFC0392BL

    /** Colours of one mood as ARGB Longs. [RatnaColors] is the Compose view of this. */
    data class Spec(
        val mood: RatnaMood,
        val isDark: Boolean,
        val bg0: Long, val bg1: Long,
        val lacquerTop: Long, val lacquerBottom: Long,
        val ink3: Long, val line: Long,
        val text: Long, val text2: Long, val text3: Long, val onSky: Long,
        /** Gold for text and icons; bright haldi on dark moods, deep gold on light ones (gold text never sits on ivory at haldi). */
        val gold: Long,
        /** Fill of the primary button and filled gold shapes. */
        val goldFill: Long, val onGoldFill: Long,
        val kumkum: Long, val okay: Long,
        val glassTintTop: Long, val glassTintBottom: Long, val glassBorder: Long,
    ) {
        /** Surfaces text must be legible on. */
        val surfaces: List<Long> get() = listOf(bg0, bg1, lacquerTop, lacquerBottom)
    }

    private fun fit(seed: Long, surfaces: List<Long>, darken: Boolean) = adjustForContrast(seed, surfaces, darken)

    val night = Spec(
        RatnaMood.NIGHT, true, INK0, INK0, INK2, INK1, INK3, LINE,
        CHANDAN, CHANDAN2, CHANDAN3, WHITE,
        HALDI, HALDI, INK0, KUMKUM, OKAY,
        GLASS_TINT_TOP, GLASS_TINT_BOTTOM, GLASS_BORDER,
    )

    private val utsavSurfaces = listOf(UTSAV_GROUND_TOP, UTSAV_GROUND_BOTTOM, UTSAV_LACQUER_TOP, UTSAV_LACQUER_BOTTOM)
    /** Utsav keeps the night text roles; the tertiary text and kumkum are lifted by rule where the plum ground needs it. */
    val utsav = Spec(
        RatnaMood.UTSAV, true, UTSAV_GROUND_TOP, UTSAV_GROUND_BOTTOM, UTSAV_LACQUER_TOP, UTSAV_LACQUER_BOTTOM,
        0xFF4A1220L, LINE,
        CHANDAN, CHANDAN2, fit(CHANDAN3, utsavSurfaces, darken = false), WHITE,
        HALDI, HALDI, INK0, fit(KUMKUM, utsavSurfaces, darken = false), OKAY,
        0x943A0C17L, 0xA8260810L, GLASS_BORDER,
    )

    private val chandanSurfaces = listOf(CHANDAN_BG_TOP, CHANDAN_BG_BOTTOM, CHANDAN_LACQUER)
    val chandan = Spec(
        RatnaMood.CHANDAN, false, CHANDAN_BG_TOP, CHANDAN_BG_BOTTOM, CHANDAN_LACQUER, CHANDAN_LACQUER,
        0xFFF0E2C4L, 0x1F2A1A12L,
        CHANDAN_TEXT,
        fit(0xFF5C4A3AL, chandanSurfaces, darken = true),
        fit(0xFF7A6650L, chandanSurfaces, darken = true),
        WHITE,
        fit(CHANDAN_GOLD_TEXT, chandanSurfaces, darken = true), HALDI_DEEP, CHANDAN_TEXT,
        fit(CHANDAN_KUMKUM, chandanSurfaces, darken = true),
        fit(0xFF5A6078L, chandanSurfaces, darken = true),
        GLASS_TINT_TOP_CHANDAN, GLASS_TINT_TOP_CHANDAN, GLASS_BORDER_CHANDAN,
    )

    private val paperSurfaces = listOf(PAPER_BG)
    val paper = Spec(
        RatnaMood.PAPER, false, PAPER_BG, PAPER_BG, PAPER_BG, PAPER_BG,
        0xFFF4EEDDL, 0x1F2A1A12L,
        CHANDAN_TEXT,
        fit(0xFF5C4A3AL, paperSurfaces, darken = true),
        fit(0xFF7A6650L, paperSurfaces, darken = true),
        WHITE,
        fit(CHANDAN_GOLD_TEXT, paperSurfaces, darken = true), HALDI_DEEP, CHANDAN_TEXT,
        fit(PAPER_HOLIDAY, paperSurfaces, darken = true),
        fit(0xFF5A6078L, paperSurfaces, darken = true),
        GLASS_TINT_TOP_CHANDAN, GLASS_TINT_TOP_CHANDAN, GLASS_BORDER_CHANDAN,
    )

    fun of(mood: RatnaMood): Spec = when (mood) {
        RatnaMood.NIGHT -> night
        RatnaMood.UTSAV -> utsav
        RatnaMood.CHANDAN -> chandan
        RatnaMood.PAPER -> paper
    }

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
