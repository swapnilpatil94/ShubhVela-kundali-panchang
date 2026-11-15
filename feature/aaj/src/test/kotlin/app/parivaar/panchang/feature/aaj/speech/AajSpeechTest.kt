@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AajSpeechTest {
    @Test fun `script order is date, verdict, next, windows, footer and blanks are dropped`() {
        val s = AajSpeech.script(SpeechParts("D", "V", "N", listOf("W1", " ", "W2"), "F"))
        assertEquals(listOf("D", "V", "N", "W1", "W2", "F"), s)
        assertEquals(listOf("D", "V", "F"), AajSpeech.script(SpeechParts("D", "V", null, emptyList(), "F")))
    }

    @Test fun `language availability follows the platform codes`() {
        assertTrue(VoicePicker.languageUsable(0)) // LANG_AVAILABLE
        assertTrue(VoicePicker.languageUsable(1)) // LANG_COUNTRY_AVAILABLE
        assertTrue(VoicePicker.languageUsable(2)) // LANG_COUNTRY_VAR_AVAILABLE
        assertFalse(VoicePicker.languageUsable(-1)) // LANG_MISSING_DATA
        assertFalse(VoicePicker.languageUsable(-2)) // LANG_NOT_SUPPORTED
    }

    @Test fun `network voices are never picked`() {
        val voices = listOf(VoiceInfo("net", "hi-IN", true), VoiceInfo("local", "hi-IN", false))
        assertEquals("local", VoicePicker.pick(voices, "hi-IN")?.id)
        assertNull(VoicePicker.pick(listOf(VoiceInfo("net", "hi-IN", true)), "hi-IN"))
    }

    @Test fun `exact tag wins, same language is accepted, other languages are not`() {
        val voices = listOf(VoiceInfo("a", "en-GB", false), VoiceInfo("b", "en-IN", false), VoiceInfo("c", "mr-IN", false))
        assertEquals("b", VoicePicker.pick(voices, "en-IN")?.id)
        assertEquals("a", VoicePicker.pick(voices, "en-US")?.id)
        assertNull(VoicePicker.pick(voices, "ta-IN"))
    }
}
