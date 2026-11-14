package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.jyotish.Rashi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** docs/specs/M03.md section 4, `BalaTest` (synthetic indices only). */
class BalaTest {
    @Test
    fun taraIsTheNinefoldCountFromTheNatalNakshatra() {
        assertEquals(1, Bala.tara(0, 0))
        assertEquals(1, Bala.tara(9, 0))
        assertEquals(9, Bala.tara(8, 0))
        assertEquals(9, Bala.tara(4, 5))
        assertEquals(1, Bala.tara(26, 26))
        assertEquals(2, Bala.tara(0, 26))
    }

    @Test
    fun taraStaysInOneToNineForEveryPair() {
        for (a in 0..26) for (b in 0..26) assertEquals(true, Bala.tara(a, b) in 1..9)
    }

    @Test
    fun taraRejectsOutOfRangeIndicesWithoutEchoingThem() {
        val e = assertFailsWith<IllegalArgumentException> { Bala.tara(27, 0) }
        assertEquals(false, e.message.orEmpty().any { it.isDigit() })
        assertFailsWith<IllegalArgumentException> { Bala.tara(0, -1) }
    }

    @Test
    fun chandraCountsHousesFromTheNatalMoon() {
        assertEquals(12, Bala.chandra(Rashi.MEENA, Rashi.MESHA))
        assertEquals(1, Bala.chandra(Rashi.MESHA, Rashi.MESHA))
        assertEquals(2, Bala.chandra(Rashi.MESHA, Rashi.MEENA))
        assertEquals(7, Bala.chandra(Rashi.TULA, Rashi.MESHA))
    }
}
