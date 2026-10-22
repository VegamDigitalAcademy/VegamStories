package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.NewStoryDecision
import com.vegamstories.app.domain.model.SeenStory
import com.vegamstories.app.domain.model.decideNewStoryAlert
import com.vegamstories.app.domain.repository.SettingsRepository
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Run after a successful refresh. Looks at the newest story in the local cache, remembers it as
 * seen, and says whether to announce it (see [decideNewStoryAlert]). The caller posts the
 * notification for [NewStoryDecision.Announce].
 */
class CheckForNewStoryUseCase @Inject constructor(
    private val storyRepository: StoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** @param userIsInApp true when called from the app itself: the user sees new stories on Home, so never announce. */
    suspend operator fun invoke(userIsInApp: Boolean): NewStoryDecision {
        val newest = storyRepository.observeStories(categoryId = null).first().firstOrNull() // newest first
        val canAnnounce = !userIsInApp && settingsRepository.newStoryAlertsEnabled.first()
        val decision = decideNewStoryAlert(newest, settingsRepository.getLastSeenStory(), canAnnounce)
        val toRemember = when (decision) {
            is NewStoryDecision.Announce -> decision.story
            is NewStoryDecision.MarkSeen -> decision.story
            NewStoryDecision.NoChange -> null
        }
        toRemember?.let { settingsRepository.setLastSeenStory(SeenStory(it.id, it.publishedAt)) }
        return decision
    }
}
