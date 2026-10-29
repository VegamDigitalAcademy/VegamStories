package com.vegamstories.app.testutil

import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.BookmarkItem
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.ContentLanguages
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.SeenStory
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.domain.repository.ContentLanguageRepository
import com.vegamstories.app.domain.repository.LibraryRepository
import com.vegamstories.app.domain.repository.SettingsRepository
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * In-memory repositories for view model tests. Their flows are live, like the database's: change a
 * value here and every screen watching it sees the change.
 */

/** The app's story language in these fakes, as in [testStory]. */
const val APP_LANGUAGE = "te"

class FakeStoryRepository : StoryRepository {
    val categories = MutableStateFlow<List<Category>>(emptyList())

    /** The catalogue in the app's story language, newest first. */
    val stories = MutableStateFlow<List<Story>>(emptyList())

    /** Every story's language versions: [observeStoryDetail] picks one by its story's language. */
    val details = MutableStateFlow<List<StoryDetail>>(emptyList())

    /** The next episode of a story, by story id. */
    val nextStoryIds = mutableMapOf<String, String>()

    /** What [refresh] returns: whether it downloaded, or a failure. */
    var refreshResult: Result<Boolean> = Result.success(false)

    /** While set, [refresh] waits for it, so a test can look at the app mid-refresh. */
    var refreshGate: CompletableDeferred<Unit>? = null

    /** Each refresh's force flag, in order. */
    val refreshes = mutableListOf<Boolean>()

    override fun observeCategories(): Flow<List<Category>> = categories

    override fun observeStories(categoryId: String?): Flow<List<Story>> =
        stories.map { all -> if (categoryId == null) all else all.filter { it.categoryId == categoryId } }

    override fun observeStoryDetail(storyId: String, language: String?): Flow<StoryDetail?> =
        details.map { all -> all.firstOrNull { it.story.id == storyId && it.story.language == (language ?: APP_LANGUAGE) } }

    override fun searchStories(query: String): Flow<List<Story>> =
        stories.map { all -> all.filter { query in it.title } }

    override suspend fun refresh(force: Boolean): Result<Boolean> {
        refreshes += force
        refreshGate?.await()
        return refreshResult
    }

    override suspend fun getNextStoryId(storyId: String): String? = nextStoryIds[storyId]
}

class FakeLibraryRepository : LibraryRepository {
    /** Stories opened, newest first; the finished ones go on the bookshelf. */
    val history = MutableStateFlow<List<Story>>(emptyList())

    /** The story Continue reading offers, on the ALL tab and on its own category's tab. */
    val continueReading = MutableStateFlow<Story?>(null)

    val favorites = mutableSetOf<String>()
    val downloads = mutableSetOf<String>()

    /** Each saveProgress call: story, page, total pages. */
    val savedProgress = mutableListOf<Triple<String, Int, Int>>()

    val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    override fun observeFavorites(): Flow<List<Story>> = emptyFlow()
    override fun observeDownloads(): Flow<List<Story>> = emptyFlow()
    override fun observeReadingHistory(): Flow<List<Story>> = history

    override fun observeContinueReading(categoryId: String?): Flow<Story?> =
        continueReading.map { story -> story?.takeIf { categoryId == null || it.categoryId == categoryId } }

    override suspend fun toggleFavorite(storyId: String): Boolean = toggle(favorites, storyId)
    override suspend fun toggleDownload(storyId: String): Boolean = toggle(downloads, storyId)

    override suspend fun saveProgress(storyId: String, pageIndex: Int, totalPages: Int) {
        savedProgress += Triple(storyId, pageIndex, totalPages)
    }

    override suspend fun clearReadingHistory() {
        history.value = emptyList()
    }

    override fun observeBookmarks(): Flow<List<BookmarkItem>> = emptyFlow()

    override fun observeBookmarks(storyId: String): Flow<List<Bookmark>> =
        bookmarks.map { all -> all.filter { it.storyId == storyId } }

    override suspend fun addBookmark(bookmark: Bookmark) {
        bookmarks.update { it + bookmark }
    }

    override suspend fun removeBookmarks(bookmarks: List<Bookmark>) {
        this.bookmarks.update { it - bookmarks.toSet() }
    }

    /** Adds or removes [id]; returns whether it is in [set] now. */
    private fun toggle(set: MutableSet<String>, id: String): Boolean {
        if (!set.remove(id)) set += id
        return id in set
    }
}

class FakeSettingsRepository(readerSettings: ReaderSettings = ReaderSettings()) : SettingsRepository {
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val readerSettings = MutableStateFlow(readerSettings)
    override val newStoryAlertsEnabled = MutableStateFlow(false)

    /** The newest story announced or seen, as the new-story check left it. */
    var lastSeenStory: SeenStory? = null

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        readerSettings.update(transform)
    }

    override suspend fun setNewStoryAlertsEnabled(enabled: Boolean) {
        newStoryAlertsEnabled.value = enabled
    }

    override suspend fun getLastSeenStory(): SeenStory? = lastSeenStory

    override suspend fun setLastSeenStory(story: SeenStory) {
        lastSeenStory = story
    }
}

class FakeContentLanguageRepository(
    languages: ContentLanguages = ContentLanguages(
        all = listOf(Language(APP_LANGUAGE, "Telugu", "తెలుగు"), Language("en", "English", "English")),
        selected = APP_LANGUAGE,
    ),
) : ContentLanguageRepository {
    val languages = MutableStateFlow(languages)

    override val contentLanguages: Flow<ContentLanguages> = this.languages
    override val contentLanguage: Flow<String> = this.languages.map { it.selected }

    override suspend fun setContentLanguage(code: String) {
        languages.update { it.copy(selected = code, isChosen = true) }
    }
}
