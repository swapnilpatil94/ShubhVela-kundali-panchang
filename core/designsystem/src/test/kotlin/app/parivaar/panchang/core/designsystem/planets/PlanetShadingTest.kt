package app.parivaar.panchang.core.designsystem.planets

import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The CPU reference of the planet shader: phases, terminator orientation, rings and their shadows. */
class PlanetShadingTest {
    private val n = 240

    private class Lit(val fraction: Double, val angleDeg: Double)

    /** Lit share of the Moon's disc from the CPU shading (I > 0.02, above the earthshine) and the bright-limb angle. */
    private fun lit(light: PlanetLight): Lit {
        val style = planetStyle(PlanetBody.MOON)
        val sun = floatArrayOf(light.sunX, light.sunY, light.sunZ)
        var disc = 0
        var on = 0
        var cx = 0.0
        var cy = 0.0
        for (y in 0 until n) for (x in 0 until n) {
            val px = (x + 0.5f - n / 2f) / (n / 2f)
            val py = (y + 0.5f - n / 2f) / (n / 2f)
            val r2 = px * px + py * py
            if (r2 >= 1f) continue
            disc++
            val mu = sqrt(1f - r2)
            val mu0 = px * sun[0] - py * sun[1] + mu * sun[2]
            if (PlanetShading.discIntensity(style.model, style.soft, style.limb, mu, mu0) > 0.02f) {
                on++
                cx += px
                cy += py
            }
        }
        // Angle of the lit centroid from "up" toward "left" (y down on screen): the theta of planetLightOf.
        val angle = Math.toDegrees(atan2(-cx, -cy)).let { if (it < 0) it + 360.0 else it }
        return Lit(on.toDouble() / disc, angle)
    }

    private fun moon(i: Double, chi: Double, q: Double? = null) =
        planetLightOf(PlanetBody.MOON, i, chi, q, false, null)

    @Test
    fun illuminatedFractionAtTheQuarters() {
        assertEquals(0.0, lit(moon(180.0, 270.0)).fraction, 0.01) // new
        assertEquals(0.5, lit(moon(90.0, 270.0)).fraction, 0.01) // first quarter
        assertEquals(1.0, lit(moon(0.0, 270.0)).fraction, 0.01) // full
        assertEquals(0.5, lit(moon(90.0, 90.0)).fraction, 0.01) // last quarter
        for (i in listOf(30.0, 60.0, 120.0, 150.0)) {
            assertEquals((1 + Math.cos(Math.toRadians(i))) / 2, lit(moon(i, 200.0)).fraction, 0.015, "i=$i")
        }
    }

    @Test
    fun terminatorOrientationFollowsTheta() {
        assertEquals(270.0, lit(moon(90.0, 270.0)).angleDeg, 1.0) // first quarter: right side
        assertEquals(90.0, lit(moon(90.0, 90.0)).angleDeg, 1.0) // last quarter: left side
        assertEquals(180.0, lit(moon(60.0, 270.0, 90.0)).angleDeg, 1.0) // q turns it to the bottom
        assertEquals(35.0, lit(moon(120.0, 35.0)).angleDeg, 1.0)
    }

    @Test
    fun renderedFrameMatchesTheLightingFunction() {
        val px = PlanetShading.render(PlanetBody.MOON, moon(90.0, 270.0), 64)
        val right = px[32 * 64 + 56]
        val left = px[32 * 64 + 8]
        val corner = px[0]
        assertEquals(0, corner ushr 24, "outside the disc is transparent")
        assertEquals(255, right ushr 24)
        assertTrue(lum(right) > 4 * lum(left), "first quarter: right half bright")
        assertTrue(lum(left) > 0, "earthshine keeps the night side faintly visible")
    }

    @Test
    fun ringBandsAndOcclusion() {
        assertEquals(0f, PlanetShading.ringAlpha(1.1f))
        assertTrue(PlanetShading.ringAlpha(1.7f) > 0.6f) // B ring (0.85 x ripple 0.76..1)
        assertTrue(PlanetShading.ringAlpha(1.99f) < 0.12f) // Cassini division
        assertTrue(PlanetShading.ringAlpha(2.15f) > 0.45f) // A ring
        assertEquals(0f, PlanetShading.ringAlpha(2.3f))
        val b = Math.toRadians(20.0)
        val sinB = Math.sin(b).toFloat()
        val cosB = Math.cos(b).toFloat()
        val y = (1.7 * Math.sin(b)).toFloat()
        assertTrue(PlanetShading.visibleRingAlpha(0f, y, sinB, cosB) > 0.6f, "near half crosses in front of the disc")
        assertEquals(0f, PlanetShading.visibleRingAlpha(0f, -y, sinB, cosB), "far half is hidden by the disc")
        assertTrue(PlanetShading.visibleRingAlpha(2.15f, 0f, sinB, cosB) > 0.45f)
    }

