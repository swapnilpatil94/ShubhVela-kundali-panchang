package app.parivaar.panchang.core.designsystem.planets

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanetLightTest {
    private fun assertSun(x: Float, y: Float, z: Float, l: PlanetLight) {
        assertEquals(x, l.sunX, 1e-6f, "x")
        assertEquals(y, l.sunY, 1e-6f, "y")
        assertEquals(z, l.sunZ, 1e-6f, "z")
    }

    private fun moon(i: Double, chi: Double, q: Double? = null) =
        planetLightOf(PlanetBody.MOON, i, chi, q, eastOfSun = false, ringTiltDeg = 12.0)

    @Test
    fun moonPhases() {
        assertSun(0f, 0f, 1f, moon(0.0, 123.0)) // full: lit from the viewer
        assertSun(0f, 0f, -1f, moon(180.0, 40.0)) // new: lit from behind
        assertSun(1f, 0f, 0f, moon(90.0, 270.0)) // first quarter, bright limb west: right half (north up)
        assertSun(-1f, 0f, 0f, moon(90.0, 90.0)) // last quarter, bright limb east: left half
        assertSun(0f, 1f, 0f, moon(90.0, 0.0)) // bright limb north: top
        assertNull(moon(90.0, 0.0).ringTiltDeg)
    }

    @Test
    fun parallacticAngleRotatesTheMoon() {
        // theta = chi - q: chi 270 with q 90 puts the bright limb at the bottom of the zenith-up disc.
        assertSun(0f, -1f, 0f, moon(90.0, 270.0, 90.0))
        assertSun(1f, 0f, 0f, moon(90.0, 300.0, 30.0))
    }

    @Test
    fun planetsLitFromTheSunSide() {
        val east = planetLightOf(PlanetBody.VENUS, 90.0, 0.0, null, eastOfSun = true, ringTiltDeg = null)
        val west = planetLightOf(PlanetBody.VENUS, 90.0, 0.0, null, eastOfSun = false, ringTiltDeg = null)
        assertSun(1f, 0f, 0f, east)
        assertSun(-1f, 0f, 0f, west)
        val saturn = planetLightOf(PlanetBody.SATURN, 6.0, 0.0, 10.0, true, -3.5)
        assertEquals(-3.5f, saturn.ringTiltDeg)
        assertTrue(saturn.sunX > 0f && saturn.sunY == 0f)
        assertNull(planetLightOf(PlanetBody.JUPITER, 6.0, 0.0, null, true, 7.0).ringTiltDeg)
        assertEquals(PlanetLight.FULL, planetLightOf(PlanetBody.SUN, 50.0, 10.0, 3.0, true, null))
    }

    @Test
    fun sunVectorIsUnit() {
        for (i in 0..180 step 15) for (chi in 0..359 step 37) {
            val l = moon(i.toDouble(), chi.toDouble(), (chi % 50).toDouble())
            assertEquals(1f, sqrt(l.sunX * l.sunX + l.sunY * l.sunY + l.sunZ * l.sunZ), 1e-5f)
        }
    }

    @Test
    fun budgetAllowsOneAnimatedPlanet() {
        val b = PlanetAnimationBudget()
        val a = Any()
        val c = Any()
        assertTrue(b.tryAcquire(a))
        assertTrue(b.tryAcquire(a))
        assertFalse(b.tryAcquire(c))
        b.release(c)
        assertFalse(b.tryAcquire(c))
        b.release(a)
        assertTrue(b.tryAcquire(c))
    }

    @Test
    fun pauseRules() {
        val on = AnimationGate(true, true, false, false, true, true, true)
        assertTrue(on.animate)
        assertFalse(on.copy(requested = false).animate)
        assertFalse(on.copy(budgetGranted = false).animate)
        assertFalse(on.copy(reducedMotion = true).animate)
        assertFalse(on.copy(batterySaver = true).animate)
        assertFalse(on.copy(resumed = false).animate)
        assertFalse(on.copy(onScreen = false).animate)
        assertFalse(on.copy(shaderAvailable = false).animate)
    }

    @Test
    fun frameGateCapsAt30Fps() {
        val g = FrameGate()
        val ms = 1_000_000L
        assertTrue(g.take(0))
        assertFalse(g.take(16 * ms))
        assertFalse(g.take(32 * ms))
        assertTrue(g.take(33 * ms))
        assertFalse(g.take(49 * ms))
        assertTrue(g.take(70 * ms))
    }

    @Test
    fun spinWithinDesignRange() {
        assertEquals(0f, spinRadPerSec(PlanetBody.MOON)) // tidally locked
        for (b in PlanetBody.entries - PlanetBody.MOON) {
            val px = planetStyle(b).spinPxPerSec
            assertTrue(px in 2f..9f, "$b spin $px px/s")
        }
        assertEquals(50f, planetRadiusPx(PlanetBody.MARS, 100f))
        assertTrue(planetRadiusPx(PlanetBody.SATURN, 100f) * 2.269f < 50f) // A ring fits the square
    }
}
