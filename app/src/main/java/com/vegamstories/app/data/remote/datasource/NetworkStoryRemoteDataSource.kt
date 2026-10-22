package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.data.remote.api.StoryApi
import com.vegamstories.app.data.remote.dto.FeedResponseDto
import com.vegamstories.app.data.remote.dto.LanguageDto
import com.vegamstories.app.data.remote.dto.PageDto
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.data.remote.dto.SupabaseStoryRow
import com.vegamstories.app.data.remote.dto.TextDto
import com.vegamstories.app.data.remote.dto.VideoDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/** Loads categories, stories and languages from Supabase and shapes them into the app's feed format. */
class NetworkStoryRemoteDataSource @Inject constructor(
    private val api: StoryApi,
) : StoryRemoteDataSource {

    override suspend fun fetchFeed(): FeedResponseDto = coroutineScope {
        // Both requests run in parallel.
        val categories = async { api.getCategories() }
        val stories = async { api.getStories() }
        val languages = async { fetchLanguages() }
        FeedResponseDto(
            categories = categories.await(),
            stories = stories.await().map { it.toStoryDto() },
            languages = languages.await(),
        )
    }

    /** Only names and order hang on this, so stories still load if it fails (e.g. 005 not run yet). */
    private suspend fun fetchLanguages(): List<LanguageDto> = try {
        api.getLanguages()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Languages not loaded; keeping the saved list")
        emptyList()
    }

    private fun SupabaseStoryRow.toStoryDto() = StoryDto(
        id = id,
        categoryId = categoryId,
        episode = episode,
        title = title,
        summary = summary,
        coverUrl = coverUrl,
        publishedAt = parseSupabaseTimestamp(publishedAt),
        pages = pages.sortedBy { it.pageIndex }.map { PageDto(imageUrl = it.imageUrl) },
        video = if (videoUrl != null && videoThumbnailUrl != null) {
            VideoDto(
                title = videoTitle.orEmpty(),
                description = videoDescription.orEmpty(),
                thumbnailUrl = videoThumbnailUrl,
                url = videoUrl,
            )
        } else {
            null
        },
        likeCount = likeCount,
        texts = texts.map { TextDto(language = it.language, body = it.body) },
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
