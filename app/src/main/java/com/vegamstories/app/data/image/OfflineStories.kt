package com.vegamstories.app.data.image

import coil3.ImageLoader
import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.data.local.dao.StoryDao
import com.vegamstories.app.di.IoDispatcher
import com.vegamstories.app.domain.repository.ContentLanguageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OfflineStories"

/** Which stories can be read without internet: their pictures are already on the phone. */
@Singleton
class OfflineStories @Inject constructor(
    private val storyDao: StoryDao,
    private val imageLoader: ImageLoader,
    private val contentLanguage: ContentLanguageRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val log = AppLogger(TAG)

    /** Whether story [storyId] opens without internet (see [readableOffline]). */
    suspend fun canOpen(storyId: String): Boolean = withContext(io) {
        val story = storyDao.getStoryRow(storyId, contentLanguage.contentLanguage.first()) ?: return@withContext false
        val pages = storyDao.getPages(storyId, story.language).map { it.imageUrl }
        readableOffline(
            downloaded = story.isDownloaded,
            pageUrls = pages,
            hasText = story.hasText,
            saved = ::isSaved,
        ).also { log.d("$TAG: Story $storyId without internet: ${if (it) "opens" else "can't open"}") }
    }

    /** The picture at [url] is in the image cache on the phone. */
    private fun isSaved(url: String): Boolean =
        imageLoader.diskCache?.openSnapshot(url)?.use { true } ?: false
}

/**
 * A story reads without internet if it was downloaded, or every comic page has been seen before
 * (and so is saved on the phone), or it has no comic pages, only text, which the app always keeps.
 */
internal fun readableOffline(downloaded: Boolean, pageUrls: List<String>, hasText: Boolean, saved: (String) -> Boolean): Boolean =
    downloaded || if (pageUrls.isEmpty()) hasText else pageUrls.all(saved)
