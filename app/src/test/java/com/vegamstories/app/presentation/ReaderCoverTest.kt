package com.vegamstories.app.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.reader.READER_COVER_TAG
import com.vegamstories.app.presentation.reader.ReaderScreenContent
import com.vegamstories.app.presentation.reader.ReaderSounds
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** A story opens on its cover, which turns by itself to the first page, unless picked up mid-way. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderCoverTest {

    @get:Rule
    val compose = createComposeRule()

    private val story = testStory(pageCount = 4)
    private val pagesSettled = mutableListOf<Int>()
    private var turnSounds = 0

    private fun open(startPage: Int = 0, scrollMode: ReaderScrollMode = ReaderScrollMode.PAGED) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PreviewTheme {
                ReaderScreenContent(
                    state = ReaderUiState(
                        isLoading = false,
                        detail = StoryDetail(
                            story = story,
                            pages = List(4) { StoryPage(index = it, imageUrl = "https://example.com/p$it.webp") },
                        ),
                        initialPage = startPage,
                        settings = ReaderSettings(autoTurnPages = false, pageTurnHintShown = true, scrollMode = scrollMode),
                    ),
                    onBack = {},
                    onPageChanged = { pagesSettled += it },
                    onToggleFavorite = {},
                    onToggleDownload = {},
                    onOpenNextStory = {},
                    sounds = object : ReaderSounds {
                        override fun pageTurn() { turnSounds++ }
                        override fun tear() = Unit
                        override fun glued() = Unit
                    },
                )
            }
        }
    }

    private fun coverShowing() = compose.onAllNodesWithTag(READER_COVER_TAG).fetchSemanticsNodes().any { node ->
        // On the screen, not just built beside it as the next or last page.
        node.boundsInRoot.left in 0f..10f && node.boundsInRoot.width > 0f
    }

    @Test
    fun `a story opened at its start opens on its cover, which turns by itself`() {
        open()

        compose.mainClock.advanceTimeBy(500)
        assertEquals(emptyList<Int>(), pagesSettled)
        assert(coverShowing())

        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(listOf(0), pagesSettled)
        assertEquals(1, turnSounds)
    }

    @Test
    fun `touching the cover keeps it open`() {
        open()
        compose.mainClock.advanceTimeBy(300)

        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(5_000)

        assertEquals(emptyList<Int>(), pagesSettled)
    }

    @Test
    fun `a story picked up mid-way opens on the page it was left on`() {
        open(startPage = 2)

        compose.mainClock.advanceTimeBy(3_000)

        assertEquals(listOf(2), pagesSettled)
        assertEquals(0, turnSounds)
    }

    @Test
    fun `turning back from the first page shows the cover again`() {
        open()
        compose.mainClock.advanceTimeBy(3_000)

        compose.onRoot().performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
        compose.mainClock.advanceTimeBy(2_000)

        assert(coverShowing())
        assertEquals(listOf(0), pagesSettled)
    }

    @Test
    fun `the vertical strip has no cover`() {
        open(scrollMode = ReaderScrollMode.VERTICAL)

        compose.mainClock.advanceTimeBy(3_000)

        assertEquals(listOf(0), pagesSettled)
        assertEquals(0, turnSounds)
    }
}
