package com.vegamstories.app.presentation.widget

import com.vegamstories.app.core.logging.AppLogger
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.vegamstories.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StoryWidgetUpdater"

/**
 * Redraws placed widgets whenever what they show changes: a page turned, a story finished, or a
 * refresh brought a new story. Does nothing if no widget is on the home screen.
 */
@Singleton
class StoryWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetData: StoryWidgetData,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    @OptIn(FlowPreview::class)
    fun start() {
        appScope.launch {
            widgetData.observeKey()
                .distinctUntilChanged()
                .debounce(UPDATE_DEBOUNCE_MS) // page flips come quickly; redraw once they settle
                .collect { key ->
                    try {
                        log.timed("$TAG: Redraw home screen widgets") { StoryWidget().updateAll(context) }
                        log.v("$TAG: Widget shows", data = key)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.w("$TAG: Widget update failed", e)
                    }
                }
        }
    }

    private val log = AppLogger(TAG)

    private companion object {
        const val UPDATE_DEBOUNCE_MS = 1_000L
    }
}
