package com.vegamstories.app.data.repository

import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.data.image.ImagePrefetcher
import com.vegamstories.app.data.local.dao.LibraryDao
import com.vegamstories.app.data.local.dao.StoryDao
import com.vegamstories.app.data.local.entity.DownloadEntity
import com.vegamstories.app.data.local.entity.ReadingProgressEntity
import com.vegamstories.app.data.mapper.toDomain
import com.vegamstories.app.data.mapper.toEntity
import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.BookmarkItem
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.di.ApplicationScope
import com.vegamstories.app.domain.repository.ContentLanguageRepository
import com.vegamstories.app.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LibraryRepositoryImpl"

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

    private val log = AppLogger(TAG)

    // The library shows every saved story: in the app's story language when it has that version,
    // else in another language it has (see LibraryDao).

    override fun observeFavorites(): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            libraryDao.observeFavorites(lang).map { rows ->
                log.v("$TAG: Favorites: ${rows.size}")
                rows.map { it.toDomain() }
            }
        }

    override fun observeDownloads(): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            libraryDao.observeDownloads(lang).map { rows ->
                log.v("$TAG: Downloads: ${rows.size}")
                rows.map { it.toDomain() }
            }
        }

    override fun observeReadingHistory(): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            libraryDao.observeHistory(lang).map { rows ->
                log.v("$TAG: Reading history: ${rows.size}")
                rows.map { it.toDomain() }
            }
        }

    override fun observeContinueReading(categoryId: String?): Flow<Story?> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            libraryDao.observeContinueReading(categoryId, lang).map { it?.toDomain() }
        }

    override suspend fun toggleFavorite(storyId: String): Boolean {
        val liked = libraryDao.toggleFavorite(storyId, now())
        log.i("$TAG: ${if (liked) "Liked" else "Unliked"} $storyId (saved; the like goes to the server next)")
        // The heart and count update at once; the server catches up in the background
        // (and survives the screen closing).
        appScope.launch { likeSyncer.syncPending() }
        return liked
    }

    override suspend fun toggleDownload(storyId: String): Boolean {
        if (libraryDao.isDownloaded(storyId)) {
            libraryDao.deleteDownload(storyId)
            log.i("$TAG: Download removed: $storyId")
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
        log.i(
            "$TAG: Downloaded $storyId for offline reading",
            data = mapOf("language" to story?.language, "comic pages" to pages.size, "images saving" to urls.size),
        )
        return true
    }

    override suspend fun saveProgress(storyId: String, pageIndex: Int, totalPages: Int) {
        val existing = libraryDao.getProgress(storyId)
        val reachedEnd = pageIndex >= totalPages - 1
        log.d(
            "$TAG: Progress $storyId: ${pageIndex + 1} of $totalPages" +
                if (reachedEnd && existing?.isCompleted != true) " — finished" else "",
        )
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

    override suspend fun clearReadingHistory() {
        libraryDao.clearProgress()
        log.i("$TAG: Reading history cleared")
    }

    override fun observeBookmarks(): Flow<List<BookmarkItem>> =
        libraryDao.observeBookmarks().flatMapLatest { entities ->
            log.v("$TAG: Bookmarks: ${entities.size}")
            if (entities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(entities.map { bookmarkItem(it.toDomain()) }) { items -> items.filterNotNull() }
            }
        }

    /** [bookmark] with its story in the bookmark's language, and the marked page's picture or first words. */
    private fun bookmarkItem(bookmark: Bookmark): Flow<BookmarkItem?> {
        val page: Flow<Pair<String?, String?>> = if (bookmark.format == StoryFormat.TEXT) {
            storyDao.observeText(bookmark.storyId, bookmark.language).map { body ->
                val paragraph = body?.let { StoryText(bookmark.language, it).paragraphs.getOrNull(bookmark.position) }
                val words = paragraph?.drop(bookmark.offset)?.trim()
                null to if (words != null && words.length > SNIPPET_LENGTH) words.take(SNIPPET_LENGTH).trimEnd() + "…" else words
            }
        } else {
            storyDao.observePages(bookmark.storyId, bookmark.language).map { pages ->
                pages.getOrNull(bookmark.position)?.imageUrl to null
            }
        }
        return combine(storyDao.observeStory(bookmark.storyId, bookmark.language), page) { row, (image, snippet) ->
            row?.let { BookmarkItem(bookmark, it.toDomain(), pageImageUrl = image, snippet = snippet) }
        }
    }

    override fun observeBookmarks(storyId: String): Flow<List<Bookmark>> =
        libraryDao.observeBookmarks(storyId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addBookmark(bookmark: Bookmark) {
        libraryDao.upsertBookmark(bookmark.toEntity(createdAt = now()))
        log.i("$TAG: Bookmark added", data = bookmark)
    }

    override suspend fun removeBookmarks(bookmarks: List<Bookmark>) {
        libraryDao.deleteBookmarks(bookmarks.map { it.toEntity(createdAt = 0L) })
        log.i("$TAG: Bookmarks removed", data = bookmarks)
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        /** Characters of a text bookmark shown in the library. */
        const val SNIPPET_LENGTH = 120
    }
}
