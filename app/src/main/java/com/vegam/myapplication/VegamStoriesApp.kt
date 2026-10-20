package com.vegam.myapplication

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.vegam.myapplication.presentation.shortcuts.AppShortcutsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VegamStoriesApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var appShortcutsManager: AppShortcutsManager

    override fun onCreate() {
        super.onCreate()
        // Long-press-on-icon menu: keeps category shortcuts in sync with the database.
        appShortcutsManager.start()
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
