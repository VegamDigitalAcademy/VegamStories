package com.vegamstories.app.presentation.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen widths, as in Android's window size classes: below [MEDIUM_WIDTH] is a phone held
 * upright; from [MEDIUM_WIDTH] a small tablet, an open foldable or a phone on its side; from
 * [EXPANDED_WIDTH] a large tablet or a tablet on its side.
 */
val MEDIUM_WIDTH = 600.dp
val EXPANDED_WIDTH = 840.dp

/** Story covers per row for a grid [width] wide: 2 on phones, more as the screen grows. */
fun storyGridColumns(width: Dp): Int = when {
    width < MEDIUM_WIDTH -> 2
    width < EXPANDED_WIDTH -> 3
    else -> (width / STORY_CELL_WIDTH).toInt().coerceAtLeast(4)
}

/** About this wide each cover is on large screens, so they stay the size they are on a phone. */
private val STORY_CELL_WIDTH = 200.dp

/** [storyGridColumns] for a lazy grid: worked out from the grid's own width, so it follows rotation and split screen. */
object StoryGridCells : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> =
        with(GridCells.Fixed(storyGridColumns(availableSize.toDp()))) { calculateCrossAxisCellSizes(availableSize, spacing) }
}

/**
 * A wide window lying on its side (a tablet, or an open foldable, held landscape): room for two
 * comic pages side by side.
 */
fun isWideLandscape(width: Dp, height: Dp): Boolean = width >= MEDIUM_WIDTH && width > height

/** [isWideLandscape] for the app's window right now. */
@Composable
fun windowIsWideLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { isWideLandscape(size.width.toDp(), size.height.toDp()) }
}
