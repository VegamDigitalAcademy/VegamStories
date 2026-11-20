package com.vegamstories.app.presentation.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.data.image.OfflineStories
import com.vegamstories.app.data.network.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "OfflineViewModel"

/** The internet connection, for the "No internet" notice, and whether a story opens without it. */
@HiltViewModel
class OfflineViewModel @Inject constructor(
    private val connectivity: ConnectivityObserver,
    private val offlineStories: OfflineStories,
) : ViewModel() {
    private val log = AppLogger(TAG)

    val isOnline: StateFlow<Boolean> = connectivity.isOnline
        .onEach { log.i("$TAG: Internet ${if (it) "on" else "off"}") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, connectivity.isOnlineNow())

    /** Story [storyId] can be opened now: there's internet, or it reads without (OfflineStories). */
    suspend fun canOpen(storyId: String): Boolean = connectivity.isOnlineNow() || offlineStories.canOpen(storyId)
}
