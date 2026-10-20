package com.vegam.myapplication.domain.model

/**
 * The 11-character YouTube video id in [StoryVideo.videoUrl], or null when the link isn't a
 * single video (a channel, playlist or search link). Null → the app falls back to opening the link.
 */
val StoryVideo.youTubeVideoId: String?
    get() = YouTubeLinks.videoIdOf(videoUrl)

object YouTubeLinks {
    private val bareId = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val patterns = listOf(
        // youtube.com/watch?v=ID (also m. / music. and extra params), /embed/ID, /shorts/ID, /live/ID, /v/ID
        Regex("""youtube(?:-nocookie)?\.com/(?:watch\?(?:[^#]*&)?v=|embed/|shorts/|live/|v/)([A-Za-z0-9_-]{11})"""),
        // youtu.be/ID
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
    )

    fun videoIdOf(url: String): String? {
        val value = url.trim()
        if (bareId.matches(value)) return value
        return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.get(1) }
    }
}
