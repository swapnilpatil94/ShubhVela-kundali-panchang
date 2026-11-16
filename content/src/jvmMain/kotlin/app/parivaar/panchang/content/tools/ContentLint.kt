package app.parivaar.panchang.content.tools

import app.parivaar.panchang.content.ContentIds
import app.parivaar.panchang.content.text.TemplateSyntax
import app.parivaar.panchang.content.text.TermKind
import app.parivaar.panchang.content.text.TermResolver
import java.io.File
import java.text.Normalizer
import kotlin.system.exitProcess

/** One lint error. Printed as `lang id rule` only (never the text itself). */
data class LintIssue(val lang: String, val id: String, val rule: String) {
    override fun toString(): String = "$lang $id $rule"
}

/**
 * Content lint (docs/specs/M11.md section 3 "Lint"); every finding is an error. L1 id regex; L2 required ids; L3 banned
 * terms; L4 length; L5 named args equal en's and term refs in range; L6 braces; L7 NFC and no outer space; L8 en/hi
 * terms complete; L9 variants contiguous from v1.
 */
object ContentLint {
    private val ID = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
    private val WS = Regex("(?U)\\s+")
    private val VARIANT = Regex("^(.+)\\.v([0-9]+)$")
    private const val GOCHAR_WORDS = 40
    private const val GOCHAR_CHARS = 400
    private const val RF_WORDS = 30
    private val DIRECT = setOf("en", "hi")
    private const val PACK = "-"

    fun lint(packs: Map<String, SourcePack>, banned: Map<String, List<String>>): List<LintIssue> {
        val resolver = TermResolver(packs.mapValues { it.value.toContentPack() })
        val en = packs["en"]
        val out = packs.flatMap { (lang, p) ->
            val bannedRx = banned[lang].orEmpty().map { bannedMatcher(lang, it) }
            p.entries.flatMap { (id, text) ->
                entryRules(id, text, en?.entries?.get(id), bannedRx).map { LintIssue(lang, id, it) }
            } + packRules(lang, p, resolver)
        }
        return (out + (DIRECT - packs.keys).map { LintIssue(it, PACK, "L2") }).distinct()
    }

    private fun entryRules(id: String, text: String, enText: String?, banned: List<(String) -> Boolean>): List<String> {
        val balanced = bracesBalanced(text)
        val nfc = text.isNotBlank() && Normalizer.isNormalized(text, Normalizer.Form.NFC) && text.trim() == text
        return listOfNotNull(
            "L1".takeUnless { ID.matches(id) },
            "L3".takeIf { banned.any { it(text) } },
            "L4".takeUnless { lengthOk(id, text) },
            "L6".takeUnless { balanced },
            "L5".takeIf { balanced && !argsOk(id, text, enText) },
            "L7".takeUnless { nfc },
        )
    }

    private fun packRules(lang: String, p: SourcePack, resolver: TermResolver): List<LintIssue> {
        val missing = ContentIds.REQUIRED.filterNot { id ->
            if (lang in DIRECT) p.entries.containsKey(id) else resolver.has(id, lang)
        }.map { LintIssue(lang, it, "L2") }
        val terms = listOfNotNull(
            LintIssue(lang, "terms", "L8").takeIf { lang in DIRECT && !termsComplete(p) },
            LintIssue(lang, "terms", "L7").takeIf {
                p.terms.values.flatten().any { !Normalizer.isNormalized(it, Normalizer.Form.NFC) || it.trim() != it }
            },
        )
        return missing + terms + variantGaps(p.entries.keys).map { LintIssue(lang, it, "L9") }
    }

    /** en: case-insensitive whole word; other scripts: substring after NFC. */
    private fun bannedMatcher(lang: String, term: String): (String) -> Boolean {
        val t = Normalizer.normalize(term, Normalizer.Form.NFC)
        if (lang != "en") return { text -> Normalizer.normalize(text, Normalizer.Form.NFC).contains(t) }
        val rx = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(t) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        return { text -> rx.containsMatchIn(text) }
    }

    private fun words(text: String): Int = text.trim().split(WS).count { it.isNotEmpty() }

    private fun lengthOk(id: String, text: String): Boolean = when {
        id.startsWith("gochar.") -> words(text) <= GOCHAR_WORDS && text.length <= GOCHAR_CHARS
        id.startsWith("rf.") -> words(text) <= RF_WORDS
        else -> true
    }

    /** `{{` and `}}` are literal; every other `{` closes with `}` before any other brace. */
    internal fun bracesBalanced(text: String): Boolean {
        var i = 0
        var open = false
        var ok = true
        while (ok && i < text.length) {
            val c = text[i]
            val pair = !open && (c == '{' || c == '}') && text.getOrNull(i + 1) == c
            when {
                pair -> i++
                c == '{' -> { ok = !open; open = true }
                c == '}' -> { ok = open; open = false }
            }
            i++
        }
        return ok && !open
    }

    /** Parses, term refs in range, and the named-arg set equals en's (en itself: the code contract). */
    private fun argsOk(id: String, text: String, enText: String?): Boolean {
        val mine = TemplateSyntax.namedArgs(text).getOrNull()
        val expected = when {
            enText == null -> ContentIds.ARGS[id] ?: mine
            enText == text -> ContentIds.ARGS[id].orEmpty()
            else -> TemplateSyntax.namedArgs(enText).getOrNull()
        }
        return mine != null && mine == expected
    }

    private fun termsComplete(p: SourcePack): Boolean = TermKind.entries.all { k ->
        val v = p.terms[k.key]
        v != null && v.size == k.count && v.none { it.isBlank() }
    }

    private fun variantGaps(ids: Set<String>): List<String> {
        val byPrefix = ids.mapNotNull { VARIANT.matchEntire(it) }.groupBy({ it.groupValues[1] }, { it.groupValues[2] })
        return byPrefix.filter { (_, vs) ->
            val ns = vs.map { it.toInt() }.toSet()
            vs.any { it.startsWith("0") } || ns != (1..ns.size).toSet()
        }.keys.map { "$it.v" }.sorted()
    }

    /** Lints a content-src directory. */
    fun lintDir(root: File): List<LintIssue> = lint(SourcePacks.read(root), SourcePacks.banned(root))
}

/** `contentLint` entry point: args `<content-src> <stamp file>`; prints `lang id rule` lines, exit 1 on any error. */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: ContentLint <content-src> <stamp>" }
    val issues = ContentLint.lintDir(File(args[0]))
    issues.forEach { println(it) }
    if (issues.isNotEmpty()) {
        System.err.println("contentLint: ${issues.size} error(s)")
        exitProcess(1)
    }
    File(args[1]).apply { parentFile.mkdirs() }.writeText("ok\n")
}
