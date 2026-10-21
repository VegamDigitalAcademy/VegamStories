package com.vegam.myapplication.presentation.preview

import com.vegam.myapplication.domain.model.Category
import com.vegam.myapplication.domain.model.ReadingProgress
import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.model.StoryDetail
import com.vegam.myapplication.domain.model.StoryPage
import com.vegam.myapplication.domain.model.StoryVideo

/**
 * Sample data for @Preview only. Images don't load in previews (no network),
 * so cards show their placeholder background there.
 */
object PreviewData {

    val categories = listOf(
        Category("moral", "నీతి కథలు", 0),
        Category("tenali", "తెనాలి రామకృష్ణ కథలు", 1),
        Category("panchatantra", "పంచతంత్ర కథలు", 2),
        Category("folk", "జానపద కథలు", 3),
    )

    private val titles = listOf(
        "నిజాయితీ బహుమతి", "తెనాలి రాముడి తెలివి", "సింహం మరియు కుందేలు",
        "అద్భుత దీపం", "కష్టం విలువ", "కోతి మొసలి",
    )

    val video = StoryVideo(
        title = "నిజాయితీ బహుమతి: యానిమేషన్ వీడియో",
        description = "ఈ కథను యానిమేషన్ వీడియోగా చూడండి. మరిన్ని కథల కోసం మా YouTube ఛానెల్‌ని సబ్‌స్క్రైబ్ చేయండి.",
        thumbnailUrl = "https://picsum.photos/seed/preview-video/960/540",
        videoUrl = "https://www.youtube.com/watch?v=M7lc1UVf-VE",
    )

    fun story(
        index: Int = 0,
        isFavorite: Boolean = false,
        isDownloaded: Boolean = false,
        progress: ReadingProgress? = null,
    ): Story {
        val category = categories[index % categories.size]
        val id = "${category.id}-${index + 1}"
        return Story(
            id = id,
            categoryId = category.id,
            categoryName = category.name,
            episodeNumber = index + 1,
            title = titles[index % titles.size],
            summary = "పిల్లలు, పెద్దలు అందరూ చదవదగిన చక్కని కథ.",
            coverImageUrl = "https://picsum.photos/seed/preview-$id/480/640",
            pageCount = 3,
            publishedAt = 0L,
            video = if (index % 2 == 0) video else null,
            isFavorite = isFavorite,
            likeCount = listOf(0, 7, 128, 1_540)[index % 4],
            isDownloaded = isDownloaded,
            progress = progress,
        )
    }

    fun progress(storyIndex: Int, lastPage: Int, total: Int = 3, completed: Boolean = false) =
        ReadingProgress(
            storyId = story(storyIndex).id,
            lastPageIndex = lastPage,
            totalPages = total,
            isCompleted = completed,
            updatedAt = 0L,
        )

    /** A varied list: read, in progress, favorite, downloaded, untouched. */
    val stories: List<Story> = listOf(
        story(0, isFavorite = true, progress = progress(0, 2, completed = true)),
        story(1, progress = progress(1, 0)),
        story(2, isDownloaded = true),
        story(3, isFavorite = true),
        story(4),
        story(5, isDownloaded = true, progress = progress(5, 1)),
    )

    val continueReading: Story = stories[1]

    val historyStories: List<Story> = stories.filter { it.progress != null }

    /** Finished page images (panels and speech bubbles are part of each image). */
    val pages: List<StoryPage> = List(3) { pageIndex ->
        StoryPage(index = pageIndex, imageUrl = "https://picsum.photos/seed/preview-page$pageIndex/900/1200")
    }

    val storyDetail = StoryDetail(story = story(0, isFavorite = true), pages = pages)
}
