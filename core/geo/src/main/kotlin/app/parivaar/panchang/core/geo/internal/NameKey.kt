package app.parivaar.panchang.core.geo.internal

import java.text.Normalizer
import java.util.Locale

/**
 * Name normaliser (M05 spec 4.1). Must stay identical to tools/geo/namekey.py; parity is pinned by
 * golden/geo_namekey.json.
 */
internal object NameKey {
    private const val MIN_QUERY_CODE_POINTS = 2
    private const val COMBINING_FIRST = 0x0300
    private const val COMBINING_LAST = 0x036F
    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D

    fun of(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val stripped = StringBuilder(decomposed.length)
        var i = 0
        while (i < decomposed.length) {
            val cp = decomposed.codePointAt(i)
            i += Character.charCount(cp)
            if (cp in COMBINING_FIRST..COMBINING_LAST || cp == ZWNJ || cp == ZWJ) continue
            stripped.appendCodePoint(cp)
        }
        val lower = stripped.toString().lowercase(Locale.ROOT)
        val out = StringBuilder(lower.length)
        var pendingSpace = false
        i = 0
        while (i < lower.length) {
            val cp = lower.codePointAt(i)
            i += Character.charCount(cp)
            if (keep(cp)) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.appendCodePoint(cp)
            } else {
                pendingSpace = true
            }
        }
        return out.toString()
    }

    fun tokens(key: String): List<String> = if (key.isEmpty()) emptyList() else key.split(' ')

    /** True when the query key has fewer than 2 code points (spaces excluded). */
    fun tooShort(key: String): Boolean {
        val compact = key.replace(" ", "")
        return compact.codePointCount(0, compact.length) < MIN_QUERY_CODE_POINTS
    }

    private fun keep(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK,
        Character.DECIMAL_DIGIT_NUMBER,
        -> true
        else -> false
    }
}
