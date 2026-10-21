package com.vegam.myapplication

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.vegam.myapplication.background.NewStoryNotifier
import com.vegam.myapplication.background.StoryRefreshScheduler
import com.vegam.myapplication.core.logging.LineNumberDebugTree
import com.vegam.myapplication.presentation.shortcuts.AppShortcutsManager
import com.vegam.myapplication.presentation.widget.StoryWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VegamStoriesApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var appShortcutsManager: AppShortcutsManager
    @Inject lateinit var storyWidgetUpdater: StoryWidgetUpdater
    @Inject lateinit var storyRefreshScheduler: StoryRefreshScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager starts on demand with Hilt's factory (default start-up is removed in the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Logs only in debug builds; release builds plant nothing, so Timber calls are free no-ops.
        if (BuildConfig.DEBUG) Timber.plant(LineNumberDebugTree())
        // Long-press-on-icon menu: keeps category shortcuts in sync with the database.
        appShortcutsManager.start()
        // Home screen widget: redraws when continue-reading or the newest story changes.
        storyWidgetUpdater.start()
        // Background refresh every few hours: keeps the widget current and spots new stories.
        storyRefreshScheduler.schedule()
        NewStoryNotifier.createChannel(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.25).build()
            }
            .diskCache {
                // Large enough to hold several downloaded stories for offline reading.
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("story_images"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .build()
}
