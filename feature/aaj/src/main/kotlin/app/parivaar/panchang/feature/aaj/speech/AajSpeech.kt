@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.speech

/** The sentences of the read-aloud script, already formatted in the user's language and time style. */
data class SpeechParts(
    val date: String,
    val verdict: String,
    val next: String?,
    val windows: List<String>,
    val footer: String,
)

object AajSpeech {
    /** Tier 1 then tier 2, then the footer. Blank parts are dropped. */
    fun script(p: SpeechParts): List<String> =
        (listOf(p.date, p.verdict, p.next) + p.windows + p.footer).filterNotNull().filter { it.isNotBlank() }
}

/** A voice as the picker sees it (mapped from the platform `Voice`). */
data class VoiceInfo(val id: String, val languageTag: String, val needsNetwork: Boolean)

object VoicePicker {
    /** `TextToSpeech.LANG_AVAILABLE` is 0; missing-data and not-supported are negative. */
    fun languageUsable(isLanguageAvailableResult: Int): Boolean = isLanguageAvailableResult >= 0

    private fun lang(tag: String) = tag.substringBefore('-').substringBefore('_').lowercase()

    /** An offline voice for the language, preferring an exact tag match; null means "voice not available". */
    fun pick(voices: List<VoiceInfo>, languageTag: String): VoiceInfo? =
        voices.filter { !it.needsNetwork && lang(it.languageTag) == lang(languageTag) }
            .minWithOrNull(compareBy<VoiceInfo> { !it.languageTag.equals(languageTag, ignoreCase = true) }.thenBy { it.id })
}

/** What the read-aloud control shows. */
enum class SpeechUi { Checking, Unavailable, Ready, Speaking }
