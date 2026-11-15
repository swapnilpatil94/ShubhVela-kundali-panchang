@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.speech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-aloud over the platform [TextToSpeech], offline voices only (decision D6): the language must be available
 * and the chosen voice must not need the network. When no such voice exists the state is
 * [SpeechUi.Unavailable] and nothing is ever spoken. Stops on screen off; call [stop] when the app leaves the
 * screen and [shutdown] when done. Text never leaves the device and is not logged.
 */
class AajSpeaker(private val context: Context) {
    private val _state = MutableStateFlow(SpeechUi.Checking)
    val state: StateFlow<SpeechUi> = _state

    private var tts: TextToSpeech? = null
    private var ready = false
    private var screenOff: BroadcastReceiver? = null

    /** Starts the engine once. Safe to call repeatedly. */
    fun ensureReady(locale: Locale = Locale.getDefault()) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status, locale) }
    }

    private fun onInit(status: Int, locale: Locale) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            _state.value = SpeechUi.Unavailable
            return
        }
        val usable = VoicePicker.languageUsable(engine.isLanguageAvailable(locale))
        val voices = engine.voices.orEmpty().map { VoiceInfo(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired) }
        val pick = if (usable) VoicePicker.pick(voices, locale.toLanguageTag()) else null
        val voice = pick?.let { p -> engine.voices.orEmpty().firstOrNull { it.name == p.id } }
        if (voice == null) {
            _state.value = SpeechUi.Unavailable
            return
        }
        engine.voice = voice
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == LAST) finish() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish()
        })
        ready = true
        _state.value = SpeechUi.Ready
    }

    fun speak(lines: List<String>) {
        val engine = tts
        if (!ready || engine == null || lines.isEmpty()) return
        listenForScreenOff()
        _state.value = SpeechUi.Speaking
        lines.forEachIndexed { i, line ->
            val id = if (i == lines.lastIndex) LAST else "l$i"
            engine.speak(line, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    fun stop() {
        if (_state.value == SpeechUi.Speaking) tts?.stop()
        finish()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
        _state.value = SpeechUi.Checking
    }

    private fun finish() {
        screenOff?.let { runCatching { context.unregisterReceiver(it) } }
        screenOff = null
        if (ready) _state.value = SpeechUi.Ready
    }

    private fun listenForScreenOff() {
        if (screenOff != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = stop()
        }
        screenOff = r
        context.registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private companion object {
        const val LAST = "last"
    }
}
