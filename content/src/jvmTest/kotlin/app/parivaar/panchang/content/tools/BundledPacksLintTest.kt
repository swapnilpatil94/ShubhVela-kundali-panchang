package app.parivaar.panchang.content.tools

import kotlin.test.Test
import kotlin.test.assertEquals

/** The bundled source packs lint clean (0 errors), the same check as `:content:contentLint`. */
class BundledPacksLintTest {
    @Test fun zeroErrors() {
        assertEquals(listOf("en", "hi", "mr", "gu"), ContentSource.packs.keys.toList())
        assertEquals(emptyList(), ContentLint.lintDir(ContentSource.root).map { it.toString() })
    }

    @Test fun marathiAndGujaratiAreEmptyPlaceholders() {
        for (l in listOf("mr", "gu")) assertEquals(emptyMap(), ContentSource.packs.getValue(l).entries)
    }
}
