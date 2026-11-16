package app.parivaar.panchang.content.text

import app.parivaar.panchang.content.pack.ContentPack
import app.parivaar.panchang.content.pack.Terms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Fixture terms only (not the bundled packs). */
internal object Fixture {
    private val RASHI = listOf("Mesha", "Vrishabha", "Mithuna", "Karka", "Simha", "Kanya", "Tula", "Vrishchika",
        "Dhanu", "Makara", "Kumbha", "Meena")

    fun terms(prefix: String = ""): Terms = Terms(
        mapOf(
            TermKind.RASHI to RASHI.map { prefix + it },
            TermKind.GRAHA to (1..9).map { "${prefix}g$it" },
            TermKind.NAK to (1..27).map { "${prefix}n$it" },
            TermKind.TARA to (1..9).map { "${prefix}t$it" },
        ),
    )

    fun resolver(en: Map<String, String>, hi: Map<String, String> = emptyMap(), hiTerms: Terms = terms("hi-")) =
        TermResolver(
            mapOf(
                "en" to ContentPack("en", 1, en, terms()),
                "hi" to ContentPack("hi", 1, hi, hiTerms),
                "mr" to ContentPack("mr", 1, emptyMap(), Terms.EMPTY),
            ),
        )
}

class TemplateRendererTest {
    private fun renderer(en: Map<String, String>, hi: Map<String, String> = emptyMap()) =
        TemplateRenderer(Fixture.resolver(en, hi)) { "T${it.epochSeconds}" }

    @Test fun fixedTermRef() {
        val r = renderer(mapOf("a.b" to "Moon in {rashi:4}")).render("a.b", emptyMap(), "en").getOrThrow()
        assertEquals("Moon in Karka", r.text)
        assertFalse(r.usedFallback)
    }

    @Test fun doubledBracesAreLiteral() {
        assertEquals("{x}", renderer(mapOf("a.b" to "{{x}}")).render("a.b", emptyMap(), "en").getOrThrow().text)
    }

    @Test fun namedArgsOfEveryKind() {
        val args = mapOf(
            "graha" to TemplateArg.Term(TermKind.GRAHA, 2),
            "time" to TemplateArg.Time(Instant.fromEpochSeconds(60)),
            "who" to TemplateArg.Text("x"),
        )
        val r = renderer(mapOf("a.b" to "{graha} at {time} {who}")).render("a.b", args, "en").getOrThrow()
        assertEquals("g2 at T60 x", r.text)
    }

    @Test fun termsFollowTheUserLanguage() {
        val r = renderer(mapOf("a.b" to "en {rashi:1}"), mapOf("a.b" to "hi {rashi:1}"))
            .render("a.b", emptyMap(), "hi").getOrThrow()
        assertEquals("hi hi-Mesha", r.text)
        assertFalse(r.usedFallback)
    }

    @Test fun fallbackIsFlagged() {
        val r = renderer(mapOf("a.b" to "en {rashi:1}")).render("a.b", emptyMap(), "mr").getOrThrow()
        assertEquals("en Mesha", r.text)
        assertTrue(r.usedFallback)
    }

    @Test fun missingArgFails() {
        assertTrue(renderer(mapOf("a.b" to "{graha}")).render("a.b", emptyMap(), "en").isFailure)
    }

    @Test fun unknownArgFails() {
        val args = mapOf("other" to TemplateArg.Text("x"))
        assertTrue(renderer(mapOf("a.b" to "plain")).render("a.b", args, "en").isFailure)
    }

    @Test fun badTermIndexFails() {
        assertTrue(renderer(mapOf("a.b" to "{nak:28}")).render("a.b", emptyMap(), "en").isFailure)
        assertTrue(renderer(mapOf("a.b" to "{nak:0}")).render("a.b", emptyMap(), "en").isFailure)
        val bad = mapOf("g" to TemplateArg.Term(TermKind.GRAHA, 10))
        assertTrue(renderer(mapOf("a.b" to "{g}")).render("a.b", bad, "en").isFailure)
    }

    @Test fun braceErrorsFail() {
        for (t in listOf("open {graha", "close }", "{}", "{Bad}", "{rashi:x}", "{star:1}")) {
            assertTrue(renderer(mapOf("a.b" to t)).render("a.b", emptyMap(), "en").isFailure, t)
        }
    }

    @Test fun unknownIdFails() {
        assertTrue(renderer(mapOf("a.b" to "x")).render("c.d", emptyMap(), "en").isFailure)
    }
}
