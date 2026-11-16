package app.parivaar.panchang.content.text

import app.parivaar.panchang.content.pack.Terms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TermResolverTest {
    private val r = Fixture.resolver(mapOf("a.b" to "en text"), mapOf("a.b" to "hi text"))

    @Test fun directHit() {
        assertEquals(Resolved("hi text", "hi", false), r.text("a.b", "hi"))
        assertEquals(Resolved("en text", "en", false), r.text("a.b", "en"))
    }

    @Test fun marathiFallsBackToEnglishFlagged() {
        assertEquals(Resolved("en text", "en", true), r.text("a.b", "mr"))
        assertEquals(Resolved("Karka", "en", true), r.term(TermKind.RASHI, 4, "mr"))
    }

    @Test fun unknownLanguageFallsBackToEnglish() {
        assertEquals(Resolved("en text", "en", true), r.text("a.b", "ta"))
    }

    @Test fun termsInLanguage() {
        assertEquals(Resolved("hi-Karka", "hi", false), r.term(TermKind.RASHI, 4, "hi"))
    }

    @Test fun missingTermArrayFallsBack() {
        val noHiTerms = Fixture.resolver(mapOf("a.b" to "x"), hiTerms = Terms.EMPTY)
        assertEquals(Resolved("g9", "en", true), noHiTerms.term(TermKind.GRAHA, 9, "hi"))
    }

    @Test fun missEverywhereThrows() {
        assertFailsWith<IllegalStateException> { r.text("x.y", "hi") }
    }

    @Test fun termIndexOutOfRangeThrows() {
        assertFailsWith<IllegalArgumentException> { r.term(TermKind.TARA, 10, "en") }
    }
}
