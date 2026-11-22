package com.vegamstories.app

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.vegamstories.app.background.NewStoryNotifier
import com.vegamstories.app.background.StoryRefreshScheduler
import com.vegamstories.app.core.logging.AppLog
import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.presentation.shortcuts.AppShortcutsManager
import com.vegamstories.app.presentation.widget.StoryWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

private const val TAG = "VegamStoriesApp"

@HiltAndroidApp
class VegamStoriesApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var appShortcutsManager: AppShortcutsManager
    @Inject lateinit var storyWidgetUpdater: StoryWidgetUpdater
    @Inject lateinit var storyRefreshScheduler: StoryRefreshScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager starts on demand with Hilt's factory (default start-up is removed in the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val log = AppLogger(TAG)

    override fun onCreate() {
        // Before anything else, so every log from start-up is kept: Logcat in debug builds only;
        // the log files (Settings → Support → Share logs) in every build.
        AppLog.init(this, logcat = BuildConfig.DEBUG)
        super.onCreate()
        log.i(
            "$TAG: App started",
            data = mapOf(
                "version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "build" to BuildConfig.BUILD_TYPE,
                "stories" to if (BuildConfig.USE_FAKE_BACKEND) "sample data" else "server",
                "phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            ),
        )
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
            // A picture that doesn't load (offline, a wrong link on the server) goes in the logs.
            .eventListener(
                object : EventListener() {
                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        imageLog.w("$TAG: Image didn't load: ${request.data} (${result.throwable})")
                    }
                },
            )
            .diskCache {
                // Large enough to hold several downloaded stories for offline reading.
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("story_images"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .build()

    private companion object {
        val imageLog = AppLogger(TAG)
    }
}
