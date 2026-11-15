package app.parivaar.panchang.core.designsystem

import android.view.HapticFeedbackConstants
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpHaptic
import app.parivaar.panchang.core.designsystem.tokens.PpHaptics
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood
import app.parivaar.panchang.core.designsystem.tokens.chipContainerOf
import app.parivaar.panchang.core.designsystem.tokens.contrastRatio
import app.parivaar.panchang.core.designsystem.tokens.gemFor
import app.parivaar.panchang.core.designsystem.tokens.lighten
import app.parivaar.panchang.core.model.settings.ThemeMode
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RatnaTokensTest {
    private fun hex(v: Long) = "#" + java.lang.Long.toHexString(v and 0xFFFFFFFFL)
    private fun pass(name: String, fg: Long, bg: Long, min: Double) {
        val r = contrastRatio(fg, bg)
        assertTrue(r >= min, "$name ${hex(fg)} on ${hex(bg)} = $r < $min")
    }

    private fun chipBg(p: PpPalette.Spec, role: Long) = chipContainerOf(p.lacquerBottom, role)

    @Test fun textPairsMeetAaInEveryMood() {
        for (mood in RatnaMood.entries) {
            val p = PpPalette.of(mood)
            for (bg in p.surfaces) {
                pass("$mood text", p.text, bg, 4.5)
                pass("$mood text2", p.text2, bg, 4.5)
                pass("$mood text3", p.text3, bg, 4.5)
                pass("$mood gold", p.gold, bg, 4.5)
                pass("$mood kumkum", p.kumkum, bg, 4.5)
                pass("$mood okay", p.okay, bg, 4.5)
            }
            pass("$mood text on pill", p.text, p.ink3, 4.5)
            pass("$mood text2 on pill", p.text2, p.ink3, 4.5)
            pass("$mood onGoldFill", p.onGoldFill, p.goldFill, 4.5)
            // Chips: tinted container of the surface, text in the role colour.
            for (role in listOf(p.gold, p.kumkum)) pass("$mood chip", role, chipBg(p, role), 4.5)
        }
    }

    @Test fun largeTextAndIconsAtLeast3() {
        for (mood in RatnaMood.entries) {
            val p = PpPalette.of(mood)
            for (bg in p.surfaces) {
                pass("$mood gold large", p.gold, bg, 3.0)
                if (p.isDark) pass("$mood goldFill icon", p.goldFill, bg, 3.0)
            }
        }
        // Heavy gold button gradient stops carry dark text on every stop.
        for (stop in listOf(PpPalette.GOLD_GRAD_TOP, PpPalette.GOLD_GRAD_MID, PpPalette.GOLD_GRAD_BOTTOM)) {
            pass("on gradient", PpPalette.INK0, stop, 4.5)
        }
        // Text on the sky stays on the dedicated white.
        pass("onSky on ink0", PpPalette.WHITE, PpPalette.INK0, 4.5)
    }

    @Test fun brightGoldIsDarkSurfaceOnly() {
        for (mood in RatnaMood.entries) {
            val p = PpPalette.of(mood)
            if (!p.isDark) {
                assertTrue(p.gold != PpPalette.HALDI, "$mood uses bright haldi as text")
                assertTrue(contrastRatio(PpPalette.HALDI, p.bg0) < 3.0, "haldi would be legible on $mood")
            } else {
                assertEquals(PpPalette.HALDI, p.gold)
            }
        }
    }

    @Test fun lightMoodsKeepDesignHexes() {
        assertEquals(0xFFFBF4E6L, PpPalette.chandan.bg0)
        assertEquals(0xFFF3E6CCL, PpPalette.chandan.bg1)
        assertEquals(0xFFFFFBF2L, PpPalette.chandan.lacquerTop)
        assertEquals(0xFF2A1A12L, PpPalette.chandan.text)
        assertEquals(0xFFFFFDF6L, PpPalette.paper.bg0)
        assertEquals(0x1AEDE6D6L, PpPalette.LINE)
        assertEquals(0xFF0A0D18L, PpPalette.night.bg0)
    }

    @Test fun gemsAndGradientsAreVerbatim() {
        assertEquals(0xFFD2463FL, gemFor(DayOfWeek.SUNDAY).argb)
        assertEquals(Gem.MOTI, gemFor(DayOfWeek.MONDAY))
        assertEquals(Gem.MOONGA, gemFor(DayOfWeek.TUESDAY))
        assertEquals(Gem.PANNA, gemFor(DayOfWeek.WEDNESDAY))
        assertEquals(Gem.PUKHRAJ, gemFor(DayOfWeek.THURSDAY))
        assertEquals(Gem.HEERA, gemFor(DayOfWeek.FRIDAY))
        assertEquals(0xFF4C78E0L, gemFor(DayOfWeek.SATURDAY).argb)
        assertEquals(0xFFB07A4EL, Gem.GOMED.argb)
        assertEquals(0xFFB9C2A8L, Gem.LEHSUNIA.argb)
        assertEquals(DayOfWeek.entries.toSet(), DayOfWeek.entries.filter { gemFor(it) in Gem.entries }.toSet())
    }

    @Test fun lightenedGemsStayVisibleOnNight() {
        for (g in Gem.entries) pass("$g", lighten(g.argb), PpPalette.INK0, 3.0)
    }

    @Test fun lightenMixesTowardWhiteKeepingAlpha() {
        assertEquals(0xFFFFFFFFL, lighten(0xFF000000L, 1f))
        assertEquals(0xFF123456L, lighten(0xFF123456L, 0f))
        assertEquals(0x80_40_40_40L, lighten(0x80000000L, 0.25f))
        val g = Gem.NEELAM.argb
        assertTrue(contrastRatio(lighten(g), PpPalette.INK0) > contrastRatio(g, PpPalette.INK0))
    }

    @Test fun themeModeMapsToMood() {
        assertEquals(RatnaMood.NIGHT, moodFor(ThemeMode.DARK, systemDark = false))
        assertEquals(RatnaMood.CHANDAN, moodFor(ThemeMode.LIGHT, systemDark = true))
        assertEquals(RatnaMood.NIGHT, moodFor(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(RatnaMood.CHANDAN, moodFor(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test fun hapticsMapToSemanticConstantsWithFallbacks() {
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, PpHaptics.constantFor(PpHaptic.TAB, 34))
        assertEquals(4, PpHaptics.constantFor(PpHaptic.TAB, 31))
        assertEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, PpHaptics.constantFor(PpHaptic.BEAD, 35))
        assertEquals(4, PpHaptics.constantFor(PpHaptic.BEAD, 30))
        for (h in listOf(PpHaptic.COMPLETE, PpHaptic.BELL, PpHaptic.REWARD)) {
            assertEquals(16, PpHaptics.constantFor(h, 30))
            assertEquals(0, PpHaptics.constantFor(h, 29))
        }
        assertEquals(17, PpHaptics.constantFor(PpHaptic.ERROR, 34))
        assertEquals(0, PpHaptics.constantFor(PpHaptic.ERROR, 28))
    }
}
