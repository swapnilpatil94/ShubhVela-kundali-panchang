package app.parivaar.panchang.content.text

import kotlin.time.Instant

/** A value for a named placeholder. No numeric or plural args: plurals stay in strings.xml (ICU). */
sealed interface TemplateArg {
    /** A term; [n] is 1-based. */
    data class Term(val kind: TermKind, val n: Int) : TemplateArg

    /** A moment, formatted by the caller's formatter (same as Aaj). */
    data class Time(val at: Instant) : TemplateArg

    /** Verbatim text. */
    data class Text(val value: String) : TemplateArg {
        override fun toString(): String = "Text(redacted)"
    }
}

/** A rendered line. [usedFallback] is true when the text or any term came from English. */
data class Rendered(val text: String, val usedFallback: Boolean) {
    override fun toString(): String = "Rendered(redacted)"
}

/** Parsed template pieces (docs/specs/M11.md section 3 "Template grammar"). */
sealed interface Token {
    data class Literal(val text: String) : Token
    data class TermRef(val kind: TermKind, val n: Int) : Token
    data class Named(val name: String) : Token
}

/** The template grammar, shared by the renderer and the content lint. */
object TemplateSyntax {
    private val NAME = Regex("[a-z][a-z0-9_]*")
    private val NUMBER = Regex("[1-9][0-9]{0,2}")

    /**
     * `{{` and `}}` are literal braces; `{kind:N}` a fixed term ref (N 1-based, in range); `{name}` a named arg.
     * A lone `}`, an unclosed `{`, a bad name or an out-of-range N is a failure.
     */
    fun parse(template: String): Result<List<Token>> = runCatching { Scanner(template).run() }

    /** One pass over a template; throws [IllegalArgumentException] on a grammar error (wrapped by [parse]). */
    private class Scanner(private val t: String) {
        private val out = ArrayList<Token>()
        private val lit = StringBuilder()
        private var i = 0

        fun run(): List<Token> {
            while (i < t.length) step()
            flush()
            return out
        }

        private fun step() {
            val c = t[i]
            val doubled = (c == '{' || c == '}') && t.getOrNull(i + 1) == c
            require(doubled || c != '}') { "unbalanced closing brace" }
            when {
                doubled -> { lit.append(c); i += 2 }
                c == '{' -> placeholder()
                else -> { lit.append(c); i++ }
            }
        }

        private fun placeholder() {
            val end = t.indexOf('}', i + 1)
            require(end >= 0) { "unclosed brace" }
            val token = requireNotNull(token(t.substring(i + 1, end))) { "bad placeholder" }
            flush()
            out.add(token)
            i = end + 1
        }

        private fun flush() {
            if (lit.isNotEmpty()) { out.add(Token.Literal(lit.toString())); lit.clear() }
        }
    }

    internal fun token(body: String): Token? {
        val colon = body.indexOf(':')
        if (colon < 0) return Token.Named(body).takeIf { NAME.matches(body) }
        val kind = TermKind.ofKey(body.substring(0, colon))
        val num = body.substring(colon + 1).takeIf { NUMBER.matches(it) }?.toInt()
        return if (kind != null && num != null && num in 1..kind.count) Token.TermRef(kind, num) else null
    }

    /** The named args a template uses (failure when it does not parse). */
    fun namedArgs(template: String): Result<Set<String>> =
        parse(template).map { ts -> ts.filterIsInstance<Token.Named>().map { it.name }.toSet() }
}

/**
 * Renders content entries with named placeholders (docs/specs/M11.md section 3). Unknown arg, missing arg, bad term
 * index or unclosed brace give `Result.failure`; the UI then shows the English line. Gender-safe by content rule: the
 * text never inflects for the reader (subject is the day, the time or the planet); nothing here inflects either.
 */
class TemplateRenderer(private val r: TermResolver, private val time: (Instant) -> String) {
    fun render(id: String, args: Map<String, TemplateArg>, lang: String): Result<Rendered> = runCatching {
        val src = r.text(id, lang)
        val tokens = TemplateSyntax.parse(src.text).getOrThrow()
        val used = tokens.filterIsInstance<Token.Named>().map { it.name }.toSet()
        require(used.containsAll(args.keys)) { "unknown argument" }
        var fallback = src.isFallback
        val sb = StringBuilder()
        for (t in tokens) {
            when (t) {
                is Token.Literal -> sb.append(t.text)
                is Token.TermRef -> {
                    val term = r.term(t.kind, t.n, src.lang)
                    fallback = fallback || term.lang != lang
                    sb.append(term.text)
                }
                is Token.Named -> when (val a = args[t.name] ?: throw IllegalArgumentException("missing argument")) {
                    is TemplateArg.Term -> {
                        val term = r.term(a.kind, a.n, src.lang)
                        fallback = fallback || term.lang != lang
                        sb.append(term.text)
                    }
                    is TemplateArg.Time -> sb.append(time(a.at))
                    is TemplateArg.Text -> sb.append(a.value)
                }
            }
        }
        Rendered(sb.toString(), fallback)
    }
}
