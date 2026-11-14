package app.parivaar.panchang.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SeedParseTest {
    private val header = "id\tname\tadmin1\tcc\tlat\tlon\televM\tpop\tzone\toverlay\talt\tstatus"
    private fun row(
        id: String = "2000000001", lat: String = "19.0760", lon: String = "72.8777", zone: String = "Asia/Kolkata",
        overlay: String = "", extra: String = "",
    ) = "$id\tMumbai\tMH\tIN\t$lat\t$lon\t14\t12000000\t$zone\t$overlay\tBombay\tVERIFY$extra"

    private fun parse(vararg rows: String) = SeedPlaceStore((listOf(header) + rows).joinToString("\n"), setOf("bombay"))

    @Test fun realSeedIsSmallAndAllVerify() {
        val lines = SeedTestSupport.tsv.lines().filter { it.isNotEmpty() }
        assertTrue(lines.size - 1 <= 200)
        assertTrue(lines.drop(1).all { it.split('\t').last() == "VERIFY" })
        assertEquals(lines.size - 1, SeedTestSupport.store.places.size)
        assertTrue(SeedTestSupport.store.places.all { it.id >= 2_000_000_001L })
    }

    @Test fun realSeedHasRequiredCities() {
        val names = SeedTestSupport.store.places.map { it.name }.toSet()
        val required = listOf(
            "Mumbai", "Delhi", "Kolkata", "Chennai", "Ujjain", "London", "New York", "Auckland", "Suva", "Prayagraj",
            "Karachi", "Lahore", "Dhaka", "Kathmandu", "Colombo", "Apia", "Kiritimati", "Santiago", "Sydney",
        )
        for (n in required) assertTrue(n in names, "missing a required seed city")
        val byName = SeedTestSupport.store.places.associateBy { it.name }
        assertEquals("bombay", byName.getValue("Mumbai").overlayKey)
        assertEquals("calcutta", byName.getValue("Kolkata").overlayKey)
    }

    @Test fun validRowParses() {
        assertEquals(1, parse(row(overlay = "bombay")).places.size)
    }

    @Test fun rejectsBadColumnCount() { assertFailsWith<IllegalArgumentException> { parse(row(extra = "\textra")) } }

    @Test fun rejectsDuplicateId() { assertFailsWith<IllegalArgumentException> { parse(row(), row()) } }

    @Test fun rejectsOutOfRangeCoordinates() {
        assertFailsWith<IllegalArgumentException> { parse(row(lat = "90.5")) }
        assertFailsWith<IllegalArgumentException> { parse(row(lon = "-180.5")) }
    }

    @Test fun rejectsUnknownZone() { assertFailsWith<IllegalArgumentException> { parse(row(zone = "Mars/Olympus")) } }

    @Test fun rejectsUnknownOverlayKey() { assertFailsWith<IllegalArgumentException> { parse(row(overlay = "nope")) } }

    @Test fun errorMessagesCarryNoPlaceData() {
        val e = assertFailsWith<IllegalArgumentException> { parse(row(zone = "Mars/Olympus")) }
        assertTrue("Mars" !in e.message.orEmpty() && "Mumbai" !in e.message.orEmpty())
    }
}
