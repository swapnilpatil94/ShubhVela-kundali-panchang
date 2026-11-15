package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.model.profile.RecordId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class AajCacheTest {
    private fun key(i: Int) = AajKey(LocalDate(2026, 11, 1 + i), RecordId("0".repeat(32)), 0, 1)

    @Test fun `evicts the least recently used beyond eight`() {
        val c = AajCache<Int>()
        for (i in 0 until 8) c.put(key(i), i)
        assertEquals(0, c.get(key(0))) // touch 0, so 1 is the eldest
        c.put(key(8), 8)
        assertEquals(8, c.size())
        assertNull(c.get(key(1)))
        assertEquals(0, c.get(key(0)))
    }

    @Test fun `different settings hash is a different key`() {
        val c = AajCache<Int>()
        c.put(key(0), 1)
        assertNull(c.get(key(0).copy(settingsHash = 2)))
        assertNull(c.get(key(0).copy(offsetSeconds = 60)))
    }
}
