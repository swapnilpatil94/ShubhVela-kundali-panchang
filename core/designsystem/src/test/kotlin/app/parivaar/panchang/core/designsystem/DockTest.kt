package app.parivaar.panchang.core.designsystem

import app.parivaar.panchang.core.designsystem.components.DockScrollRule
import app.parivaar.panchang.core.designsystem.components.DockSpec
import app.parivaar.panchang.core.designsystem.components.OrbGeometry
import app.parivaar.panchang.core.designsystem.components.OrbTone
import app.parivaar.panchang.core.designsystem.components.dockVisible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockTest {
    @Test fun ringSweepIs360TimesFraction() {
        assertEquals(360f, OrbGeometry.sweepDegrees(1f))
        assertEquals(90f, OrbGeometry.sweepDegrees(0.25f), 1e-4f)
        assertEquals(0f, OrbGeometry.sweepDegrees(0f))
        assertEquals(360f, OrbGeometry.sweepDegrees(null)) // no choghadiya: full ring
        assertEquals(360f, OrbGeometry.sweepDegrees(3f))
    }

    @Test fun endDotStartsAtTwelveAndGoesClockwise() {
        val (x0, y0) = OrbGeometry.endDot(1f, 29f) // full sweep ends where it started
        assertEquals(0f, x0, 1e-3f); assertEquals(-29f, y0, 1e-3f)
        val (x, y) = OrbGeometry.endDot(0.25f, 29f) // quarter sweep: 3 o'clock
        assertEquals(29f, x, 1e-3f); assertEquals(0f, y, 1e-3f)
    }

    @Test fun diyaIsUnlitOnlyForAvoid() {
        assertTrue(OrbGeometry.diyaLit(OrbTone.GOOD))
        assertTrue(OrbGeometry.diyaLit(OrbTone.NEUTRAL))
        assertFalse(OrbGeometry.diyaLit(OrbTone.AVOID))
    }

    @Test fun geometryMatchesDesign() {
        assertEquals(64, DockSpec.HEIGHT)
        assertEquals(12, DockSpec.MARGIN)
        assertEquals(80, DockSpec.ORB_COLUMN)
        assertEquals(17, DockSpec.ORB_RAISE)
        assertEquals(480, DockSpec.MAX_WIDTH)
        assertEquals(29f, OrbGeometry.RING_RADIUS_DP)
    }

    @Test fun hidesAfter24dpDownAndAnyUpShows() {
        val r = DockScrollRule(24f)
        assertTrue(r.onScroll(-10f)); assertTrue(r.onScroll(-14f)) // 24 is not yet over the threshold
        assertFalse(r.onScroll(-1f))
        assertFalse(r.onScroll(-100f))
        assertTrue(r.onScroll(0.5f))
        assertTrue(r.onScroll(-20f)) // accumulation restarted by the up scroll
    }

    @Test fun immersiveAlwaysHides() {
        assertFalse(dockVisible(immersive = true, scrollVisible = true))
        assertFalse(dockVisible(immersive = false, scrollVisible = false))
        assertTrue(dockVisible(immersive = false, scrollVisible = true))
    }
}