    @Test
    fun shadowsBothWays() {
        // Planet on ring: a ring point on the night side of the planet is shadowed, the day side is not.
        assertTrue(PlanetShading.planetShadowOnRing(floatArrayOf(1.6f, 0f, 0f), floatArrayOf(-1f, 0f, 0f)))
        assertTrue(!PlanetShading.planetShadowOnRing(floatArrayOf(-1.6f, 0f, 0f), floatArrayOf(-1f, 0f, 0f)))
        // Ring on disc: with the Sun below the ring plane part of the lit disc is shaded; edge-on rings cast none.
        val b = Math.toRadians(25.0)
        val sinB = Math.sin(b).toFloat()
        val cosB = Math.cos(b).toFloat()
        val sun = floatArrayOf(0.3f, -0.5f, 0.81f).let { v ->
            val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
        }
        var shaded = 0
        var edgeOnShaded = 0
        for (y in -20..20) for (x in -20..20) {
            val px = x / 20f
            val py = y / 20f
            val r2 = px * px + py * py
            if (r2 >= 1f) continue
            val nv = floatArrayOf(px, -py, sqrt(1f - r2))
            val dayside = nv[0] * sun[0] + nv[1] * sun[1] + nv[2] * sun[2] > 0f
            if (dayside && PlanetShading.ringShadowOnDisc(nv, sun, sinB, cosB) < 0.5f) shaded++
            if (dayside && PlanetShading.ringShadowOnDisc(nv, sun, 0f, 1f) < 1f) edgeOnShaded++
        }
        assertTrue(shaded > 10, "ring shadow band on the disc: $shaded")
        assertEquals(0, edgeOnShaded)
    }

    @Test
    fun unlitRingFaceIsDimmer() {
        val lit = PlanetLight(0.2f, 0.6f, 0.77f, 20f)
        val unlit = PlanetLight(0.2f, -0.6f, 0.77f, 20f)
        val a = PlanetShading.render(PlanetBody.SATURN, lit, 92)
        val c = PlanetShading.render(PlanetBody.SATURN, unlit, 92)
        val i = 46 * 92 + 46 + (2.15f * planetRadiusPx(PlanetBody.SATURN, 92f)).toInt() // A ring, right ansa
        assertTrue(lum(c[i]) < 0.6 * lum(a[i]), "unlit ${lum(c[i])} lit ${lum(a[i])}")
    }

    @Test
    fun emissiveSunIsLimbDarkened() {
        val s = planetStyle(PlanetBody.SUN)
        val centre = PlanetShading.discIntensity(s.model, s.soft, s.limb, 1f, -1f)
        val edge = PlanetShading.discIntensity(s.model, s.soft, s.limb, 0.1f, -1f)
        assertEquals(1f, centre, 1e-6f)
        assertTrue(edge < 0.5f)
    }

    @Test
    fun framesUseTheDesignTextures() {
        val mars = PlanetShading.render(PlanetBody.MARS, PlanetLight.FULL, 64)[32 * 64 + 32]
        val r = (mars shr 16) and 0xFF
        val g = (mars shr 8) and 0xFF
        val b = mars and 0xFF
        assertTrue(r > g && g > b && r > 120, "rust-red Mars: $r $g $b")
        val sat = PlanetShading.render(PlanetBody.SATURN, PlanetLight(0f, 0f, 1f, 20f), 92)
        val ansa = sat[46 * 92 + 46 + (1.7f * planetRadiusPx(PlanetBody.SATURN, 92f)).toInt()]
        assertTrue(ansa ushr 24 > 120, "B ring opaque outside the disc")
    }

    private fun lum(argb: Int): Int = ((argb shr 16) and 0xFF) + ((argb shr 8) and 0xFF) + (argb and 0xFF)
}
