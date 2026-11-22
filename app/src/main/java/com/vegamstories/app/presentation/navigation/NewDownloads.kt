package com.vegamstories.app.presentation.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Stories downloaded in the reader (where the bottom bar is hidden) that the bar hasn't shown yet:
 * when it comes back, the Downloads tab gives a hop, to show where they went (GlassBottomBar).
 */
@Stable
class NewDownloads {
    var count by mutableIntStateOf(0)
        private set

    fun added() {
        count++
    }

    fun shown() {
        count = 0
    }
}

/** The app's [NewDownloads] (VegamApp); a fresh one where none is given (previews, tests). */
val LocalNewDownloads = staticCompositionLocalOf { NewDownloads() }
