package com.vegamstories.app.domain.model

/**
 * Domain models. Pure Kotlin — no Android, Room or network annotations here,
 * so the rest of the app never depends on how data is stored or fetched.
 */

data class Category(
    val id: String,
    val name: String,
    val sortOrder: Int,
)

/** A story in one language version ([language]): the title, cover, pages, text and video are that version's. */
data class Story(
    val id: String,
    /** Language code of this version, e.g. "te". */
    val language: String = "te",
    val categoryId: String,
    val categoryName: String,
    val episodeNumber: Int,
    val title: String,
    val summary: String,
    /** The cover, or else the first comic page; null = no picture, a title card is shown (StoryCover). */
    val coverImageUrl: String?,
    val pageCount: Int,
    val publishedAt: Long,
    val video: StoryVideo?,
    /** This version can also be read as text. */
    val hasText: Boolean = false,
    /** The heart: [isFavorite] is this device's like, [likeCount] is everyone's. */
    val isFavorite: Boolean,
    val likeCount: Int,
    val isDownloaded: Boolean,
    val progress: ReadingProgress?,
) {
    val isRead: Boolean get() = progress?.isCompleted == true
    val isInProgress: Boolean get() = progress != null && !progress.isCompleted

    /** The formats this story comes in, always in the order comic, text, video. */
    val formats: Set<StoryFormat>
        get() = buildSet {
            if (pageCount > 0) add(StoryFormat.COMIC)
            if (hasText) add(StoryFormat.TEXT)
            if (video != null) add(StoryFormat.VIDEO)
        }
}

/** A story ready to read, in one language version, plus the other languages it's available in. */
data class StoryDetail(
    val story: Story,
    /** Comic pages of this version. */
    val pages: List<StoryPage>,
    /** This version as text; null if it has no text. */
    val text: StoryText? = null,
    /** Every language the story is in (this one included), in the server's order. */
    val languages: List<Language> = emptyList(),
)

/** One finished comic page: a single image that already contains the panels, captions and speech bubbles. */
data class StoryPage(
    val index: Int,
    val imageUrl: String,
)

data class StoryVideo(
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val videoUrl: String,
)

data class ReadingProgress(
    val storyId: String,
    val lastPageIndex: Int,
    val totalPages: Int,
    val isCompleted: Boolean,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = when {
            isCompleted -> 1f
            totalPages <= 0 -> 0f
            else -> ((lastPageIndex + 1).toFloat() / totalPages).coerceIn(0f, 1f)
        }
}
