package com.vegamstories.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
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
    LazyVerticalGrid(
        columns = StoryGridCells,
        modifier = modifier,
        // Under the bottom bar's glass, the last row scrolls up clear of it.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + LocalBottomBarSpace.current),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        header()
        items(stories, key = { it.id }) { story ->
            StoryCard(story = story, onClick = { onStoryClick(story) })
        }
    }
}

fun LazyGridScope.fullWidthItem(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}
