package app.parivaar.panchang.platform.tile

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TileStateTest {
    @Test fun successPassesThrough() = assertEquals("a" to "b", safeTile("x") { "a" to "b" })

    @Test fun anyExceptionFallsBackToUnavailable() {
        assertEquals("x" to null, safeTile("x") { error("engine") })
        assertEquals("x" to null, safeTile("x") { throw IllegalArgumentException() })
    }

    @Test fun cancellationIsNotSwallowed() {
        assertFailsWith<CancellationException> { safeTile("x") { throw CancellationException() } }
    }
}
