package app.parivaar.panchang.engine.astro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The golden gates rely on [MaxError]: a non-finite error must fail them, never be skipped. */
class MaxErrorTest {
    @Test
    fun keepsTheLargestFiniteError() {
        val max = MaxError()
        max.add(0.5, "a")
        max.add(2.0, "b")
        max.add(1.0, "c")
        assertEquals(2.0, max.value)
        assertEquals("b", max.where)
        assertEquals(3, max.count)
    }

    @Test
    fun nanAfterFiniteValuesFailsEveryLimit() {
        val max = MaxError()
        max.add(0.001, "a")
        max.add(Double.NaN, "b")
        max.add(0.002, "c")
        assertEquals(Double.POSITIVE_INFINITY, max.value)
        assertTrue(max.value > 1e300)
        assertTrue(max.where.startsWith("b"))
    }

    @Test
    fun nanAsFirstVectorFailsEveryLimit() {
        val max = MaxError()
        max.add(Double.NaN, "a")
        max.add(0.002, "b")
        assertEquals(Double.POSITIVE_INFINITY, max.value)
        assertTrue(max.where.startsWith("a"))
    }

    @Test
    fun infiniteErrorFailsEveryLimit() {
        val max = MaxError()
        max.add(Double.POSITIVE_INFINITY, "a")
        max.add(Double.NEGATIVE_INFINITY, "b")
        assertEquals(Double.POSITIVE_INFINITY, max.value)
        assertTrue(max.where.startsWith("a"))
    }
}
