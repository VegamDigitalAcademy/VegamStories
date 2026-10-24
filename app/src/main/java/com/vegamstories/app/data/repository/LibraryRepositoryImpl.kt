package com.vegamstories.app.data.repository

import com.vegamstories.app.data.image.ImagePrefetcher
import com.vegamstories.app.data.local.dao.LibraryDao
import com.vegamstories.app.data.local.dao.StoryDao
import com.vegamstories.app.data.local.entity.DownloadEntity
import com.vegamstories.app.data.local.entity.ReadingProgressEntity
import com.vegamstories.app.data.mapper.toDomain
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.di.ApplicationScope
import com.vegamstories.app.domain.repository.ContentLanguageRepository
import com.vegamstories.app.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val libraryDao: LibraryDao,
    private val storyDao: StoryDao,
    private val imagePrefetcher: ImagePrefetcher,
    private val likeSyncer: LikeSyncer,
    private val contentLanguage: ContentLanguageRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : LibraryRepository {

    // The library shows every saved story: in the app's story language when it has that version,
    // else in another language it has (see LibraryDao).

    override fun observeFavorites(): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            libraryDao.observeFavorites(lang).map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeDownloads(): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            libraryDao.observeDownloads(lang).map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeReadingHistory(): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            libraryDao.observeHistory(lang).map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeContinueReading(categoryId: String?): Flow<Story?> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            libraryDao.observeContinueReading(categoryId, lang).map { it?.toDomain() }
        }

    override suspend fun toggleFavorite(storyId: String): Boolean {
        val liked = libraryDao.toggleFavorite(storyId, now())
        // The heart and count update at once; the server catches up in the background
        // (and survives the screen closing).
        appScope.launch { likeSyncer.syncPending() }
        return liked
    }

    override suspend fun toggleDownload(storyId: String): Boolean {
        if (libraryDao.isDownloaded(storyId)) {
            libraryDao.deleteDownload(storyId)
            return false
        }
        // Story text is already in Room; cache every image of the version the user reads, so it reads offline.
        val story = storyDao.getStoryRow(storyId, contentLanguage.contentLanguage.first())
        val pages = story?.let { storyDao.getPages(storyId, it.language) }.orEmpty()
        val urls = mutableListOf<String>()
        story?.coverImageUrl?.let { urls.add(it) }
        story?.videoThumbnailUrl?.let { urls.add(it) }
        pages.forEach { page -> urls.add(page.imageUrl) }
        imagePrefetcher.prefetch(urls)
        libraryDao.upsertDownload(DownloadEntity(storyId, now()))
        return true
    }

    override suspend fun saveProgress(storyId: String, pageIndex: Int, totalPages: Int) {
        val existing = libraryDao.getProgress(storyId)
        val reachedEnd = pageIndex >= totalPages - 1
        libraryDao.upsertProgress(
            ReadingProgressEntity(
                storyId = storyId,
                lastPageIndex = pageIndex,
                totalPages = totalPages,
                // Once finished, a story stays "read" even if re-opened from page 1.
                isCompleted = reachedEnd || existing?.isCompleted == true,
                updatedAt = now(),
            ),
        )
    }

    override suspend fun clearReadingHistory() = libraryDao.clearProgress()

    private fun now() = System.currentTimeMillis()
}
