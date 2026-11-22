package com.vegamstories.app.presentation

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.reader.ReaderScreenContent
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Comic pages turning by themselves on a phone with its animations off (Remove animations in
 * Accessibility, or 0× in Developer options): each page still waits its full time. A long press
 * pauses that wait.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderAutoTurnTest {

    /** The phone's animation speed as Compose sees it: 0 skips every animation. */
    private object AnimationsOff : MotionDurationScale {
        override val scaleFactor = 0f
    }

    @get:Rule
    val compose = createComposeRule(effectContext = AnimationsOff)

    private val pagesSettled = mutableListOf<Int>()

    private fun reader(state: ReaderUiState) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PreviewTheme {
                ReaderScreenContent(
                    state = state,
                    onBack = {},
                    onPageChanged = { pagesSettled += it },
                    onToggleFavorite = {},
                    onToggleDownload = {},
                    onOpenNextStory = {},
                    openOnCover = false,
                )
            }
        }
    }

    private fun comic(settings: ReaderSettings = ReaderSettings(pageTurnHintShown = true)) = ReaderUiState(
        isLoading = false,
        detail = StoryDetail(
            story = testStory(pageCount = 3),
            pages = List(3) { StoryPage(index = it, imageUrl = "https://example.com/p$it.webp") },
        ),
        settings = settings,
    )

    /** A finger on the right of the page, where a tap would turn it. */
    private fun press() = compose.onRoot().performTouchInput { down(Offset(width * 0.9f, height / 2f)) }
    private fun lift() {
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle() // the reader hears of it on the main thread, which this test's clock doesn't run
    }

    @Test
    fun `a comic page waits the default 60 s before it turns`() {
        reader(comic())

        compose.mainClock.advanceTimeBy(59_000)
        assertEquals(listOf(0), pagesSettled)

        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(listOf(0, 1), pagesSettled)
    }

    @Test
    fun `a long press stops the page turning, and it carries on from there when let go`() {
        reader(comic())
        compose.mainClock.advanceTimeBy(10_000)

        press()
        compose.mainClock.advanceTimeBy(60_000)
        assertEquals(listOf(0), pagesSettled)

        // Letting go isn't a tap: the page stays, with the time it had left (about 50 s).
        lift()
        compose.mainClock.advanceTimeBy(48_000)
        assertEquals(listOf(0), pagesSettled)

        compose.mainClock.advanceTimeBy(4_000)
        assertEquals(listOf(0, 1), pagesSettled)
    }

    @Test
    fun `a long press also holds the page in the vertical strip`() {
        reader(comic(ReaderSettings(pageTurnHintShown = true, scrollMode = ReaderScrollMode.VERTICAL)))
        compose.mainClock.advanceTimeBy(10_000)

        press()
        compose.mainClock.advanceTimeBy(60_000)
        assertEquals(listOf(0), pagesSettled)

        lift()
        compose.mainClock.advanceTimeBy(52_000)
        assertEquals(listOf(0, 1), pagesSettled)
    }

    @Test
    fun `a quick tap still turns the page`() {
        reader(comic())
        compose.mainClock.advanceTimeBy(1_000)

        press()
        compose.mainClock.advanceTimeBy(50)
        lift()
        compose.mainClock.advanceTimeBy(2_000)

        assertEquals(listOf(0, 1), pagesSettled)
    }
}
