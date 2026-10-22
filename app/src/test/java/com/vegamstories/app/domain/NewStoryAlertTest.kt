package com.vegamstories.app.domain

import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.NewStoryDecision
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.SeenStory
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.domain.model.decideNewStoryAlert
import com.vegamstories.app.domain.repository.SettingsRepository
import com.vegamstories.app.domain.repository.StoryRepository
import com.vegamstories.app.domain.usecase.CheckForNewStoryUseCase
import com.vegamstories.app.testutil.testStory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewStoryAlertTest {

    // ---------- decideNewStoryAlert ----------

    @Test
    fun `no stories means nothing to do`() {
        assertEquals(NewStoryDecision.NoChange, decideNewStoryAlert(newest = null, lastSeen = null, canAnnounce = true))
    }

    @Test
    fun `first check only remembers, never announces`() {
        val newest = story("moral-5", publishedAt = 500)
        assertEquals(NewStoryDecision.MarkSeen(newest), decideNewStoryAlert(newest, lastSeen = null, canAnnounce = true))
    }

    @Test
    fun `same story as last time is not news`() {
        val newest = story("moral-5", publishedAt = 500)
        assertEquals(
            NewStoryDecision.NoChange,
            decideNewStoryAlert(newest, SeenStory("moral-5", 500), canAnnounce = true),
        )
    }

    @Test
    fun `a newer story is announced`() {
        val newest = story("moral-6", publishedAt = 600)
        assertEquals(
            NewStoryDecision.Announce(newest),
            decideNewStoryAlert(newest, SeenStory("moral-5", 500), canAnnounce = true),
        )
    }

    @Test
    fun `a newer story is only remembered when alerts can't be shown`() {
        val newest = story("moral-6", publishedAt = 600)
        assertEquals(
            NewStoryDecision.MarkSeen(newest),
            decideNewStoryAlert(newest, SeenStory("moral-5", 500), canAnnounce = false),
        )
    }

    @Test
    fun `a story the user already opened is not announced`() {
        val newest = story("moral-6", publishedAt = 600, opened = true)
        assertEquals(
            NewStoryDecision.MarkSeen(newest),
            decideNewStoryAlert(newest, SeenStory("moral-5", 500), canAnnounce = true),
        )
    }

    @Test
    fun `an older story (last one was unpublished) is remembered, not announced`() {
        val newest = story("moral-4", publishedAt = 400)
        assertEquals(
            NewStoryDecision.MarkSeen(newest),
            decideNewStoryAlert(newest, SeenStory("moral-5", 500), canAnnounce = true),
        )
    }

    // ---------- CheckForNewStoryUseCase ----------

    @Test
    fun `background check announces and remembers a new story when alerts are on`() = runTest {
        val settings = FakeSettingsRepository(alertsEnabled = true, lastSeen = SeenStory("moral-5", 500))
        val useCase = CheckForNewStoryUseCase(FakeStoryRepository(story("moral-6", 600), story("moral-5", 500)), settings)

        val decision = useCase(userIsInApp = false)

        assertEquals(NewStoryDecision.Announce(story("moral-6", 600)), decision)
        assertEquals(SeenStory("moral-6", 600), settings.lastSeen)
    }

    @Test
    fun `check from inside the app remembers without announcing`() = runTest {
        val settings = FakeSettingsRepository(alertsEnabled = true, lastSeen = SeenStory("moral-5", 500))
        val useCase = CheckForNewStoryUseCase(FakeStoryRepository(story("moral-6", 600)), settings)

        val decision = useCase(userIsInApp = true)

        assertEquals(NewStoryDecision.MarkSeen(story("moral-6", 600)), decision)
        assertEquals(SeenStory("moral-6", 600), settings.lastSeen)
    }

    @Test
    fun `nothing is remembered when there are no stories`() = runTest {
        val settings = FakeSettingsRepository(alertsEnabled = true, lastSeen = null)
        val useCase = CheckForNewStoryUseCase(FakeStoryRepository(), settings)

        assertEquals(NewStoryDecision.NoChange, useCase(userIsInApp = false))
        assertNull(settings.lastSeen)
    }

    // ---------- helpers ----------

    private fun story(id: String, publishedAt: Long, opened: Boolean = false) =
        testStory(id = id, publishedAt = publishedAt, opened = opened)

    /** [stories] newest first, like the real repository. */
    private class FakeStoryRepository(vararg stories: Story) : StoryRepository {
        private val list = stories.toList()
        override fun observeCategories(): Flow<List<Category>> = emptyFlow()
        override fun observeStories(categoryId: String?): Flow<List<Story>> = flowOf(list)
        override fun observeStoryDetail(storyId: String, language: String?): Flow<StoryDetail?> = emptyFlow()
        override fun searchStories(query: String): Flow<List<Story>> = emptyFlow()
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
        override suspend fun getNextStoryId(storyId: String): String? = null
    }

    private class FakeSettingsRepository(alertsEnabled: Boolean, var lastSeen: SeenStory?) : SettingsRepository {
        override val themeMode: Flow<ThemeMode> = emptyFlow()
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override val readerSettings: Flow<ReaderSettings> = emptyFlow()
        override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) = Unit
        override val newStoryAlertsEnabled = MutableStateFlow(alertsEnabled)
        override suspend fun setNewStoryAlertsEnabled(enabled: Boolean) {
            newStoryAlertsEnabled.value = enabled
        }
        override suspend fun getLastSeenStory(): SeenStory? = lastSeen
        override suspend fun setLastSeenStory(story: SeenStory) {
            lastSeen = story
        }
    }
}
