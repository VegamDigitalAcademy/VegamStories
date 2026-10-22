package com.vegamstories.app.background

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
import timber.log.Timber

/**
 * Runs every few hours in the background (StoryRefreshScheduler):
 *  1. refreshes stories from the server (this also sends any queued likes),
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

    override suspend fun doWork(): Result {
        if (refreshContent().isFailure) {
            // Server trouble: retry a couple of times with backoff, then wait for the next period.
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
        try {
            // StoryWidgetUpdater would also do this, but debounced; the process may be gone by then.
            StoryWidget().updateAll(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Widget update after background refresh failed")
        }
        val decision = checkForNewStory(userIsInApp = false)
        if (decision is NewStoryDecision.Announce) notifier.show(decision.story)
        return Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
