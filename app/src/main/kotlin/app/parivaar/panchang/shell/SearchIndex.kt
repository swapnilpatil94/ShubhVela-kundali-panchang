package app.parivaar.panchang.shell

import java.text.Normalizer
import java.util.Locale

data class SearchEntry(val dest: Dest, val labels: List<String>)

/** In-memory palette matcher: rank 0 label prefix, 1 word prefix, 2 substring; ties keep entry order. */
class SearchIndex(private val entries: List<SearchEntry>) {
    private val normalized: List<List<String>> = entries.map { e -> e.labels.map(::norm) }

    fun query(q: String, limit: Int = 8): List<SearchEntry> {
        val n = norm(q)
        if (n.isEmpty() || limit <= 0) return emptyList()
        return entries.indices
            .mapNotNull { i -> normalized[i].mapNotNull { rank(it, n) }.minOrNull()?.let { i to it } }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(limit)
            .map { entries[it.first] }
    }

    private fun rank(label: String, q: String): Int? =
        when {
            label.startsWith(q) -> 0
            label.split(SEPARATORS).any { it.startsWith(q) } -> 1
            label.contains(q) -> 2
            else -> null
        }

    private companion object {
        val SEPARATORS = Regex("[\\s\\-/,.]+")

        fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC).trim().lowercase(Locale.ROOT)
    }
}
