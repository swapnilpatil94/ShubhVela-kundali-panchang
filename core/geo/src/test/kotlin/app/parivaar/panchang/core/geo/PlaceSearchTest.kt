package app.parivaar.panchang.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceSearchTest {
    private val store = SeedTestSupport.store
    private fun idOf(name: String) = store.places.first { it.name == name }.id

    @Test fun prayagrajUnderEveryName() {
        val id = idOf("Prayagraj")
        val queries = listOf("Prayagraj", "Allahabad", "allahabad ", "prayag", "प्रयागराज", "इलाहाबाद")
        queries.forEachIndexed { i, q -> assertEquals(id, store.search(q).firstOrNull()?.place?.id, "query index $i") }
    }

    @Test fun matchedNameIsOriginalText() {
        val hit = store.search("allahabad").first()
        assertEquals("Allahabad", hit.matchedName)
        assertEquals(0, hit.tier)
    }

    @Test fun alternateNames() {
        assertEquals(idOf("Mumbai"), store.search("Bombay").first().place.id)
        assertEquals(idOf("Kolkata"), store.search("Calcutta").first().place.id)
        assertEquals(idOf("Bengaluru"), store.search("bangalore").first().place.id)
        assertEquals(idOf("Tromsø"), store.search("tromso").first().place.id)
    }

    @Test fun shortQueryIsEmpty() {
        assertTrue(store.search("a").isEmpty())
        assertTrue(store.search(" a ").isEmpty())
        assertTrue(store.search("").isEmpty())
        assertTrue(store.search("--").isEmpty())
    }

    @Test fun tier2TokenPrefixes() {
        val hit = store.search("york new").first()
        assertEquals(idOf("New York"), hit.place.id)
        assertEquals(2, hit.tier)
    }

    @Test fun distinctTokensRequired() {
        assertTrue(store.search("new new").none { it.place.name == "New York" && it.tier == 2 })
    }

    @Test fun rankingOrderTierThenPopulationThenName() {
        val hits = store.search("ba")
        assertTrue(hits.size > 1)
        val keys = hits.map { it.tier }
        assertEquals(keys.sorted(), keys)
        for (i in 1 until hits.size) {
            val a = hits[i - 1]
            val b = hits[i]
            if (a.tier == b.tier) assertTrue(a.place.population >= b.place.population)
        }
        // exact beats prefix even with smaller population
        val exact = SeedPlaceStore(
            "id\tname\tadmin1\tcc\tlat\tlon\televM\tpop\tzone\toverlay\talt\tstatus\n" +
                "1\tBigville\t\tIN\t1\t1\t\t900\tAsia/Kolkata\t\t\tVERIFY\n" +
                "2\tBig\t\tIN\t2\t2\t\t10\tAsia/Kolkata\t\t\tVERIFY\n",
            emptySet(),
        )
        assertEquals(listOf(2L, 1L), exact.search("big").map { it.place.id })
    }

    @Test fun limitClamp() {
        // "ma" prefixes many seed names (Mumbai, Madurai, Manchester, ...)
        assertTrue(store.search("ma", 1000).size in 4..50)
        assertEquals(1, store.search("ma", 0).size)
        assertEquals(1, store.search("ma", -5).size)
        assertEquals(3, store.search("ma", 3).size)
    }
}
