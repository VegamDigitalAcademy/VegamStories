package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.data.remote.dto.CategoryDto
import com.vegamstories.app.data.remote.dto.FeedResponseDto
import com.vegamstories.app.data.remote.dto.PageDto
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.data.remote.dto.TextDto
import com.vegamstories.app.data.remote.dto.VideoDto
import com.vegamstories.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Pretends to be the server: returns the same JSON shape ([FeedResponseDto]) the real
 * API will return, filled with dummy Telugu text and placeholder images.
 */
class FakeStoryRemoteDataSource @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val likes: FakeLikeRemoteDataSource,
) : StoryRemoteDataSource {

    override suspend fun fetchFeed(): FeedResponseDto = withContext(ioDispatcher) {
        delay(NETWORK_DELAY_MS) // simulate network latency so loading states are visible
        FeedResponseDto(categories = categories, stories = buildStories())
    }

    private suspend fun buildStories(): List<StoryDto> {
        val stories = mutableListOf<StoryDto>()
        var globalIndex = 0
        categories.forEach { category ->
            val titles = titlesByCategory.getValue(category.id)
            titles.forEachIndexed { i, title ->
                val episode = i + 1
                val id = "${category.id}-$episode"
                stories += StoryDto(
                    id = id,
                    categoryId = category.id,
                    episode = episode,
                    title = title,
                    summary = "$title – ${category.name}. పిల్లలు, పెద్దలు అందరూ చదవదగిన చక్కని కథ.",
                    coverUrl = image("$id-cover", 480, 640),
                    // Interleave categories so "Latest" mixes them, like the real feed.
                    publishedAt = BASE_TIME - (episode * categories.size + category.sortOrder) * HOUR_MS * 6,
                    pages = buildPages(id, seed = globalIndex),
                    video = if (episode % 2 == 1) buildVideo(id, title) else null,
                    likeCount = likes.countFor(id),
                    texts = buildTexts(title, episode),
                )
                globalIndex++
            }
        }
        return stories
    }

    /**
     * The story as text, to try every format combination: episodes 1, 4, 7… in Telugu and English,
     * 2, 5, 8… in Telugu only, 3, 6, 9… without text.
     */
    private fun buildTexts(title: String, episode: Int): List<TextDto> = when (episode % 3) {
        1 -> listOf(teluguText(title), englishText(title))
        2 -> listOf(teluguText(title))
        else -> emptyList()
    }

    private fun teluguText(title: String) = TextDto(
        language = "te",
        body = "ఒకప్పుడు ఒక చిన్న ఊరిలో \"$title\" కథ మొదలైంది. ఆ ఊరిలో అందరూ కలిసిమెలిసి ఉండేవారు.\n\n" +
            "ఒక రోజు ఒక చిన్న సమస్య వచ్చింది. పిల్లలు, పెద్దలు కలిసి ఆలోచించి దానికి పరిష్కారం కనుగొన్నారు.\n\n" +
            "నీతి: కలిసి ఉంటే ఏ కష్టమైనా సులభమే.",
    )

    private fun englishText(title: String) = TextDto(
        language = "en",
        body = "Once upon a time, in a small village, the story of \"$title\" began. Everyone there lived together happily.\n\n" +
            "One day a small problem came up. The children and the elders thought about it together and found a way out.\n\n" +
            "Moral: Together, any difficulty becomes easy.",
    )

    /** 3–4 finished page images per story (portrait, like a real comic page). */
    private fun buildPages(storyId: String, seed: Int): List<PageDto> {
        val pageCount = 3 + seed % 2
        return List(pageCount) { pageIndex -> PageDto(imageUrl = image("$storyId-page$pageIndex", 900, 1200)) }
    }

    private fun buildVideo(storyId: String, title: String) = VideoDto(
        title = "$title: యానిమేషన్ వీడియో",
        description = "ఈ కథను యానిమేషన్ వీడియోగా చూడండి. మరిన్ని కథల కోసం మా YouTube ఛానెల్‌ని సబ్‌స్క్రైబ్ చేయండి.",
        thumbnailUrl = image("$storyId-video", 960, 540),
        // YouTube's own IFrame-API demo video: stable and embeddable, for testing the in-app player.
        url = "https://www.youtube.com/watch?v=M7lc1UVf-VE",
    )

    /** Placeholder image service; seeded so each story always gets the same pictures. */
    private fun image(seed: String, w: Int, h: Int) = "https://picsum.photos/seed/vegam-$seed/$w/$h"

    private companion object {
        const val NETWORK_DELAY_MS = 800L
        const val HOUR_MS = 60L * 60L * 1000L
        const val BASE_TIME = 1_760_000_000_000L

        val categories = listOf(
            CategoryDto(id = "moral", name = "నీతి కథలు", sortOrder = 0),
            CategoryDto(id = "tenali", name = "తెనాలి రామకృష్ణ కథలు", sortOrder = 1),
            CategoryDto(id = "panchatantra", name = "పంచతంత్ర కథలు", sortOrder = 2),
            CategoryDto(id = "folk", name = "జానపద కథలు", sortOrder = 3),
        )

        val titlesByCategory = mapOf(
            "moral" to listOf(
                "నిజాయితీ బహుమతి", "కష్టం విలువ", "స్నేహితుని సహాయం", "అత్యాశ ఫలితం",
                "దయగల రాజు", "సత్యమే గెలుపు", "ఓర్పు గొప్పతనం", "పెద్దల మాట",
            ),
            "tenali" to listOf(
                "తెనాలి రాముడి తెలివి", "రాజుగారి గుర్రం", "బంగారు మామిడిపండ్లు", "పిల్లి పాలు",
                "దొంగల పని పట్టిన రాముడు", "బావిలో నిధి", "గర్విష్ఠి పండితుడు", "రాముడి చిత్రం",
            ),
            "panchatantra" to listOf(
                "సింహం మరియు కుందేలు", "కోతి మొసలి", "నీలి నక్క", "తాబేలు హంసలు",
                "పావురాలు మరియు వల", "ఎలుక చేసిన సాయం", "తెలివైన కాకి", "మాట్లాడే గుహ",
            ),
            "folk" to listOf(
                "అద్భుత దీపం", "మాయా చెట్టు", "చందమామ కథ", "రాకుమారి సాహసం",
                "మాట్లాడే చిలుక", "బంగారు చేప", "ఏడు సముద్రాలు", "దేవలోక యాత్ర",
            ),
        )
    }
}
