package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.data.remote.dto.CategoryDto
import com.vegamstories.app.data.remote.dto.FeedResponseDto
import com.vegamstories.app.data.remote.dto.LanguageDto
import com.vegamstories.app.data.remote.dto.PageDto
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.data.remote.dto.StoryTranslationDto
import com.vegamstories.app.data.remote.dto.VideoDto
import com.vegamstories.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Pretends to be the server: returns the same JSON shape ([FeedResponseDto]) the real
 * API will return, filled with sample stories in Telugu, English and Odia and placeholder images.
 */
class FakeStoryRemoteDataSource @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val likes: FakeLikeRemoteDataSource,
) : StoryRemoteDataSource {

    override suspend fun fetchFeed(): FeedResponseDto = withContext(ioDispatcher) {
        delay(NETWORK_DELAY_MS) // simulate network latency so loading states are visible
        FeedResponseDto(categories = categories, stories = buildStories(), languages = languages)
    }

    /**
     * Every story has a Telugu version. Episodes 1–6 also have English, episodes 1–3 also Odia,
     * so switching the app's story language changes what Home shows. Within a version, odd
     * episodes have a video and episodes 1, 2, 4, 5, 7, 8 have the story as text.
     */
    private suspend fun buildStories(): List<StoryDto> {
        val stories = mutableListOf<StoryDto>()
        var globalIndex = 0
        categories.forEach { category ->
            val titles = titlesByCategory.getValue(category.id)
            titles.forEachIndexed { i, teluguTitle ->
                val episode = i + 1
                val id = "${category.id}-$episode"
                val translations = buildList {
                    add(translation(id, "te", teluguTitle, category.names.getValue("te"), episode, globalIndex))
                    if (episode <= 6) {
                        add(translation(id, "en", "${category.names.getValue("en")} $episode", category.names.getValue("en"), episode, globalIndex))
                    }
                    if (episode <= 3) {
                        add(translation(id, "or", "${category.names.getValue("or")} $episode", category.names.getValue("or"), episode, globalIndex))
                    }
                }
                stories += StoryDto(
                    id = id,
                    categoryId = category.id,
                    episode = episode,
                    // Interleave categories so "Latest" mixes them, like the real feed.
                    publishedAt = BASE_TIME - (episode * categories.size + category.sortOrder) * HOUR_MS * 6,
                    likeCount = likes.countFor(id),
                    translations = translations,
                )
                globalIndex++
            }
        }
        return stories
    }

    private fun translation(id: String, language: String, title: String, categoryName: String, episode: Int, seed: Int) =
        StoryTranslationDto(
            language = language,
            title = title,
            summary = "$title – $categoryName.",
            // Episodes 7 and 8 have no cover: 7 shows its first comic page, 8 (text only) a title card.
            coverUrl = if (episode >= 7) null else image("$id-$language-cover", 480, 640),
            pages = if (episode == 8) emptyList() else buildPages("$id-$language", seed),
            text = if (episode % 3 != 0) sampleText(language, title) else null,
            video = if (episode % 2 == 1) buildVideo("$id-$language", title) else null,
        )

    private fun sampleText(language: String, title: String): String = when (language) {
        "en" ->
            "Once upon a time, in a small village, the story of \"$title\" began. Everyone there lived together happily.\n\n" +
                "One day a small problem came up. The children and the elders thought about it together and found a way out.\n\n" +
                "Moral: Together, any difficulty becomes easy."
        "or" ->
            "ଏକଦା ଏକ ଛୋଟ ଗାଁରେ \"$title\" କାହାଣୀ ଆରମ୍ଭ ହେଲା। ସେଠାରେ ସମସ୍ତେ ମିଳିମିଶି ରହୁଥିଲେ।\n\n" +
                "ଦିନେ ଏକ ଛୋଟ ସମସ୍ୟା ଆସିଲା। ପିଲା ଓ ବଡ଼ମାନେ ମିଶି ଭାବିଲେ ଏବଂ ସମାଧାନ ପାଇଲେ।\n\n" +
                "ନୀତି: ମିଳିମିଶି ରହିଲେ ସବୁ କଷ୍ଟ ସହଜ ହୁଏ।"
        else ->
            "ఒకప్పుడు ఒక చిన్న ఊరిలో \"$title\" కథ మొదలైంది. ఆ ఊరిలో అందరూ కలిసిమెలిసి ఉండేవారు.\n\n" +
                "ఒక రోజు ఒక చిన్న సమస్య వచ్చింది. పిల్లలు, పెద్దలు కలిసి ఆలోచించి దానికి పరిష్కారం కనుగొన్నారు.\n\n" +
                "నీతి: కలిసి ఉంటే ఏ కష్టమైనా సులభమే."
    }

    /** 3–4 finished page images per story (portrait, like a real comic page). */
    private fun buildPages(storyId: String, seed: Int): List<PageDto> {
        val pageCount = 3 + seed % 2
        return List(pageCount) { pageIndex -> PageDto(imageUrl = image("$storyId-page$pageIndex", 900, 1200)) }
    }

    private fun buildVideo(storyId: String, title: String) = VideoDto(
        title = "$title ▶",
        description = "",
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

        // Sample data only: in the real app every name comes from the server (admin site).
        val categories = listOf(
            CategoryDto(id = "moral", names = mapOf("te" to "నీతి కథలు", "en" to "Moral Stories", "or" to "ନୀତି କଥା"), sortOrder = 0),
            CategoryDto(id = "tenali", names = mapOf("te" to "తెనాలి రామకృష్ణ కథలు", "en" to "Tenali Rama", "or" to "ତେନାଲି ରାମ"), sortOrder = 1),
            CategoryDto(id = "panchatantra", names = mapOf("te" to "పంచతంత్ర కథలు", "en" to "Panchatantra", "or" to "ପଞ୍ଚତନ୍ତ୍ର"), sortOrder = 2),
            CategoryDto(id = "folk", names = mapOf("te" to "జానపద కథలు", "en" to "Folk Tales", "or" to "ଲୋକ କଥା"), sortOrder = 3),
        )

        // Like the server's languages table (supabase/migrations/005_languages.sql) plus Odia.
        val languages = listOf(
            LanguageDto(code = "te", name = "Telugu", nativeName = "తెలుగు", sortOrder = 0),
            LanguageDto(code = "en", name = "English", nativeName = "English", sortOrder = 1),
            LanguageDto(code = "or", name = "Odia", nativeName = "ଓଡ଼ିଆ", sortOrder = 2),
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
