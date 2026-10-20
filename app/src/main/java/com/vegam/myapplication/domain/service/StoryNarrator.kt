package com.vegam.myapplication.domain.service

import com.vegam.myapplication.domain.model.NarrationState
import kotlinx.coroutines.flow.StateFlow

/**
 * Reads text aloud. The domain only knows "speak these lines"; the Android
 * TextToSpeech details live in the data layer (AndroidStoryNarrator), so it could be
 * swapped for recorded audio from the server later without touching the reader.
 */
interface StoryNarrator {
    val state: StateFlow<NarrationState>

    /** Replaces anything currently being read with [segments], read in order. */
    fun speak(segments: List<String>)

    fun stop()
}
