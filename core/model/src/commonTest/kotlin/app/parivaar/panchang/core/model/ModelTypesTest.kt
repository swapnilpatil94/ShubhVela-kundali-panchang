package app.parivaar.panchang.core.model

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.model.profile.BirthData
import app.parivaar.panchang.core.model.profile.RecordId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ModelTypesTest {
    private val id = RecordId("0123456789abcdef0123456789abcdef")
    private val epoch = kotlin.time.Instant.fromEpochSeconds(0)
    private val traits = BondTraits("Asha", 26, 4, 11, null, 2, 0, 11, null, null, null)

    @Test
    fun recordIdRejectsBadValuesWithoutEchoing() {
        for (bad in listOf("", "ABCDEF0123456789ABCDEF0123456789", "0123456789abcdef0123456789abcde", "g".repeat(32))) {
            val e = assertFailsWith<IllegalArgumentException> { RecordId(bad) }
            assertFalse(e.message.orEmpty().contains(bad.ifEmpty { "\u0000" }))
        }
    }

    @Test
    fun birthTimeFlagMustMatch() {
        val d = LocalDate(1990, 1, 2)
        assertFailsWith<IllegalArgumentException> { BirthData(d, null, true) }
        assertFailsWith<IllegalArgumentException> { BirthData(d, LocalTime(1, 2, 3), false) }
        BirthData(d, null, false)
    }

    @Test
    fun bondRanges() {
        assertFailsWith<IllegalArgumentException> { traits.copy(moonNakshatra = 27) }
        assertFailsWith<IllegalArgumentException> { traits.copy(moonPada = 0) }
        assertFailsWith<IllegalArgumentException> { traits.copy(name = "") }
        assertFailsWith<IllegalArgumentException> { traits.copy(name = "x".repeat(13)) }
        assertFailsWith<IllegalArgumentException> { traits.copy(manglik = 3) }
        assertFailsWith<IllegalArgumentException> { traits.copy(lagna = 12) }
        assertFailsWith<IllegalArgumentException> { Bond(id, traits, "Bad Kind", epoch) }
    }

    @Test
    fun toStringIsRedacted() {
        assertEquals("BondTraits(redacted)", traits.toString())
        assertFalse(traits.toString().contains("Asha"))
        assertFalse(BirthData(LocalDate(1990, 1, 2), null, false).toString().contains("1990"))
        assertEquals("Bond(id=${id.hex})", Bond(id, traits, "friend", epoch).toString())
    }
}
