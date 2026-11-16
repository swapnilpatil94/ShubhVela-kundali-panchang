@file:Suppress("MagicNumber", "MaxLineLength", "NestedBlockDepth")

package app.parivaar.panchang.core.designsystem.sky

import app.parivaar.panchang.core.designsystem.sky.GemChipColors
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood
import app.parivaar.panchang.core.designsystem.tokens.contrastRatio
import app.parivaar.panchang.core.designsystem.tokens.ratnaColorsOf
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkyContrastTest {
    private val grid = (0..16).map { it / 16f }

    @Test fun capIsIdempotentMonotoneAndNeverDarkensBelowCap() {
        val cap = SkySafeZone.MAX_LUMINANCE
        var count = 0
        for (r in grid) for (g in grid) for (b in grid) {
            val o = capLuminance(r, g, b)
            val l = luminanceOf(r, g, b); val lo = luminanceOf(o[0], o[1], o[2])
            assertTrue(lo <= cap + 1e-4f, "L=$lo for $r,$g,$b")
            if (l <= cap) assertTrue(o[0] == r && o[1] == g && o[2] == b)
            val o2 = capLuminance(o[0], o[1], o[2])
            assertTrue(luminanceOf(o2[0], o2[1], o2[2]) <= cap + 1e-4f)
            assertTrue(kotlin.math.abs(o2[0] - o[0]) < 1e-3f && kotlin.math.abs(o2[1] - o[1]) < 1e-3f && kotlin.math.abs(o2[2] - o[2]) < 1e-3f)
            count++
        }
        assertEquals(17 * 17 * 17, count)
        val white = capLuminance(1f, 1f, 1f)
        assertEquals(cap, luminanceOf(white[0], white[1], white[2]), 1e-4f)
        // monotone along grey
        var prev = -1f
        for (v in grid) { val o = capLuminance(v, v, v); val l = luminanceOf(o[0], o[1], o[2]); assertTrue(l >= prev - 1e-6f); prev = l }
    }

    @Test fun cappedWhiteHasSevenToOneAgainstOnSkyText() {
        val w = capLuminance(1f, 1f, 1f)
        fun q(f: Float) = Math.floor((f * 255f + 1e-3f).toDouble()).toLong()
        val px = 0xFF000000L or (q(w[0]) shl 16) or (q(w[1]) shl 8) or q(w[2])
        assertTrue(contrastRatio(0xFFF6F1E7L, px) >= 7.0, "${contrastRatio(0xFFF6F1E7L, px)}")
    }

    @Test fun worstCaseHeaderContrastForEveryMood() {
        for (mood in RatnaMood.entries) {
            val text = ratnaColorsOf(mood).onSky.toArgb().toLong() and 0xFFFFFFFFL
            val worst = SkyContrastModel.worstContrast(text)
            println("worst contrast $mood = $worst")
            assertTrue(worst >= 7.0, "$mood $worst")
        }
    }

    @Test fun pipelineKeepsEveryZonePixelUnderCapForAllGradesAndWhiteImage() {
        val w = 120; val h = 100; val feather = 14f
        val inputs = Gem.entries.map { placeholderSkyPixels(it, w, h) } + listOf(IntArray(w * h) { 0xFFFFFFFF.toInt() })
        for (px in inputs) {
            applyTextSafeZone(px, w, h, feather)
            for (y in 0 until h) for (x in 0 until w) {
                if (safeZoneWeight(x, y, w, h, feather) < 1f) continue
                val p = px[y * w + x]
                val l = luminanceOf(((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f)
                assertTrue(l <= SkySafeZone.MAX_LUMINANCE + 1e-4f, "L=$l at $x,$y")
            }
        }
    }

    @Test fun placeholdersAreNeverBrightenedByTheCap() {
        val before = placeholderSkyPixels(Gem.HEERA, 60, 60)
        val after = before.copyOf(); applyTextSafeZone(after, 60, 60, 7f)
        for (i in before.indices) assertTrue((after[i] and 0xFF) <= (before[i] and 0xFF) && ((after[i] shr 8) and 0xFF) <= ((before[i] shr 8) and 0xFF))
    }

    @Test fun featherIsOneInsideAndFallsToZero() {
        assertEquals(1f, safeZoneWeight(10, 10, 100, 100, 14f))
        assertEquals(0f, safeZoneWeight(99, 99, 100, 100, 14f))
        assertTrue(safeZoneWeight(88, 10, 100, 100, 14f) in 0.01f..0.99f)
    }

    @Test fun shadeStopsMatchSpec() {
        assertEquals(0.78f, SkyShade.shadeAlpha(0f), 1e-5f); assertEquals(0.55f, SkyShade.shadeAlpha(0.55f), 1e-5f); assertEquals(0.12f, SkyShade.shadeAlpha(1f), 1e-5f)
        assertEquals(0.25f, SkyShade.fadeAlpha(0f), 1e-5f); assertEquals(0.10f, SkyShade.fadeAlpha(0.45f), 1e-5f); assertEquals(0.78f, SkyShade.fadeAlpha(0.72f), 1e-5f); assertEquals(1f, SkyShade.fadeAlpha(1f), 1e-5f)
    }

    @Test fun gemChipColoursMeetAa() {
        val fill = GemChipColors.FILL_RGB
        for (gem in Gem.entries) {
            assertTrue(contrastRatio(GemChipColors.TEXT, fill) >= 4.5)
            assertTrue(contrastRatio(GemChipColors.facet(gem), fill) >= 3.0, "$gem")
            // worst sky under the translucent pill: white
            val a = GemChipColors.FILL_ALPHA
            fun ch(s: Int) = (((fill shr s) and 0xFF) * a + 255 * (1 - a)).toLong()
            val over = 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            assertTrue(contrastRatio(GemChipColors.TEXT, over) >= 4.5, "$gem over white: ${contrastRatio(GemChipColors.TEXT, over)}")
        }
    }

    @Test fun nebulaIsDeterministicAndOpaque() {
        for (gem in Gem.entries) {
            val a = nebulaPixels(gem, 160, 140); val b = nebulaPixels(gem, 160, 140)
            assertTrue(a.contentEquals(b), "$gem")
            assertTrue(a.all { (it ushr 24) == 0xFF }, "$gem alpha")
        }
        assertTrue(!nebulaPixels(Gem.NEELAM, 160, 140).contentEquals(nebulaPixels(Gem.MANIKYA, 160, 140)))
    }

    @Test fun cappedNebulaZoneIsUnderCapStaysVisibleAndOutsideIsBrighter() {
        val w = 360; val h = 330; val feather = 14f
        for (gem in Gem.entries) {
            val px = nebulaPixels(gem, w, h); applyTextSafeZone(px, w, h, feather)
            var zoneMax = 0f; var outsideMax = 0f; var brightStars = 0
            for (y in 0 until h) for (x in 0 until w) {
                val p = px[y * w + x]
                val l = luminanceOf(((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f)
                if (safeZoneWeight(x, y, w, h, feather) >= 1f) {
                    zoneMax = max(zoneMax, l)
                    assertTrue(l <= SkySafeZone.MAX_LUMINANCE + 1e-4f, "$gem L=$l at $x,$y")
                    if (l > 0.03f) brightStars++
                } else outsideMax = max(outsideMax, l)
            }
            assertTrue(brightStars > 20, "$gem stars visible in zone: $brightStars")
            assertTrue(outsideMax > SkySafeZone.MAX_LUMINANCE, "$gem outside is brighter: $outsideMax")
        }
    }

    @Test fun nebulaGenerationTimeAndWorstCase() {
        nebulaPixels(Gem.NEELAM, 200, 180)
        val w = 1080; val h = 990; val feather = 40f
        val t0 = System.nanoTime(); val px = nebulaPixels(Gem.NEELAM, w, h); val t1 = System.nanoTime()
        applyTextSafeZone(px, w, h, feather); val t2 = System.nanoTime()
        println("nebula 1080x990 gen=${(t1 - t0) / 1_000_000} ms cap=${(t2 - t1) / 1_000_000} ms")
        assertTrue((t2 - t0) / 1_000_000 < 2_000, "generation and cap took ${(t2 - t0) / 1_000_000} ms")
        var stars = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (safeZoneWeight(x, y, w, h, feather) < 1f) continue
            val p = px[y * w + x]
            val l = luminanceOf(((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f)
            assertTrue(l <= SkySafeZone.MAX_LUMINANCE + 1e-4f, "L=$l at $x,$y")
            if (l > 0.03f) stars++
        }
        assertTrue(stars > 20, "stars visible in the zone: $stars")
    }

    /** The header text column (eyebrow, title, meta, chip) is bottom-anchored: the zone must reach its bottom edge at every height. */
    @Test fun zoneCoversTheRealTextColumnBounds() {
        for (insetDp in listOf(0f, 24f, 48f, 80f)) for (grow in listOf(0f, 120f, 400f)) {
            val hDp = SkyHeaderSpec.HEIGHT_DP + insetDp + grow
            assertTrue(SkySafeZone.textBottomFrac(hDp) <= SkySafeZone.Y_FRAC, "text bottom ${SkySafeZone.textBottomFrac(hDp)} at $hDp dp")
        }
        assertTrue(SkyHeaderSpec.TEXT_FRACTION_WITH_PLANET <= SkySafeZone.X_FRAC && SkyHeaderSpec.TEXT_FRACTION <= SkySafeZone.X_FRAC)
        // The reviewer's measurement: text spans about 58 to 93 % of the header; the old 62 % zone missed it.
        assertTrue(SkySafeZone.Y_FRAC >= 0.93f)
    }

    @Test fun horaLordSpanHasSevenToOneOverTheWorstCappedSkyForEveryGem() {
        for (gem in Gem.entries) {
            val c = SkyText.horaLordArgb(gem)
            val worst = SkyContrastModel.worstContrast(c)
            println("hora lord $gem = ${java.lang.Long.toHexString(c)} worst $worst")
            assertTrue(worst >= SkyText.MIN_CONTRAST && SkyText.MIN_CONTRAST >= 7.0, "$gem $worst")
            // still a tint of the gem, not plain white
            assertTrue(c != 0xFFFFFFFFL, "$gem")
        }
    }
}
