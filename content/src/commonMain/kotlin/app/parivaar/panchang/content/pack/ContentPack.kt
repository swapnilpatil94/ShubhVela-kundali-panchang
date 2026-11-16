package app.parivaar.panchang.content.pack

import app.parivaar.panchang.content.text.TermKind

/**
 * Display terms of one language: arrays of exactly [TermKind.count] names each, or absent (placeholder packs such as
 * mr and gu in M11, which fall back to English).
 */
data class Terms(val byKind: Map<TermKind, List<String>> = emptyMap()) {
    init {
        for ((k, v) in byKind) require(v.size == k.count) { "term array of wrong size" }
    }

    /** The 1-based [n]th term of [kind], or null when this language has no such array. */
    fun of(kind: TermKind, n: Int): String? = byKind[kind]?.getOrNull(n - 1)

    /** True when every [TermKind] array is present. */
    val complete: Boolean get() = TermKind.entries.all { it in byKind }

    companion object {
        val EMPTY = Terms()
    }
}

/** One verified language pack (interpretation text, not personal data). */
data class ContentPack(val lang: String, val version: Int, val entries: Map<String, String>, val terms: Terms) {
    /** The number of contiguous variants `<prefix>.v1 .. <prefix>.vN` (0 when v1 is absent). */
    fun variantCount(prefix: String): Int {
        var n = 0
        while (entries.containsKey("$prefix.v${n + 1}")) n++
        return n
    }

    override fun toString(): String = "ContentPack($lang, v$version, ${entries.size} entries)"
}
