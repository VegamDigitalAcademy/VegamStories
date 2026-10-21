package com.vegamstories.app.background

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

/** Schedules [StoryRefreshWorker]: every few hours, only when online. */
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
    }

    private companion object {
        const val WORK_NAME = "story_refresh"
        const val REPEAT_HOURS = 6L
        const val BACKOFF_MINUTES = 15L
    }
}
