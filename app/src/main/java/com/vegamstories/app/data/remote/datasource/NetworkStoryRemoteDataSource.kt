package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.data.remote.api.CatalogueApi
import com.vegamstories.app.data.remote.api.StoryApi
import com.vegamstories.app.data.remote.dto.CategoryDto
import com.vegamstories.app.data.remote.dto.FeedResponseDto
import com.vegamstories.app.data.remote.dto.LanguageDto
import com.vegamstories.app.data.remote.dto.PageDto
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.data.remote.dto.SupabaseCategoryRow
import com.vegamstories.app.data.remote.dto.SupabaseStoryRow
import com.vegamstories.app.data.remote.dto.StoryTranslationDto
import com.vegamstories.app.data.remote.dto.VideoDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

private const val TAG = "NetworkStoryRemoteDataSource"

/**
 * The catalogue from R2, written by the admin site on every change: first the small pointer file,
 * then the catalogue itself, only when its version is new to the phone. If R2 can't be reached,
 * the stories come from Supabase instead, as before R2. Like counts always come from Supabase.
 */
class NetworkStoryRemoteDataSource @Inject constructor(
    private val catalogueApi: CatalogueApi,
    private val api: StoryApi,
) : StoryRemoteDataSource {

    private val log = AppLogger(TAG)

    override suspend fun fetchCatalogue(savedVersion: String?): CatalogueUpdate = try {
        val pointer = catalogueApi.getPointer()
        if (pointer.version == savedVersion) {
            log.d("$TAG: Catalogue unchanged (version $savedVersion): nothing to download")
            CatalogueUpdate.Unchanged
        } else {
            log.d("$TAG: Catalogue version ${pointer.version} is new (the phone has ${savedVersion ?: "none"}): downloading it")
            val catalogue = catalogueApi.getCatalogue(pointer.url)
            CatalogueUpdate.Changed(feed(catalogue.categories, catalogue.stories, catalogue.languages), pointer.version)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w("$TAG: Catalogue not loaded from R2; loading the stories from Supabase instead", e)
        CatalogueUpdate.Changed(fetchFeedFromSupabase(), version = null)
    }

    override suspend fun fetchLikeCounts(): Map<String, Int> =
        api.getLikeCounts().associate { it.id to it.likeCount }

    /** The same rows as the catalogue, straight from Supabase: categories, stories and languages. */
    private suspend fun fetchFeedFromSupabase(): FeedResponseDto = coroutineScope {
        log.d("$TAG: Loading categories, stories and languages (in parallel)")
        // The requests run in parallel.
        val categories = async { api.getCategories() }
        val stories = async { api.getStories() }
        val languages = async { fetchLanguages() }
        feed(categories.await(), stories.await(), languages.await())
    }

    /** Supabase's rows, from R2 or from Supabase, as the app's feed. */
    private fun feed(categories: List<SupabaseCategoryRow>, stories: List<SupabaseStoryRow>, languages: List<LanguageDto>) =
        FeedResponseDto(
            categories = categories.map { row ->
                CategoryDto(id = row.id, names = row.names.associate { it.language to it.name }, sortOrder = row.sortOrder)
            },
            stories = stories.map { it.toStoryDto() },
            languages = languages,
        ).also { feed ->
            log.d(
                "$TAG: Feed received",
                data = mapOf(
                    "categories" to feed.categories.map { it.id },
                    "stories" to feed.stories.size,
                    "languages" to feed.languages.map { it.code },
                ),
            )
        }

    /** Only names and order hang on this, so stories still load if it fails (e.g. 005 not run yet). */
    private suspend fun fetchLanguages(): List<LanguageDto> = try {
        api.getLanguages()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w("$TAG: Languages not loaded; keeping the saved list", e)
        emptyList()
    }

    private fun SupabaseStoryRow.toStoryDto() = StoryDto(
        id = id,
        categoryId = categoryId,
        episode = episode,
        publishedAt = parseSupabaseTimestamp(publishedAt),
        likeCount = likeCount,
        translations = translations.map { t ->
            StoryTranslationDto(
                language = t.language,
                title = t.title,
                summary = t.summary,
                coverUrl = t.coverUrl,
                pages = pages.filter { it.language == t.language }.sortedBy { it.pageIndex }.map { PageDto(imageUrl = it.imageUrl) },
                text = t.body,
                video = if (t.videoUrl != null && t.videoThumbnailUrl != null) {
                    VideoDto(
                        title = t.videoTitle.orEmpty(),
                        description = t.videoDescription.orEmpty(),
                        thumbnailUrl = t.videoThumbnailUrl,
                        url = t.videoUrl,
                    )
                } else {
                    null
                },
            )
        },
    )
}

private val fractionalSeconds = Regex("""\.\d+""")

/**
 * Postgres timestamptz → epoch millis, 0 if unparseable. Done by hand because java.time
 * needs Android 8 and this app supports Android 7 (minSdk 24).
 * Accepts "2026-10-20T18:22:07.123456+00:00", "2026-10-20 18:22:07+05:30", "...Z".
 */
internal fun parseSupabaseTimestamp(value: String): Long = runCatching {
    val normalized = value.trim()
        .replace(' ', 'T')
        .replaceFirst(fractionalSeconds, "")
        .let { if (it.endsWith("Z")) it.dropLast(1) + "+00:00" else it }
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(normalized)?.time
}.getOrNull() ?: 0L
