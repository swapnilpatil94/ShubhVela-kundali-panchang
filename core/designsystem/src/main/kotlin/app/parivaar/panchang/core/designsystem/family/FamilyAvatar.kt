package app.parivaar.panchang.core.designsystem.family

/** One avatar: [initials] and [label] are shown on screen only; [tone] picks a gem colour (stable per profile). */
data class FamilyAvatar(val key: String, val initials: String, val label: String, val tone: Int) {
    override fun toString(): String = "FamilyAvatar(redacted)"
}

/** Initials of [name]: first letters of the first two words (grapheme-safe enough for display), upper-cased. */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { w -> String(Character.toChars(w.codePointAt(0))) }.uppercase()
