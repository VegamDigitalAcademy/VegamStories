package com.vegamstories.app.background

import com.vegamstories.app.core.logging.AppLogger
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vegamstories.app.domain.model.NewStoryDecision
import com.vegamstories.app.domain.usecase.CheckForNewStoryUseCase
import com.vegamstories.app.domain.usecase.RefreshContentUseCase
import com.vegamstories.app.presentation.widget.StoryWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

private const val TAG = "StoryRefreshWorker"

/**
 * Runs every few hours in the background (StoryRefreshScheduler):
 *  1. checks the catalogue and downloads the stories only when they changed (queued likes go out
 *     either way),
 *  2. redraws the home screen widget so "Today's new story" is current,
 *  3. posts a "New story" notification if one was published and alerts are on.
 */
@HiltWorker
class StoryRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refreshContent: RefreshContentUseCase,
    private val checkForNewStory: CheckForNewStoryUseCase,
    private val notifier: NewStoryNotifier,
) : CoroutineWorker(appContext, params) {

    private val log = AppLogger(TAG)

    override suspend fun doWork(): Result {
        log.i("$TAG: Background check started (attempt ${runAttemptCount + 1})")
        val refresh = log.timed("$TAG: Background refresh", summary = { if (it.getOrNull() == true) "downloaded" else "no download" }) {
            refreshContent()
        }
        if (refresh.isFailure) {
            // Server trouble: retry a couple of times with backoff, then wait for the next period.
            val retry = runAttemptCount < MAX_RETRIES
            log.w("$TAG: Background refresh failed; ${if (retry) "will retry soon" else "giving up until the next check"}", refresh.exceptionOrNull())
            return if (retry) Result.retry() else Result.success()
        }
        try {
            // StoryWidgetUpdater would also do this, but debounced; the process may be gone by then.
            StoryWidget().updateAll(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w("$TAG: Widget update after background refresh failed", e)
        }
        val decision = checkForNewStory(userIsInApp = false)
        log.d("$TAG: New story check: ${decision.describe()}")
        if (decision is NewStoryDecision.Announce) notifier.show(decision.story)
        return Result.success()
    }

    private fun NewStoryDecision.describe(): String = when (this) {
        is NewStoryDecision.Announce -> "${story.id} is new: notify"
        is NewStoryDecision.MarkSeen -> "${story.id} noted, no notification (first run, already read, or alerts off)"
        NewStoryDecision.NoChange -> "nothing new"
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
