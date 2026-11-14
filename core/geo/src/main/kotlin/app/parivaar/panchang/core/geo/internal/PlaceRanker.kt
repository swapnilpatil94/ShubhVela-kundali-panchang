package app.parivaar.panchang.core.geo.internal

import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.PlaceHit

/** One searchable name of a place: the display text and its [NameKey]. */
internal class NameEntry(val display: String, val key: String) {
    val tokens: List<String> = NameKey.tokens(key)
}

/** A place with all of its names (primary first). */
internal class NamedPlace(val place: GeoPlace, val names: List<NameEntry>)

/** Ranking of M05 spec 4.2. Shared by the seed store and the SQLite store. */
internal object PlaceRanker {
    const val MAX_LIMIT = 50
    const val MIN_LIMIT = 1

    private class Best(val tier: Int, val entry: NameEntry)

    private class Scored(val c: NamedPlace, val best: Best)

    fun rank(query: String, candidates: Iterable<NamedPlace>, limit: Int): List<PlaceHit> {
        val q = NameKey.of(query)
        if (NameKey.tooShort(q)) return emptyList()
        val qTokens = NameKey.tokens(q)
        val hits = ArrayList<Scored>()
        for (c in candidates) {
            var best: Best? = null
            for (n in c.names) {
                val t = tierOf(q, qTokens, n) ?: continue
                val b = best
                if (b == null || better(t, n, b)) best = Best(t, n)
            }
            if (best != null) hits.add(Scored(c, best))
        }
        hits.sortWith(
            compareBy<Scored> { it.best.tier }
                .thenByDescending { it.c.place.population }
                .thenBy { it.best.entry.key }
                .thenBy { it.c.place.id },
        )
        return hits.take(limit.coerceIn(MIN_LIMIT, MAX_LIMIT))
            .map { PlaceHit(it.c.place, it.best.entry.display, it.best.tier) }
    }

    private fun better(tier: Int, n: NameEntry, b: Best): Boolean =
        tier < b.tier || (tier == b.tier && n.key < b.entry.key)

    private fun tierOf(q: String, qTokens: List<String>, n: NameEntry): Int? = when {
        n.key == q -> 0
        n.key.startsWith(q) -> 1
        tokensMatch(qTokens, 0, n.tokens, BooleanArray(n.tokens.size)) -> 2
        else -> null
    }

    /** Every query token is a prefix of a distinct key token (small backtracking match). */
    private fun tokensMatch(q: List<String>, i: Int, key: List<String>, used: BooleanArray): Boolean =
        i == q.size || key.indices.any { j ->
            val free = !used[j] && key[j].startsWith(q[i])
            if (free) used[j] = true
            val ok = free && tokensMatch(q, i + 1, key, used)
            if (free && !ok) used[j] = false
            ok
        }
}
