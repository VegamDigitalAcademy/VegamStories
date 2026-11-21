package com.vegamstories.app.data.image

import com.vegamstories.app.core.logging.AppLogger
import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vegamstories.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
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
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val log = AppLogger(TAG)

    private val _saving = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Downloads under way, by key (a story): how much is saved, 0 … 1. Gone once it's all saved. */
    val saving: StateFlow<Map<String, Float>> = _saving.asStateFlow()

    /** Saves [urls] for offline reading, as [key]'s download (see [saving]). */
    fun prefetch(key: String, urls: Collection<String>) {
        val distinct = urls.distinct()
        log.d("$TAG: Saving ${distinct.size} images for offline reading", data = distinct)
        if (distinct.isEmpty()) return
        _saving.update { it + (key to 0f) }
        val jobs = distinct.map { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            imageLoader.enqueue(request).job
        }
        scope.launch {
            val done = AtomicInteger(0)
            jobs.map { job ->
                launch {
                    job.await()
                    val saved = done.incrementAndGet().toFloat() / jobs.size
                    _saving.update { it + (key to saved) }
                }
            }.joinAll()
            // Full for a moment, so it's seen to finish.
            delay(FULL_SHOWN_MILLIS)
            _saving.update { it - key }
        }
    }

    private companion object {
        const val FULL_SHOWN_MILLIS = 400L
    }
}
