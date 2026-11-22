package com.vegamstories.app.data

import com.vegamstories.app.data.remote.api.CatalogueApi
import com.vegamstories.app.data.remote.api.StoryApi
import com.vegamstories.app.data.remote.datasource.CatalogueUpdate
import com.vegamstories.app.data.remote.datasource.NetworkStoryRemoteDataSource
import com.vegamstories.app.data.remote.dto.CataloguePointerDto
import com.vegamstories.app.data.remote.dto.LanguageDto
import com.vegamstories.app.data.remote.dto.SetStoryLikeRequest
import com.vegamstories.app.data.remote.dto.StoryLikeCountRow
import com.vegamstories.app.data.remote.dto.SupabaseCatalogueDto
import com.vegamstories.app.data.remote.dto.SupabaseCategoryRow
import com.vegamstories.app.data.remote.dto.SupabaseStoryRow
import com.vegamstories.app.data.remote.dto.SupabaseTranslationRow
import com.vegamstories.app.di.NetworkModule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Checking the catalogue in R2: only a new version is downloaded; without R2, Supabase as before. */
class CatalogueCheckTest {

    private fun story(id: String, likeCount: Int? = null) = SupabaseStoryRow(
        id = id,
        categoryId = "moral",
        episode = 1,
        publishedAt = "2026-10-21T15:49:00+00:00",
        likeCount = likeCount,
        translations = listOf(SupabaseTranslationRow(language = "te", title = "కథ")),
    )

    private class FakeCatalogueApi(var pointer: CataloguePointerDto?, val catalogues: Map<String, SupabaseCatalogueDto>) : CatalogueApi {
        val downloaded = mutableListOf<String>()
        override suspend fun getPointer(url: String) = pointer ?: throw IOException("R2 can't be reached")
        override suspend fun getCatalogue(url: String): SupabaseCatalogueDto {
            downloaded += url
            return catalogues[url] ?: throw IOException("404")
        }
    }

    private inner class FakeStoryApi : StoryApi {
        var storyCalls = 0
        override suspend fun getCategories(select: String, order: String) = emptyList<SupabaseCategoryRow>()
        override suspend fun getLanguages(select: String, order: String) = emptyList<LanguageDto>()
        override suspend fun getStories(select: String, isPublished: String, order: String, pagesOrder: String): List<SupabaseStoryRow> {
            storyCalls++
            return listOf(story("moral-1", likeCount = 7))
        }
        override suspend fun getLikeCounts(select: String, isPublished: String) =
            listOf(StoryLikeCountRow("moral-1", 7), StoryLikeCountRow("moral-2", 0))
        override suspend fun setStoryLike(body: SetStoryLikeRequest) = 0
    }

    private val v2 = CataloguePointerDto(version = "v2", url = "https://media.example.com/catalogue/v1/v2.json")
    private val r2 = FakeCatalogueApi(
        pointer = v2,
        catalogues = mapOf(v2.url to SupabaseCatalogueDto(categories = emptyList(), stories = listOf(story("moral-2")))),
    )
    private val supabase = FakeStoryApi()
    private val source = NetworkStoryRemoteDataSource(r2, supabase)

    @Test
    fun `the version the phone has downloads nothing`() = runTest {
        assertEquals(CatalogueUpdate.Unchanged, source.fetchCatalogue(savedVersion = "v2"))
        assertTrue(r2.downloaded.isEmpty())
    }

    @Test
    fun `a new version downloads that catalogue`() = runTest {
        val update = source.fetchCatalogue(savedVersion = "v1") as CatalogueUpdate.Changed

        assertEquals("v2", update.version)
        assertEquals(listOf("moral-2"), update.feed.stories.map { it.id })
        assertEquals(listOf(v2.url), r2.downloaded)
        assertEquals(0, supabase.storyCalls)
    }

    @Test
    fun `without R2 the stories come from Supabase, and the next check downloads again`() = runTest {
        r2.pointer = null

        val update = source.fetchCatalogue(savedVersion = "v1") as CatalogueUpdate.Changed

        assertNull(update.version)
        assertEquals(listOf("moral-1"), update.feed.stories.map { it.id })
        assertEquals(7, update.feed.stories.single().likeCount)
    }

    @Test
    fun `a version gone from R2 falls back to Supabase too`() = runTest {
        r2.pointer = v2.copy(url = "https://media.example.com/catalogue/v1/gone.json")

        val update = source.fetchCatalogue(savedVersion = "v1") as CatalogueUpdate.Changed

        assertNull(update.version)
        assertEquals(1, supabase.storyCalls)
    }

    @Test
    fun `like counts by story`() = runTest {
        assertEquals(mapOf("moral-1" to 7, "moral-2" to 0), source.fetchLikeCounts())
    }

    @Test
    fun `reads the catalogue the admin site writes`() = runTest {
        // As vegam-admin's publishCatalogue writes it: Supabase's rows as sent (keys in Supabase's
        // order), no like counts, nulls where a version has no cover, text or video.
        val written = """
            {"categories":[{"id":"moral","sort_order":0,"category_translations":[{"name": "Moral Stories", "language": "en"}, {"name": "నీతి కథలు", "language": "te"}]}],
             "stories":[{"id":"moral-1","category_id":"moral","episode":1,"published_at":"2026-10-21T15:49:00+00:00",
               "story_translations":[
                 {"body": "Shivaji and the Spy\n\nIn", "title": "The Secret Eye of Swarajya", "summary": "Spy Stories", "language": "en", "cover_url": "https://media.vegamstories.com/moral-1/cover-c27h0o.webp", "video_url": null, "video_title": null, "video_description": null, "video_thumbnail_url": null},
                 {"body": null, "title": "కథ", "summary": "", "language": "te", "cover_url": null, "video_url": "https://youtu.be/x", "video_title": null, "video_description": null, "video_thumbnail_url": "https://media.vegamstories.com/moral-1/te/t.webp"}],
               "story_pages":[{"language":"te","page_index":0,"image_url":"p0"},{"language":"te","page_index":1,"image_url":"p1"}]}],
             "languages":[{"code":"te","name":"Telugu","native_name":"తెలుగు","sort_order":0},
              {"code":"en","name":"English","native_name":"English","sort_order":1}]}
        """.trimIndent()
        val fromAdminSite = NetworkModule.provideJson().decodeFromString<SupabaseCatalogueDto>(written)
        val source = NetworkStoryRemoteDataSource(FakeCatalogueApi(v2, mapOf(v2.url to fromAdminSite)), supabase)

        val feed = (source.fetchCatalogue(savedVersion = null) as CatalogueUpdate.Changed).feed

        val story = feed.stories.single()
        assertEquals(1_792_597_740_000L, story.publishedAt) // 2026-10-21 15:49 UTC
        assertNull(story.likeCount)
        val (english, telugu) = story.translations.sortedBy { it.language }
        assertEquals("Shivaji and the Spy\n\nIn", english.text)
        assertNull(english.video)
        assertEquals(listOf("p0", "p1"), telugu.pages.map { it.imageUrl })
        assertEquals("https://youtu.be/x", telugu.video?.url)
        assertEquals("నీతి కథలు", feed.categories.single().names["te"])
        assertEquals(listOf("te", "en"), feed.languages.map { it.code })
    }
}
