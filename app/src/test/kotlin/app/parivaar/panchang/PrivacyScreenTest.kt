package app.parivaar.panchang

import kotlin.test.Test
import kotlin.test.assertEquals

/** Fable review item 9: the privacyScreen setting sets and clears FLAG_SECURE (8192) and nothing else. */
class PrivacyScreenTest {
    @Test fun setsAndClearsOnlyFlagSecure() {
        val calls = mutableListOf<Pair<Int, Int>>()
        PrivacyScreen.apply(true) { f, m -> calls += f to m }
        PrivacyScreen.apply(false) { f, m -> calls += f to m }
        assertEquals(listOf(8192 to 8192, 0 to 8192), calls)
    }
}
