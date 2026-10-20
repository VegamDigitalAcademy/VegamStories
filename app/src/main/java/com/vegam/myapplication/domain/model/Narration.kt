package com.vegam.myapplication.domain.model

/** What the read-aloud voice is doing right now. */
sealed interface NarrationState {
    data object Idle : NarrationState

    /** Voice engine is starting up (first use only, ~1 second). */
    data object Preparing : NarrationState

    /** Reading the segment at [segmentIndex] of the list passed to speak(). */
    data class Speaking(val segmentIndex: Int) : NarrationState

    /** Finished every segment that was queued. */
    data object Finished : NarrationState

    data class Error(val reason: NarrationError) : NarrationState
}

enum class NarrationError {
    /** The phone's voice engine has no Telugu voice downloaded. */
    LANGUAGE_NOT_INSTALLED,

    /** No text-to-speech engine on the phone, or it failed to start. */
    ENGINE_UNAVAILABLE,
}
