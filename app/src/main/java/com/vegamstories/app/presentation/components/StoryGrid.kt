package com.vegamstories.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import com.vegamstories.app.presentation.LocalSplashDone
import com.vegamstories.app.presentation.navigation.EmphasizedEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.navigation.LocalBottomBarSpace

/** Story grid: 2 columns on phones, more on tablets ([storyGridColumns]). [header] items span the full width (section titles, cards…). */
@Composable
fun StoryGrid(
    stories: List<Story>,
    onStoryClick: (Story) -> Unit,
    modifier: Modifier = Modifier,
    header: LazyGridScope.() -> Unit = {},
) {
    // A new set of stories (the first load, another category): the cards on screen rise in one after
    // another. Not again for the same set, such as coming back from a story.
    // On launch, only once the splash has gone, or it would all happen behind it.
    val shownSet = stories.take(SET_KEY_SIZE).map { it.id }
    val splashDone = LocalSplashDone.current
    var risenSet by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val rising = risenSet != shownSet
    val risingSince = remember(shownSet, splashDone) { SystemClock.uptimeMillis() }
    LaunchedEffect(shownSet, splashDone) {
        if (!splashDone) return@LaunchedEffect
        delay(RISE_WINDOW_MILLIS)
        risenSet = shownSet
    }
    LazyVerticalGrid(
        columns = StoryGridCells,
        modifier = modifier,
        // Under the bottom bar's glass, the last row scrolls up clear of it.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + LocalBottomBarSpace.current),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        header()
        itemsIndexed(stories, key = { _, story -> story.id }) { index, story ->
            // Only the cards there at first rise; ones scrolled to later are simply there.
            val rises = rising && (!splashDone || SystemClock.uptimeMillis() - risingSince < RISE_WINDOW_MILLIS)
            val risen = remember(shownSet) { Animatable(if (rises) 0f else 1f) }
            LaunchedEffect(shownSet, splashDone) {
                if (splashDone && risen.value < 1f) {
                    delay(index.coerceAtMost(RISE_STAGGER_LIMIT) * RISE_STAGGER_MILLIS)
                    risen.animateTo(1f, tween(durationMillis = RISE_MILLIS, easing = EmphasizedEasing))
                }
            }
            StoryCard(
                story = story,
                onClick = { onStoryClick(story) },
                modifier = Modifier.graphicsLayer {
                    alpha = risen.value
                    translationY = (1f - risen.value) * RISE_FROM.toPx()
                },
            )
        }
    }
}

/** The first few stories name a set: when they change, it's a new set of cards. */
private const val SET_KEY_SIZE = 6

/** Cards rise in over this long, one this soon after another (up to this many), from this far down. */
private const val RISE_MILLIS = 380
private const val RISE_STAGGER_MILLIS = 45L
private const val RISE_STAGGER_LIMIT = 8
private val RISE_FROM = 24.dp

/** Cards laid out within this long of a new set rise in. */
private const val RISE_WINDOW_MILLIS = 700L

fun LazyGridScope.fullWidthItem(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}
