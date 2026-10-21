package com.vegamstories.app.data.repository

import com.vegamstories.app.data.local.dao.StoryDao
import com.vegamstories.app.data.mapper.toDomain
import com.vegamstories.app.data.mapper.toEntity
import com.vegamstories.app.data.mapper.toPageEntities
import com.vegamstories.app.data.remote.datasource.StoryRemoteDataSource
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first: the UI always reads from Room; [refresh] pulls from the server and
 * writes into Room, which then pushes the new data to every observer.
 */
@Singleton
class StoryRepositoryImpl @Inject constructor(
    private val storyDao: StoryDao,
    private val remote: StoryRemoteDataSource,
    private val likeSyncer: LikeSyncer,
) : StoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        storyDao.observeCategories().map { list -> list.map { it.toDomain() } }

    override fun observeStories(categoryId: String?): Flow<List<Story>> {
        val source = if (categoryId == null) {
            storyDao.observeAllStories()
        } else {
            storyDao.observeStoriesByCategory(categoryId)
        }
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeStoryDetail(storyId: String): Flow<StoryDetail?> =
        combine(
            storyDao.observeStory(storyId),
            storyDao.observePages(storyId).distinctUntilChanged(),
        ) { row, pages ->
            row?.let { StoryDetail(story = it.toDomain(), pages = pages.map { page -> page.toDomain() }) }
        }

    override fun searchStories(query: String): Flow<List<Story>> =
        storyDao.searchStories(query).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(): Result<Unit> = try {
        // Send queued likes first, so the like counts in the feed already include them.
        likeSyncer.syncPending()
        val feed = remote.fetchFeed()
        storyDao.replaceContent(
            categories = feed.categories.map { it.toEntity() },
            stories = feed.stories.map { it.toEntity() },
            pages = feed.stories.flatMap { it.toPageEntities() },
        )
        Timber.i("Refreshed: %d categories, %d stories", feed.categories.size, feed.stories.size)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Refresh failed, showing cached stories")
        Result.failure(e)
    }

    override suspend fun getNextStoryId(storyId: String): String? {
        val current = storyDao.getStory(storyId) ?: return null
        return storyDao.getNextStoryId(current.categoryId, current.episodeNumber)
    }
}
