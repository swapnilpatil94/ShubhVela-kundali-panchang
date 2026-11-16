package app.parivaar.panchang.core.designsystem.planets

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A waiting view must see the release (snapshot state) so it can retry and take the budget. */
class PlanetBudgetTest {
    @Test fun releaseIsObservableAndTheWaiterTakesOver() {
        val b = PlanetAnimationBudget()
        val a = Any(); val w = Any()
        assertTrue(b.tryAcquire(a))
        assertFalse(b.tryAcquire(w))
        val observed = mutableListOf<Boolean>()
        Snapshot.observe(readObserver = { observed += true }) { b.isFree }
        assertTrue(observed.isNotEmpty(), "isFree must be a snapshot read")
        b.release(a)
        Snapshot.sendApplyNotifications()
        assertTrue(b.isFree)
        assertTrue(b.tryAcquire(w))
        assertTrue(b.holds(w))
        assertEquals(false, b.isFree)
    }
}
