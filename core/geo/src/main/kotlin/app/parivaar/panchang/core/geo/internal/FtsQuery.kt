package app.parivaar.panchang.core.geo.internal

/** Builds the FTS4 MATCH expression for a user query (M05 spec 4.9). */
internal object FtsQuery {
    const val MAX_CANDIDATES = 500

    /**
     * Tokens of the [NameKey] joined by space, each suffixed with `*`. Null when the query is too short.
     * Tokens hold only letters, marks and digits, so nothing needs escaping.
     */
    fun match(query: String): String? {
        val key = NameKey.of(query)
        if (NameKey.tooShort(key)) return null
        return NameKey.tokens(key).joinToString(" ") { "$it*" }
    }
}
