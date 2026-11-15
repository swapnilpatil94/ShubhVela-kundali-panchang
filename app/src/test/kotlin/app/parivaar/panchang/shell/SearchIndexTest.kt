package app.parivaar.panchang.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchIndexTest {
    private val idx =
        SearchIndex(
            listOf(
                SearchEntry(Dest.Root(Tab.AAJ), listOf("Today", "आज", "Aaj")),
                SearchEntry(Dest.Root(Tab.CALENDAR), listOf("Calendar", "कैलेंडर")),
                SearchEntry(Dest.Settings, listOf("Settings", "सेटिंग्स", "Preferences")),
                SearchEntry(Dest.Conventions, listOf("Conventions", "Calculation settings")),
                SearchEntry(Dest.Privacy, listOf("Privacy")),
            ),
        )

    @Test fun prefixBeatsWordPrefixBeatsSubstring() {
        val r = idx.query("set").map { it.dest }
        assertEquals(listOf(Dest.Settings, Dest.Conventions), r) // prefix, then word prefix ("Calculation settings")
        assertEquals(listOf(Dest.Root(Tab.CALENDAR), Dest.Conventions), idx.query("c").map { it.dest }.take(2))
        assertEquals(listOf(Dest.Conventions, Dest.Settings), idx.query("ttings").map { it.dest }.reversed())
    }

    @Test fun caseFoldAndTrim() {
        assertEquals(listOf(Dest.Privacy), idx.query("  PRIV ").map { it.dest })
    }

    @Test fun devanagariInput() {
        assertEquals(listOf(Dest.Root(Tab.AAJ)), idx.query("आ").map { it.dest })
        assertEquals(listOf(Dest.Root(Tab.CALENDAR)), idx.query("कैल").map { it.dest })
        assertEquals(listOf(Dest.Settings), idx.query("सेट").map { it.dest })
    }

    @Test fun nfcEquivalence() {
        val decomposed = "कै" // base + vowel sign AI is already canonical; also test Latin e + combining acute
        val i = SearchIndex(listOf(SearchEntry(Dest.Privacy, listOf("Café"))))
        assertEquals(1, i.query("café").size)
        assertEquals(1, i.query(decomposed.take(0) + "caf").size)
    }

    @Test fun emptyAndLimit() {
        assertEquals(emptyList(), idx.query("   "))
        assertEquals(1, idx.query("e", limit = 1).size)
        assertEquals(emptyList(), idx.query("zzz"))
    }

    @Test fun tiesKeepEntryOrder() {
        val i = SearchIndex(listOf("Alpha", "Alps", "Alto").map { SearchEntry(Dest.Privacy, listOf(it)) })
        assertEquals(listOf("Alpha", "Alps", "Alto"), i.query("al").map { it.labels.first() })
    }
}
