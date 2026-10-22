package com.vegamstories.app.data.repository

import com.vegamstories.app.data.local.dao.StoryDao
import com.vegamstories.app.data.mapper.toDomain
import com.vegamstories.app.data.mapper.toEntity
import com.vegamstories.app.data.mapper.toNameEntities
import com.vegamstories.app.data.mapper.toPageEntities
import com.vegamstories.app.data.mapper.toTranslationEntities
import com.vegamstories.app.data.remote.datasource.StoryRemoteDataSource
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.repository.ContentLanguageRepository
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first: the UI always reads from Room; [refresh] pulls from the server and
 * writes into Room, which then pushes the new data to every observer.
 * Lists follow the story language chosen in the app ([ContentLanguageRepository]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class StoryRepositoryImpl @Inject constructor(
    private val storyDao: StoryDao,
    private val remote: StoryRemoteDataSource,
    private val likeSyncer: LikeSyncer,
    private val contentLanguage: ContentLanguageRepository,
) : StoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            storyDao.observeCategories(lang).map { list -> list.map { it.toDomain() } }
        }

    override fun observeStories(categoryId: String?): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            val source = if (categoryId == null) {
                storyDao.observeAllStories(lang)
            } else {
                storyDao.observeStoriesByCategory(categoryId, lang)
            }
            source.map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeStoryDetail(storyId: String, language: String?): Flow<StoryDetail?> {
        val lang = if (language != null) flowOf(language) else contentLanguage.contentLanguage
        return lang.flatMapLatest { requested ->
            // The version shown: [requested] if the story has it, else another language it's in.
            storyDao.observeStory(storyId, requested).flatMapLatest { row ->
                if (row == null) {
                    flowOf(null)
                } else {
                    combine(
                        storyDao.observePages(storyId, row.language).distinctUntilChanged(),
                        storyDao.observeText(storyId, row.language).distinctUntilChanged(),
                        storyDao.observeStoryLanguages(storyId).distinctUntilChanged(),
                    ) { pages, body, languages ->
                        StoryDetail(
                            story = row.toDomain(),
                            pages = pages.map { it.toDomain() },
                            text = body?.let { StoryText(languageCode = row.language, body = it) },
                            languages = languages.map { it.toDomain() },
                        )
                    }
                }
            }
        }
    }

    override fun searchStories(query: String): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            storyDao.searchStories(query, lang).map { rows -> rows.map { it.toDomain() } }
        }

    override suspend fun refresh(): Result<Unit> = try {
        // Send queued likes first, so the like counts in the feed already include them.
        likeSyncer.syncPending()
        val feed = remote.fetchFeed()
        storyDao.replaceContent(
            categories = feed.categories.map { it.toEntity() },
            categoryNames = feed.categories.flatMap { it.toNameEntities() },
            stories = feed.stories.map { it.toEntity() },
            translations = feed.stories.flatMap { it.toTranslationEntities() },
            pages = feed.stories.flatMap { it.toPageEntities() },
            languages = feed.languages.map { it.toEntity() },
        )
        Timber.i("Refreshed: %d categories, %d stories", feed.categories.size, feed.stories.size)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Refresh failed, showing cached stories")
        Result.failure(e)
    }

    /** Next episode in the same category that exists in the story language. */
    override suspend fun getNextStoryId(storyId: String): String? {
        val current = storyDao.getStory(storyId) ?: return null
        return storyDao.getNextStoryId(current.categoryId, current.episodeNumber, contentLanguage.contentLanguage.first())
    }
}
