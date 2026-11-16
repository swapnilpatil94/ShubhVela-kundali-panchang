package app.parivaar.panchang.core.designsystem.sky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ByteLruTest {
    private fun header(w: Int, h: Int) = IntArray(1).also { it[0] = w * h * 4 }

    @Test fun boundedByBytesNotCount() {
        val lru = ByteLru<Int, IntArray>(PLACEHOLDER_CACHE_BYTES) { it[0].toLong() }
        for (k in 0 until 6) lru.put(k, header(1440, 1320)) // about 7.6 MB each: only one fits
        assertTrue(lru.totalBytes() <= PLACEHOLDER_CACHE_BYTES, "${lru.totalBytes()}")
        assertEquals(1, lru.size())
        assertNotNull(lru.get(5))
        assertNull(lru.get(0))
    }

    @Test fun keepsRecentlyUsedAndDropsOversized() {
        val lru = ByteLru<String, IntArray>(10) { it[0].toLong() }
        lru.put("a", intArrayOf(4)); lru.put("b", intArrayOf(4))
        lru.get("a")
        lru.put("c", intArrayOf(4))
        assertNotNull(lru.get("a")); assertNull(lru.get("b")); assertNotNull(lru.get("c"))
        lru.put("huge", intArrayOf(11))
        assertNull(lru.get("huge"))
        assertEquals(8, lru.totalBytes())
        lru.put("a", intArrayOf(2))
        assertEquals(6, lru.totalBytes())
    }
}
