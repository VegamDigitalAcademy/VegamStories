package com.vegamstories.app.presentation

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.AppLanguage
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.reader.ReaderScreenContent
import com.vegamstories.app.presentation.reader.ReaderSounds
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.presentation.reader.pageTurnPlayback
import com.vegamstories.app.presentation.settings.SettingsContent
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The paper sound as a page turns (quicker for fast turns), and its switch in Settings → Reading. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageTurnSoundTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var sounds = 0
    private val pagesSettled = mutableListOf<Int>()

    private fun reader(settings: ReaderSettings) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PreviewTheme {
                ReaderScreenContent(
                    state = ReaderUiState(
                        isLoading = false,
                        detail = StoryDetail(
                            story = testStory(pageCount = 3),
                            pages = List(3) { StoryPage(index = it, imageUrl = "https://example.com/p$it.webp") },
                        ),
                        settings = settings,
                    ),
                    onBack = {},
                    onPageChanged = { pagesSettled += it },
                    onToggleFavorite = {},
                    onToggleDownload = {},
                    onOpenNextStory = {},
                    openOnCover = false,
                    sounds = object : ReaderSounds {
                        override fun pageTurn() { sounds++ }
                        override fun tear() = Unit
                        override fun glued() = Unit
                    },
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    /** A tap on the right of the page, which turns it on. */
    private fun tapRight() = compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }

    @Test
    fun `it's on unless switched off`() {
        assertTrue(ReaderSettings().pageTurnSound)
    }

    @Test
    fun `a tap that turns the page plays it once`() {
        reader(ReaderSettings(autoTurnPages = false, pageTurnHintShown = true))

        tapRight()
        compose.mainClock.advanceTimeBy(2_000)

        assertEquals(listOf(0, 1), pagesSettled)
        assertEquals(1, sounds)
    }

    @Test
    fun `tapping quickly through the pages plays one for each page`() {
        reader(ReaderSettings(autoTurnPages = false, pageTurnHintShown = true))

        tapRight()
        compose.mainClock.advanceTimeBy(300)
        tapRight()
        compose.mainClock.advanceTimeBy(2_000)

        assertEquals(listOf(0, 1, 2), pagesSettled)
        assertEquals(2, sounds)
    }

    @Test
    fun `a slow turn gets the full rustle, fast ones quicker and lighter ones`() {
        val slow = pageTurnPlayback(sinceLastMillis = 5_000)!!
        val brisk = pageTurnPlayback(sinceLastMillis = 600)!!
        val fast = pageTurnPlayback(sinceLastMillis = 200)!!

        assertEquals(1f, slow.rate)
        assertEquals(0.25f, slow.volume)
        assertTrue(brisk.rate > slow.rate && fast.rate > brisk.rate)
        assertTrue(fast.volume < slow.volume)
        assertEquals(1.6f, fast.rate, 0.001f)
    }

    @Test
    fun `pages passed in one jump make no extra sound`() {
        assertNull(pageTurnPlayback(sinceLastMillis = 30))
    }

    @Test
    fun `a page turning by itself plays it too`() {
        reader(ReaderSettings(autoTurnSeconds = 10, pageTurnHintShown = true))

        compose.mainClock.advanceTimeBy(11_000)

        assertEquals(listOf(0, 1), pagesSettled)
        assertEquals(1, sounds)
    }

    @Test
    fun `opening the story makes no sound`() {
        reader(ReaderSettings(autoTurnPages = false, pageTurnHintShown = true))

        assertEquals(0, sounds)
    }

    @Test
    fun `the vertical strip has none`() {
        reader(ReaderSettings(autoTurnSeconds = 10, pageTurnHintShown = true, scrollMode = ReaderScrollMode.VERTICAL))

        compose.mainClock.advanceTimeBy(11_000)

        assertEquals(0, sounds)
    }

    @Test
    fun `Settings turns it off`() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            PreviewTheme {
                SettingsContent(
                    themeMode = ThemeMode.SYSTEM,
                    language = AppLanguage.SYSTEM,
                    newStoryAlerts = false,
                    notificationsBlocked = false,
                    onThemeModeSelected = {},
                    onLanguageSelected = {},
                    onNewStoryAlertsChange = {},
                    onOpenNotificationSettings = {},
                    onBack = {},
                    pageTurnSound = true,
                    onPageTurnSoundChange = { changes += it },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_page_turn_sound)).performScrollTo().performClick()

        assertEquals(listOf(false), changes)
    }
}
