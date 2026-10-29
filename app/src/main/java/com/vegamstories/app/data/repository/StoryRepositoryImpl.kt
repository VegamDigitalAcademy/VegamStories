package com.vegamstories.app.data.repository

import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.core.logging.logTime
import com.vegamstories.app.data.local.dao.StoryDao
import com.vegamstories.app.data.mapper.toDomain
import com.vegamstories.app.data.mapper.toEntity
import com.vegamstories.app.data.mapper.toNameEntities
import com.vegamstories.app.data.mapper.toPageEntities
import com.vegamstories.app.data.mapper.toTranslationEntities
import com.vegamstories.app.data.remote.datasource.CatalogueUpdate
import com.vegamstories.app.data.remote.datasource.StoryRemoteDataSource
import com.vegamstories.app.data.remote.dto.FeedResponseDto
import com.vegamstories.app.data.settings.ContentSyncStore
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.model.isContentRefreshDue
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StoryRepositoryImpl"

/**
 * Offline-first: the UI always reads from Room; [refresh] pulls from the server (at most once a
 * day unless the reader asks) and writes into Room, which then pushes the new data to every observer.
 * Lists follow the story language chosen in the app ([ContentLanguageRepository]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class StoryRepositoryImpl @Inject constructor(
    private val storyDao: StoryDao,
    private val remote: StoryRemoteDataSource,
    private val likeSyncer: LikeSyncer,
    private val contentLanguage: ContentLanguageRepository,
    private val syncStore: ContentSyncStore,
) : StoryRepository {

    private val log = AppLogger(TAG)

    /** One refresh at a time (Home, the first-launch screen and the background worker can overlap). */
    private val refreshLock = Mutex()

    /** Whether the last refresh downloaded stories, for a refresh that waited for it to finish. */
    @Volatile
    private var lastDownloaded = false

    override fun observeCategories(): Flow<List<Category>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            storyDao.observeCategories(lang).map { list ->
                log.v("$TAG: Categories in $lang: ${list.size}")
                list.map { it.toDomain() }
            }
        }

    override fun observeStories(categoryId: String?): Flow<List<Story>> =
        contentLanguage.contentLanguage.flatMapLatest { lang ->
            val source = if (categoryId == null) {
                storyDao.observeAllStories(lang)
            } else {
                storyDao.observeStoriesByCategory(categoryId, lang)
            }
            source.map { rows ->
                log.v("$TAG: Stories in $lang${categoryId?.let { " / $it" }.orEmpty()}: ${rows.size}")
                rows.map { it.toDomain() }
            }
        }

    override fun observeStoryDetail(storyId: String, language: String?): Flow<StoryDetail?> {
        val lang = if (language != null) flowOf(language) else contentLanguage.contentLanguage
        return lang.flatMapLatest { requested ->
            // The version shown: [requested] if the story has it, else another language it's in.
            storyDao.observeStory(storyId, requested).flatMapLatest { row ->
                if (row == null) {
                    log.w("$TAG: Story $storyId isn't in the saved stories (removed on the server?)")
                    flowOf(null)
                } else {
                    combine(
                        storyDao.observePages(storyId, row.language).distinctUntilChanged(),
                        storyDao.observeText(storyId, row.language).distinctUntilChanged(),
                        storyDao.observeStoryLanguages(storyId).distinctUntilChanged(),
                    ) { pages, body, languages ->
                        log.v(
                            "$TAG: Story $storyId in ${row.language}",
                            data = mapOf(
                                "asked for" to requested,
                                "comic pages" to pages.size,
                                "text" to (body != null),
                                "video" to (row.videoUrl != null),
                                "languages" to languages.map { it.code },
                            ),
                        )
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
            storyDao.searchStories(query, lang).map { rows ->
                log.d("$TAG: Search \"$query\" in $lang: ${rows.size} found", data = rows.take(5).map { it.title })
                rows.map { it.toDomain() }
            }
        }

    override suspend fun refresh(force: Boolean): Result<Boolean> {
        log.d("$TAG: Refresh requested", data = mapOf("force" to force))
        val before = syncStore.lastRefreshAt()
        return refreshLock.withLock {
            val last = syncStore.lastRefreshAt()
            if (last != before) {
                // Another refresh finished while this one waited: what's saved is as fresh as it gets.
                log.d("$TAG: Refresh: another refresh just finished; using what it found")
                return@withLock Result.success(lastDownloaded)
            }
            val hasContent = storyDao.hasContent()
            if (!force && !isContentRefreshDue(hasContent, last, System.currentTimeMillis())) {
                // No download; likes made offline still go out (no call when there are none).
                log.d(
                    "$TAG: Refresh: checked a moment ago, not checking again yet",
                    data = mapOf("last check" to logTime(last), "stories saved" to hasContent),
                )
                likeSyncer.syncPending()
                Result.success(false)
            } else {
                log.i(
                    "$TAG: Refresh: checking the catalogue",
                    data = mapOf("force" to force, "last check" to logTime(last), "stories saved" to hasContent),
                )
                download(hasContent)
            }
        }
    }

    /**
     * Checks the catalogue and downloads it only when its version is new to the phone; the like
     * counts are read either way. Succeeds with whether stories were downloaded.
     */
    private suspend fun download(hasContent: Boolean): Result<Boolean> = try {
        // Send queued likes first, so the like counts below already include them.
        likeSyncer.syncPending()
        // The saved version only counts while its stories are still on the phone.
        val savedVersion = if (hasContent) syncStore.catalogueVersion() else null
        val update = log.timed("$TAG: Check the catalogue") { remote.fetchCatalogue(savedVersion) }
        val likeCounts = fetchLikeCounts()
        when (update) {
            CatalogueUpdate.Unchanged -> {
                likeCounts?.let { storyDao.setLikeCounts(it) }
                log.i("$TAG: Refresh done: stories unchanged" + if (likeCounts != null) ", like counts updated" else "")
            }
            is CatalogueUpdate.Changed -> {
                val feed = update.feed
                // Like counts: fresh from the server, else the feed's own, else the ones on the phone.
                val saved = storyDao.likeCounts().associate { it.id to it.likeCount }
                log.timed("$TAG: Save stories on the phone") {
                    storyDao.replaceContent(
                        categories = feed.categories.map { it.toEntity() },
                        categoryNames = feed.categories.flatMap { it.toNameEntities() },
                        stories = feed.stories.map { story ->
                            story.toEntity().copy(likeCount = likeCounts?.get(story.id) ?: story.likeCount ?: saved[story.id] ?: 0)
                        },
                        translations = feed.stories.flatMap { it.toTranslationEntities() },
                        pages = feed.stories.flatMap { it.toPageEntities() },
                        languages = feed.languages.map { it.toEntity() },
                    )
                }
                syncStore.setCatalogueVersion(update.version)
                log.i("$TAG: Refresh done: new stories saved (version ${update.version})", data = feed.summary())
            }
        }
        syncStore.setLastRefreshAt(System.currentTimeMillis())
        lastDownloaded = update is CatalogueUpdate.Changed
        Result.success(lastDownloaded)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w("$TAG: Refresh failed; showing the saved stories", e)
        Result.failure(e)
    }

    /** The like counts from the server, or null if they couldn't be read (the saved ones stay). */
    private suspend fun fetchLikeCounts(): Map<String, Int>? = try {
        remote.fetchLikeCounts()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w("$TAG: Like counts not loaded; keeping the saved ones", e)
        null
    }

    /** What a download brought, for the log: counts per language, not the stories themselves. */
    private fun FeedResponseDto.summary(): Map<String, Any?> = mapOf(
        "categories" to categories.size,
        "stories" to stories.size,
        "languages" to languages.map { it.code },
        "stories per language" to stories.flatMap { story -> story.translations.map { it.language } }
            .groupingBy { it }.eachCount(),
        "newest" to stories.maxByOrNull { it.publishedAt }?.let { "${it.id} (${logTime(it.publishedAt)})" },
    )

    /** Next episode in the same category that exists in the story language. */
    override suspend fun getNextStoryId(storyId: String): String? {
        val current = storyDao.getStory(storyId) ?: return null
        val lang = contentLanguage.contentLanguage.first()
        return storyDao.getNextStoryId(current.categoryId, current.episodeNumber, lang).also { next ->
            log.d("$TAG: Next episode after $storyId in $lang: ${next ?: "none (last episode)"}")
        }
    }
}
