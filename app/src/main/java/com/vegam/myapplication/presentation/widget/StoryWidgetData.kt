package com.vegam.myapplication.presentation.widget

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.usecase.GetContinueReadingUseCase
import com.vegam.myapplication.domain.usecase.GetStoriesUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** One row of the widget. */
data class WidgetEntry(
    val kind: Kind,
    val story: Story,
    /** Cover as a bitmap (widgets can't load URLs themselves); null if it couldn't be loaded. */
    val cover: Bitmap?,
) {
    enum class Kind { CONTINUE_READING, NEW_TODAY, LATEST }
}

/**
 * What the widget shows, read from the local database (the same cache the app shows offline):
 *  1. "Continue reading": the story the user is in the middle of, if any.
 *  2. "Today's new story" (published today) or else "Latest story": the newest story, if it's
 *     a different one.
 * The widget shows the first, or both when it's tall enough.
 */
/**
 * Which rows the widget shows, in order, and how each is labelled: continue-reading first, then
 * the newest story if it's a different one ("Today's new story" if [isToday], else "Latest story").
 * Kept free of Android calls so it can be unit-tested.
 */
internal fun widgetRows(
    continueReading: Story?,
    newest: Story?,
    isToday: (publishedAt: Long) -> Boolean,
): List<Pair<WidgetEntry.Kind, Story>> = buildList {
    continueReading?.let { add(WidgetEntry.Kind.CONTINUE_READING to it) }
    if (newest != null && newest.id != continueReading?.id) {
        add((if (isToday(newest.publishedAt)) WidgetEntry.Kind.NEW_TODAY else WidgetEntry.Kind.LATEST) to newest)
    }
}

@Singleton
class StoryWidgetData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getContinueReading: GetContinueReadingUseCase,
    private val getStories: GetStoriesUseCase,
    private val imageLoader: ImageLoader,
) {

    /** Emits whenever what the widget would show changes (used to refresh placed widgets). */
    fun observeKey(): Flow<List<Any?>> = observeStories().map { (continueReading, newest) ->
        listOf(
            continueReading?.id, continueReading?.title, continueReading?.coverImageUrl,
            continueReading?.progress?.lastPageIndex,
            newest?.id, newest?.title, newest?.coverImageUrl, newest?.isRead,
        )
    }

    suspend fun load(): List<WidgetEntry> {
        val (continueReading, newest) = observeStories().first()
        return widgetRows(continueReading, newest, isToday = DateUtils::isToday)
            .map { (kind, story) -> WidgetEntry(kind, story, loadCover(story)) }
    }

    private fun observeStories(): Flow<Pair<Story?, Story?>> = combine(
        getContinueReading(null),
        getStories(null).map { it.firstOrNull() }, // newest first
    ) { continueReading, newest -> continueReading to newest }

    private suspend fun loadCover(story: Story): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(story.coverImageUrl)
            .size(COVER_WIDTH_PX, COVER_HEIGHT_PX) // widget bitmaps travel over IPC: keep them small
            .allowHardware(false) // RemoteViews can't draw hardware bitmaps
            .build()
        return (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    private companion object {
        const val COVER_WIDTH_PX = 180
        const val COVER_HEIGHT_PX = 240
    }
}
