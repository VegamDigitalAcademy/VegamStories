package com.vegamstories.app.presentation

import android.content.Context
import com.vegamstories.app.testutil.APP_LANGUAGE
import java.util.Locale
import android.content.res.Configuration
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
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.reader.FastTurns
import com.vegamstories.app.presentation.reader.ReaderScreenContent
import com.vegamstories.app.presentation.reader.ReaderSounds
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.presentation.reader.glueCoverage
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

/** Torn pages: turning too fast tears a page, and it turns again only once glued back. */
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
    fun `glue counts where it touches the tear`() {
        val line = listOf(Offset(0f, 100f), Offset(300f, 100f))

        assertEquals(0f, glueCoverage(line, emptyList(), reach = 20f))
        assertEquals(1f, glueCoverage(line, (0..30).map { Offset(it * 10f, 105f) }, reach = 20f))
        val half = glueCoverage(line, (0..15).map { Offset(it * 10f, 95f) }, reach = 20f)
        assertTrue(half in 0.45f..0.65f)
        assertEquals(0f, glueCoverage(line, (0..30).map { Offset(it * 10f, 300f) }, reach = 20f))
    }

    @Test
    fun `flicking through tears a page, which then stays until glued`() {
        reader()

        flickThrough()

        compose.onNodeWithText(storyText(R.string.tear_hint)).assertExists()
        assertEquals(listOf("tear"), heard)
        val tornAt = pagesSettled.last()
        tapRight(thenWait = 2_000)
        assertEquals(tornAt, pagesSettled.last())
    }

    @Test
    fun `Fix it glues the page, and it turns again`() {
        reader()
        flickThrough()
        val tornAt = pagesSettled.last()

        compose.onNodeWithText(storyText(R.string.tear_fix)).performClick()
        compose.mainClock.advanceTimeBy(2_000)

        compose.onNodeWithText(storyText(R.string.tear_hint)).assertDoesNotExist()
        assertEquals(listOf("tear", "glued"), heard)
        tapRight(thenWait = 2_000)
        assertEquals(tornAt + 1, pagesSettled.last())
    }

    @Test
    fun `the torn page speaks the story's language`() {
        reader(language = "en")

        flickThrough()

        compose.onNodeWithText("Too fast! The page tore. Rub glue along the tear to fix it").assertExists()
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
    fun `it's off unless switched on in Settings`() {
        assertFalse(ReaderSettings().tornPages)
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

        assertEquals(listOf(true), changes)
    }
}
