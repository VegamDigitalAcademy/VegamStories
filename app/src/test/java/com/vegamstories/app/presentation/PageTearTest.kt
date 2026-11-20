package com.vegamstories.app.presentation

import android.content.Context
import com.vegamstories.app.testutil.APP_LANGUAGE
import java.util.Locale
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.AppLanguage
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.reader.FastTurns
import com.vegamstories.app.presentation.reader.ReaderScreenContent
import com.vegamstories.app.presentation.reader.ReaderSounds
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.presentation.reader.pointAt
import com.vegamstories.app.presentation.reader.pouredRange
import com.vegamstories.app.presentation.reader.rubbedAfter
import com.vegamstories.app.presentation.reader.tearLine
import com.vegamstories.app.presentation.settings.SettingsContent
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Torn pages: turning too fast tears a page; the glue bottle pours glue along the tear, it's rubbed
 * in (or Fix it), and the page turns again only once it's whole.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageTearTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(id: Int) = context.getString(id)

    /** The torn page speaks the story's language, whatever the app's is. */
    private fun storyText(id: Int, language: String = APP_LANGUAGE): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(config).getString(id)
    }

    private val pagesSettled = mutableListOf<Int>()
    private val heard = mutableListOf<String>()

    private fun reader(tornPages: Boolean = true, language: String = APP_LANGUAGE) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PreviewTheme {
                ReaderScreenContent(
                    state = ReaderUiState(
                        isLoading = false,
                        detail = StoryDetail(
                            story = testStory(pageCount = 8).copy(language = language),
                            pages = List(8) { StoryPage(index = it, imageUrl = "https://example.com/p$it.webp") },
                        ),
                        settings = ReaderSettings(autoTurnPages = false, pageTurnHintShown = true, tornPages = tornPages),
                    ),
                    onBack = {},
                    onPageChanged = { pagesSettled += it },
                    onToggleFavorite = {},
                    onToggleDownload = {},
                    onOpenNextStory = {},
                    openOnCover = false,
                    sounds = object : ReaderSounds {
                        override fun pageTurn() = Unit
                        override fun tear() { heard += "tear" }
                        override fun glued() { heard += "glued" }
                    },
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun tapRight(thenWait: Long) {
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.mainClock.advanceTimeBy(thenWait)
    }

    /** Four quick taps: three fast turns after the first, and the fourth page tears. */
    private fun flickThrough() {
        repeat(4) { tapRight(thenWait = 300) }
        compose.mainClock.advanceTimeBy(2_000)
    }

    @Test
    fun `three fast turns in a row tear the next page`() {
        val turns = FastTurns()
        assertEquals(listOf(false, false, false, true), (0..3).map { turns.turned(it * 300L) })
    }

    @Test
    fun `a turn within one and a half seconds of the last is fast`() {
        val turns = FastTurns()
        assertEquals(listOf(false, false, false, true), (0..3).map { turns.turned(it * 1_400L) })
    }

    @Test
    fun `a slow turn starts the count again`() {
        val turns = FastTurns()
        listOf(0L, 300, 600).forEach { turns.turned(it) }

        assertFalse(turns.turned(3_000))
        assertFalse(turns.turned(3_300))
        assertFalse(turns.turned(5_000))
        assertFalse(turns.turned(6_600)) // 1.6 s after: slow, starts again
    }

    @Test
    fun `each tear runs right across the page, about halfway down`() {
        val line = tearLine(seed = 42)

        assertEquals(0f, line.first().x)
        assertEquals(1f, line.last().x)
        assertTrue(line.all { it.y in 0.3f..0.65f })
        assertEquals(line, tearLine(seed = 42))
    }

    @Test
    fun `rubbing fills the stretch of tear under the finger, a bit each pass`() {
        // Three stretches, their middles at x = 50, 150 and 250.
        val line = listOf(Offset(0f, 100f), Offset(300f, 100f))
        val fresh = listOf(0f, 0f, 0f)

        assertEquals(listOf(0f, 0.5f, 0f), rubbedAfter(fresh, line, Offset(150f, 105f), moved = 30f, reach = 20f, fillDistance = 60f))
        assertEquals(listOf(0f, 1f, 0f), rubbedAfter(listOf(0f, 0.9f, 0f), line, Offset(150f, 100f), moved = 30f, reach = 20f, fillDistance = 60f))
        assertEquals(fresh, rubbedAfter(fresh, line, Offset(150f, 160f), moved = 30f, reach = 20f, fillDistance = 60f))
    }

    @Test
    fun `glue dropped on the tear runs from there to both ends, reaching them together`() {
        assertEquals(0.3f..0.3f, pouredRange(from = 0.3f, poured = 0f))
        val half = pouredRange(from = 0.3f, poured = 0.5f)
        assertEquals(0.15f, half.start, 0.001f)
        assertEquals(0.65f, half.endInclusive, 0.001f)
        assertEquals(0f..1f, pouredRange(from = 0.3f, poured = 1f))
    }

    @Test
    fun `points along the tear follow its jags`() {
        val line = listOf(Offset(0f, 0f), Offset(100f, 50f), Offset(200f, 0f))

        assertEquals(Offset(0f, 0f), pointAt(line, 0f))
        assertEquals(Offset(100f, 50f), pointAt(line, 0.5f))
        assertEquals(Offset(150f, 25f), pointAt(line, 0.75f))
        assertEquals(Offset(200f, 0f), pointAt(line, 1f))
    }

    @Test
    fun `flicking through tears a page, which then stays until glued`() {
        reader()

        flickThrough()

        compose.onNodeWithText(storyText(R.string.tear_hint)).assertExists()
        compose.onNodeWithText(storyText(R.string.tear_tap_glue)).assertExists()
        assertEquals(listOf("tear"), heard)
        val tornAt = pagesSettled.last()
        tapRight(thenWait = 2_000)
        assertEquals(tornAt, pagesSettled.last())
    }

    @Test
    fun `tapping the glue bottle pours glue along the tear, then asks to rub it in`() {
        reader()
        flickThrough()

        compose.onNodeWithContentDescription(storyText(R.string.tear_glue_bottle)).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(storyText(R.string.tear_tap_glue)).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(3_000)

        compose.onNodeWithText(storyText(R.string.tear_rub_glue)).assertExists()
        compose.onNodeWithText("0%").assertExists()
    }

    @Test
    fun `Fix it glues the page, and it turns again`() {
        reader()
        flickThrough()
        val tornAt = pagesSettled.last()

        compose.onNodeWithText(storyText(R.string.tear_fix)).performClick()
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText(storyText(R.string.tear_fixed)).assertExists()
        assertEquals(listOf("tear", "glued"), heard)
        tapRight(thenWait = 500)
        assertEquals(tornAt, pagesSettled.last()) // still showing Page fixed

        compose.onNodeWithText(storyText(R.string.tear_read_story)).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(storyText(R.string.tear_hint)).assertDoesNotExist()
        tapRight(thenWait = 2_000)
        assertEquals(tornAt + 1, pagesSettled.last())
    }

    @Test
    fun `Page fixed goes by itself after 3 seconds, and the page turns again`() {
        reader()
        flickThrough()
        val tornAt = pagesSettled.last()

        compose.onNodeWithText(storyText(R.string.tear_fix)).performClick()
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithText(storyText(R.string.tear_fixed)).assertExists()

        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(storyText(R.string.tear_fixed)).assertDoesNotExist()
        tapRight(thenWait = 2_000)
        assertEquals(tornAt + 1, pagesSettled.last())
    }

    @Test
    fun `the torn page speaks the story's language`() {
        reader(language = "en")

        flickThrough()

        compose.onNodeWithText("Too fast! The page tore.").assertExists()
        compose.onNodeWithText("Drag the glue bottle onto the tear!").assertExists()
        assertTrue(storyText(R.string.tear_hint, "te") != storyText(R.string.tear_hint, "en"))
    }

    @Test
    fun `with torn pages off, pages never tear`() {
        reader(tornPages = false)

        flickThrough()

        compose.onNodeWithText(storyText(R.string.tear_hint)).assertDoesNotExist()
        assertEquals(emptyList<String>(), heard)
    }

    @Test
    fun `it's on unless switched off in Settings`() {
        assertTrue(ReaderSettings().tornPages)
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
                    onTornPagesChange = { changes += it },
                )
            }
        }

        compose.onNodeWithText(text(R.string.settings_torn_pages)).performScrollTo().performClick()

        assertEquals(listOf(false), changes)
    }
}
