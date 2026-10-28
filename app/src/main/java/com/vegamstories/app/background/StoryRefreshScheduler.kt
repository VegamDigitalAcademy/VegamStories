package com.vegamstories.app.background

import com.vegamstories.app.core.logging.AppLogger
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StoryRefreshScheduler"

/**
 * Schedules [StoryRefreshWorker]: every few hours, only when online. Each run downloads only when
 * the saved stories are a day old, so the server is called at most once a day in the background.
 */
@Singleton
class StoryRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Call on every app start. Keeps the existing schedule; only a changed request is updated. */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<StoryRefreshWorker>(REPEAT_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        log.d("$TAG: Background check scheduled: every $REPEAT_HOURS hours, when online")
    }

    private val log = AppLogger(TAG)

    private companion object {
        const val WORK_NAME = "story_refresh"
        const val REPEAT_HOURS = 6L
        const val BACKOFF_MINUTES = 15L
    }
}
