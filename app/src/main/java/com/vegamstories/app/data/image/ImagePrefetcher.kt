package com.vegamstories.app.data.image

import com.vegamstories.app.core.logging.AppLogger
import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ImagePrefetcher"

/**
 * Downloads images into Coil's disk cache so a "downloaded" story can be read offline.
 */
@Singleton
class ImagePrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) {
    private val log = AppLogger(TAG)

    fun prefetch(urls: Collection<String>) {
        log.d("$TAG: Saving ${urls.distinct().size} images for offline reading", data = urls.distinct())
        urls.distinct().forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            imageLoader.enqueue(request)
        }
    }
}
