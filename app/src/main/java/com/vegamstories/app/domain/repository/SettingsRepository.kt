package com.vegamstories.app.domain.repository

import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.SeenStory
import com.vegamstories.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** User preferences stored on the device. */
interface SettingsRepository {
    /** Emits [ThemeMode.SYSTEM] until the user picks something else. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** Emits the defaults of [ReaderSettings] for anything the user hasn't changed. */
    val readerSettings: Flow<ReaderSettings>

    /** Applies [transform] to the saved settings in one atomic edit. */
    suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings)

    /** "New story alerts" switch in Settings. Off until the user turns it on. */
    val newStoryAlertsEnabled: Flow<Boolean>

    suspend fun setNewStoryAlertsEnabled(enabled: Boolean)

    /** Bookkeeping for new-story alerts: the newest story already announced or seen; null before the first check. */
    suspend fun getLastSeenStory(): SeenStory?

    suspend fun setLastSeenStory(story: SeenStory)
}
