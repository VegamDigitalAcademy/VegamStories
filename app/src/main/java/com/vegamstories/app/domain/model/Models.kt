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

data class Story(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val episodeNumber: Int,
    val title: String,
    val summary: String,
    val coverImageUrl: String,
    val pageCount: Int,
    val publishedAt: Long,
    val video: StoryVideo?,
    /** It can also be read as text (in at least one language). */
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

data class StoryDetail(
    val story: Story,
    val pages: List<StoryPage>,
    /** The story as text, one per language it's available in. Empty if it has no text version. */
    val texts: List<StoryText> = emptyList(),
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
