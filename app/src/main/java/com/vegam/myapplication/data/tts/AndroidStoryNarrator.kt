package com.vegam.myapplication.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vegam.myapplication.domain.model.NarrationError
import com.vegam.myapplication.domain.model.NarrationState
import com.vegam.myapplication.domain.service.StoryNarrator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StoryNarrator] backed by the phone's built-in text-to-speech engine (usually
 * "Speech Services by Google"), speaking Telugu.
 *
 * The engine is created lazily on the first tap, not at app start.
 */
@Singleton
class AndroidStoryNarrator @Inject constructor(
    @ApplicationContext private val context: Context,
) : StoryNarrator {

    private val _state = MutableStateFlow<NarrationState>(NarrationState.Idle)
    override val state: StateFlow<NarrationState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pending: List<String>? = null

    // Callbacks arrive on a background thread; utterance ids carry a "generation" so
    // callbacks from text we already replaced or stopped are ignored.
    @Volatile private var generation = 0
    @Volatile private var segmentCount = 0

    override fun speak(segments: List<String>) {
        generation++
        val lines = segments.map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            _state.value = NarrationState.Finished
            return
        }
        val engine = tts
        when {
            engine == null -> {
                pending = lines
                _state.value = NarrationState.Preparing
                tts = TextToSpeech(context.applicationContext) { status -> onEngineInit(status) }
            }
            !isReady -> pending = lines // still starting; speak when ready
            else -> enqueue(engine, lines)
        }
    }

    override fun stop() {
        generation++
        pending = null
        tts?.stop()
        _state.value = NarrationState.Idle
    }

    private fun onEngineInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            fail(NarrationError.ENGINE_UNAVAILABLE)
            return
        }
        val result = engine.setLanguage(TELUGU)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Keep nothing: next tap retries, in case the user has installed the voice meanwhile.
            fail(NarrationError.LANGUAGE_NOT_INSTALLED)
            return
        }
        engine.setSpeechRate(SPEECH_RATE)
        engine.setOnUtteranceProgressListener(progressListener)
        isReady = true
        pending?.let { lines ->
            pending = null
            enqueue(engine, lines)
        }
    }

    private fun enqueue(engine: TextToSpeech, lines: List<String>) {
        val gen = generation
        segmentCount = lines.size
        lines.forEachIndexed { index, text ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, "$gen:$index")
        }
        _state.value = NarrationState.Speaking(0)
    }

    private fun fail(reason: NarrationError) {
        tts?.shutdown()
        tts = null
        isReady = false
        pending = null
        _state.value = NarrationState.Error(reason)
    }

    /** "gen:index" → index, or null if it belongs to an older request. */
    private fun currentIndexOf(utteranceId: String?): Int? {
        val parts = utteranceId?.split(':') ?: return null
        if (parts.size != 2 || parts[0].toIntOrNull() != generation) return null
        return parts[1].toIntOrNull()
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            currentIndexOf(utteranceId)?.let { _state.value = NarrationState.Speaking(it) }
        }

        override fun onDone(utteranceId: String?) {
            val index = currentIndexOf(utteranceId) ?: return
            if (index == segmentCount - 1) _state.value = NarrationState.Finished
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (currentIndexOf(utteranceId) != null) _state.value = NarrationState.Idle
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (currentIndexOf(utteranceId) != null) _state.value = NarrationState.Idle
        }
    }

    private companion object {
        val TELUGU: Locale = Locale.forLanguageTag("te-IN")

        /** A little slower than normal — easier for children to follow. */
        const val SPEECH_RATE = 0.9f
    }
}
