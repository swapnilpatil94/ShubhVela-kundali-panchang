package app.parivaar.panchang.content.text

import app.parivaar.panchang.content.pack.ContentPack

/* [TermKind.key] is the payload JSON key and the template tag (`{rashi:N}`). */
private const val RASHIS = 12
private const val GRAHAS = 9
private const val NAKSHATRAS = 27
private const val TARAS = 9

/** Term arrays of a pack, with their exact sizes. */
enum class TermKind(val count: Int) {
    RASHI(RASHIS), GRAHA(GRAHAS), NAK(NAKSHATRAS), TARA(TARAS);

    val key: String get() = name.lowercase()

    companion object {
        fun ofKey(key: String): TermKind? = entries.firstOrNull { it.key == key }
    }
}

/** A resolved text. [isFallback] is true when it came from English instead of the requested [lang]. */
data class Resolved(val text: String, val lang: String, val isFallback: Boolean)

/**
 * Looks up content text and terms with the fallback chain `[lang, "en"]` only (docs/specs/M11.md section 3). A miss in
 * both throws [IllegalStateException]; lint L2/L8 make that unreachable for the bundled packs. Messages never carry
 * content or ids of personal readings (ids are public content keys).
 */
class TermResolver(private val packs: Map<String, ContentPack>) {
    private fun chain(lang: String): List<String> = if (lang == FALLBACK) listOf(FALLBACK) else listOf(lang, FALLBACK)

    /** The 1-based [n]th term of [kind] in [lang], else English. */
    fun term(kind: TermKind, n: Int, lang: String): Resolved {
        require(n in 1..kind.count) { "term index out of range" }
        for (l in chain(lang)) {
            val t = packs[l]?.terms?.of(kind, n)
            if (t != null) return Resolved(t, l, l != lang)
        }
        error("term missing in every pack of the chain")
    }

    /** The entry [id] in [lang], else English. */
    fun text(id: String, lang: String): Resolved {
        for (l in chain(lang)) {
            val t = packs[l]?.entries?.get(id)
            if (t != null) return Resolved(t, l, l != lang)
        }
        error("content id missing in every pack of the chain")
    }

    /** True when [id] resolves in [lang] or English. */
    fun has(id: String, lang: String): Boolean = chain(lang).any { packs[it]?.entries?.containsKey(id) == true }

    /** The pack that serves [id] for [lang] (for variant counts: the user-language pack, en when falling back). */
    fun packFor(prefixV1: String, lang: String): ContentPack? =
        chain(lang).firstNotNullOfOrNull { l -> packs[l]?.takeIf { it.entries.containsKey(prefixV1) } }

    companion object {
        const val FALLBACK = "en"
    }
}
