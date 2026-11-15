@file:Suppress("MaxLineLength")
package app.parivaar.panchang.core.designsystem

import app.parivaar.panchang.core.designsystem.surfaces.GlassKind
import app.parivaar.panchang.core.designsystem.surfaces.SurfaceMath
import app.parivaar.panchang.core.designsystem.surfaces.SurfacePolicy
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfacePolicyTest {
    private fun kind(sdk: Int, rt: Boolean = false, bs: Boolean = false, rm: Boolean = false, backdrop: Boolean = true) =
        SurfacePolicy.glassKind(sdk, rt, bs, rm, backdrop)

    @Test fun ladderByApiLevel() {
        assertEquals(GlassKind.SOLID, kind(26))
        assertEquals(GlassKind.SOLID, kind(30))
        assertEquals(GlassKind.BLUR_STATIC, kind(31))
        assertEquals(GlassKind.BLUR_STATIC, kind(32))
        assertEquals(GlassKind.BLUR_TILT, kind(33))
        assertEquals(GlassKind.BLUR_TILT, kind(36))
    }

    @Test fun reduceTransparencyBatterySaverAndNoBackdropForceSolid() {
        for (sdk in listOf(26, 30, 31, 33, 36)) {
            assertEquals(GlassKind.SOLID, kind(sdk, rt = true), "rt $sdk")
            assertEquals(GlassKind.SOLID, kind(sdk, bs = true), "saver $sdk")
            assertEquals(GlassKind.SOLID, kind(sdk, backdrop = false), "backdrop $sdk")
        }
    }

    @Test fun reduceMotionStopsTiltButKeepsBlur() {
        assertEquals(GlassKind.BLUR_STATIC, kind(34, rm = true))
        assertTrue(SurfacePolicy.tiltActive(34, false, false, false))
        assertTrue(!SurfacePolicy.tiltActive(34, false, false, true))
        assertTrue(!SurfacePolicy.tiltActive(34, false, true, false))
        assertTrue(!SurfacePolicy.tiltActive(34, true, false, false))
        assertTrue(!SurfacePolicy.tiltActive(32, false, false, false))
    }

    @Test fun exhaustiveMatrixNeverBlursWhenForbidden() {
        val flags = listOf(false, true)
        val cases = (21..36).flatMap { sdk -> flags.flatMap { rt -> flags.flatMap { bs -> flags.map { rm -> listOf(sdk, rt, bs, rm) } } } }
        for (c in cases) {
            val sdk = c[0] as Int
            val rt = c[1] as Boolean
            val bs = c[2] as Boolean
            val rm = c[3] as Boolean
            val k = kind(sdk, rt, bs, rm)
            if (rt || bs || sdk < 31) assertEquals(GlassKind.SOLID, k)
            if (k == GlassKind.BLUR_TILT) assertTrue(sdk >= 33 && !rm)
        }
    }

    @Test fun lacquerBordersFollowRoles() {
        val n = PpPalette.night
        assertEquals(PpPalette.LINE, SurfaceMath.lacquerBorder(n, hero = false, warn = false))
        assertEquals(0x8CE2B33CL, SurfaceMath.lacquerBorder(n, hero = true, warn = false)) // .55 gold
        assertEquals(0x73E8636AL, SurfaceMath.lacquerBorder(n, hero = false, warn = true)) // .45 kumkum
        assertEquals(0x73E8636AL, SurfaceMath.lacquerBorder(n, hero = true, warn = true)) // caution wins
    }

    @Test fun solidFallbackIsInk3PlusGoldHairline() {
        assertEquals(PpPalette.INK3, SurfaceMath.solidFill(PpPalette.night))
        assertEquals(0x8CE2B33CL, SurfaceMath.solidHairline(PpPalette.night))
        for (m in RatnaMood.entries) {
            val spec = PpPalette.of(m)
            assertEquals(0xFFL, SurfaceMath.solidFill(spec) ushr 24)
            // Content text stays legible on the solid fill.
            assertTrue(app.parivaar.panchang.core.designsystem.tokens.contrastRatio(spec.text, SurfaceMath.solidFill(spec)) >= 4.5)
        }
    }

    @Test fun withAlphaKeepsRgb() {
        assertEquals(0x80123456L, SurfaceMath.withAlpha(0xFF123456L, 0.5f))
        assertEquals(0x00123456L, SurfaceMath.withAlpha(0xFF123456L, -1f))
        assertEquals(0xFF123456L, SurfaceMath.withAlpha(0x00123456L, 2f))
    }

    @Test fun tiltLowPassAndClamp() {
        assertEquals(1f, SurfaceMath.lowPassTilt(0f, 10f), 1e-6f)
        assertEquals(10f, SurfaceMath.lowPassTilt(10f, 90f))
        assertEquals(-10f, SurfaceMath.lowPassTilt(-10f, -90f))
        var t = 0f
        repeat(200) { t = SurfaceMath.lowPassTilt(t, 4f) }
        assertEquals(4f, t, 1e-3f)
        assertEquals(0.5f, SurfaceMath.specularX(0f))
        assertEquals(0.85f, SurfaceMath.specularX(50f), 1e-6f)
        assertEquals(0.15f, SurfaceMath.specularX(-10f), 1e-6f)
    }
}
