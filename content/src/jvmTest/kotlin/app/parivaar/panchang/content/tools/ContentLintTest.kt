package app.parivaar.panchang.content.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One failing fixture per rule L1-L9, each a single change to the (clean) bundled packs. */
class ContentLintTest {
    private val base = ContentSource.packs
    private val banned = ContentSource.banned

    private fun edit(lang: String, f: (SourcePack) -> SourcePack) = base + (lang to f(base.getValue(lang)))

    private fun entry(lang: String, id: String, text: String) =
        edit(lang) { it.copy(entries = it.entries + (id to text)) }

    private fun assertRule(packs: Map<String, SourcePack>, lang: String, id: String, rule: String) {
        val issues = ContentLint.lint(packs, banned)
        assertTrue(LintIssue(lang, id, rule) in issues, "expected $lang $id $rule, got $issues")
    }

    @Test fun baseIsClean() = assertEquals(emptyList(), ContentLint.lint(base, banned))

    @Test fun l1IdRegex() = assertRule(entry("en", "Bad.Id", "Calm text."), "en", "Bad.Id", "L1")

    @Test fun l2MissingRequired() =
        assertRule(edit("hi") { it.copy(entries = it.entries - "rf.retro") }, "hi", "rf.retro", "L2")

    @Test fun l2FallbackMissingForMarathi() =
        assertRule(
            edit("en") { it.copy(entries = it.entries - "rf.chandrashtama") }, "mr", "rf.chandrashtama", "L2",
        )

    @Test fun l3BannedEnglishWholeWordCaseInsensitive() {
        assertRule(entry("en", "rf.retro", "{graha} is a Remedy."), "en", "rf.retro", "L3")
        // Whole word only: "buyer" is not "buy".
        val ok = ContentLint.lint(entry("en", "rf.retro", "{graha} and the buyer."), banned)
        assertTrue(ok.none { it.rule == "L3" }, "$ok")
    }

    @Test fun l3BannedHindiSubstring() =
        assertRule(entry("hi", "rf.retro", "{graha} अभी संकटपूर्ण है।"), "hi", "rf.retro", "L3")

    @Test fun l4TooLong() {
        val long = (1..41).joinToString(" ") { "word" }
        assertRule(entry("en", "gochar.su.h1.v1", long), "en", "gochar.su.h1.v1", "L4")
        val rf = (1..31).joinToString(" ") { "calm" }
        assertRule(entry("en", "rf.chandrashtama", rf), "en", "rf.chandrashtama", "L4")
        assertRule(entry("en", "gochar.su.h1.v1", "x".repeat(401)), "en", "gochar.su.h1.v1", "L4")
    }

    @Test fun l5ArgsDifferFromEnglish() {
        assertRule(entry("hi", "rf.retro", "{planet} अभी वक्री है।"), "hi", "rf.retro", "L5")
        assertRule(entry("en", "rf.moon_shift", "The Moon moves into {rashi}."), "en", "rf.moon_shift", "L5")
        assertRule(entry("en", "gochar.su.h1.v1", "Calm {nak:28}."), "en", "gochar.su.h1.v1", "L5")
    }

    @Test fun l6Braces() =
        assertRule(entry("en", "gochar.su.h1.v1", "Calm {graha:1 day."), "en", "gochar.su.h1.v1", "L6")

    @Test fun l7NfcAndSpaces() {
        assertRule(entry("en", "gochar.su.h1.v1", " Calm day."), "en", "gochar.su.h1.v1", "L7")
        // "क़" written as क + nukta in decomposed-then-composed form is not NFC when given as U+0958.
        assertRule(entry("hi", "gochar.su.h1.v1", "क़ शांत।"), "hi", "gochar.su.h1.v1", "L7")
    }

    @Test fun l8TermsIncomplete() {
        assertRule(edit("hi") { it.copy(terms = it.terms - "nak") }, "hi", "terms", "L8")
        assertRule(edit("en") { it.copy(terms = it.terms + ("tara" to listOf("a"))) }, "en", "terms", "L8")
    }

    @Test fun l9VariantGap() = assertRule(entry("en", "gochar.su.h1.v3", "Calm day."), "en", "gochar.su.h1.v", "L9")

    @Test fun outputIsLangIdRuleOnly() = assertEquals("hi rf.retro L2", LintIssue("hi", "rf.retro", "L2").toString())
}
