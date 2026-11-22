package com.vegamstories.app.testutil

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled

/**
 * The app in Robolectric tests (see robolectric.properties): none of VegamStoriesApp's start-up
 * work (Hilt, background refresh, log files). Images have no network to load from, so every
 * story picture shows its "couldn't load" look at once, the same on every run.
 */
class TestApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .serviceLoaderEnabled(false) // leaves out coil-network-okhttp
            .build()
}
