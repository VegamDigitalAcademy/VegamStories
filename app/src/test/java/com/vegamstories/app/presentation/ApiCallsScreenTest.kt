package com.vegamstories.app.presentation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.R
import com.vegamstories.app.core.logging.ApiCallCount
import com.vegamstories.app.domain.model.AppLanguage
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.settings.ApiCallsContent
import com.vegamstories.app.presentation.settings.SettingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/** Settings → API calls (debug builds): each API's last call and count, the total at the bottom. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApiCallsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(@StringRes id: Int) = context.getString(id)

    /** 19 Oct 2026 at 19:15:20 on the phone's clock. */
    private val oct19 = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 19, 19, 15, 20) }.timeInMillis

    private var resets = 0
    private var apiCallsOpened = 0

    private fun show(counts: List<ApiCallCount>) = compose.setContent {
        PreviewTheme { ApiCallsContent(counts = counts, onBack = {}, onReset = { resets++ }) }
    }

    private fun showSettings(onOpenApiCalls: (() -> Unit)?) = compose.setContent {
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
                onOpenApiCalls = onOpenApiCalls,
            )
        }
    }

    @Test
    fun `each API shows its last call and count, and the total is at the bottom`() {
        show(listOf(ApiCallCount("getStories", 3, oct19), ApiCallCount("getCategories", 2, oct19 - 60_000)))

        compose.onNodeWithText("getStories").assertIsDisplayed()
        compose.onNodeWithText("19 Oct 2026 19:15:20").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("getCategories").assertIsDisplayed()
        compose.onNodeWithText("19 Oct 2026 19:14:20").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.api_calls_total)).assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun `Reset clears the counts`() {
        show(listOf(ApiCallCount("getStories", 1, oct19)))

        compose.onNodeWithText(text(R.string.api_calls_reset)).performClick()

        assertEquals(1, resets)
    }

    @Test
    fun `with no calls yet it says so, with nothing to reset`() {
        show(emptyList())

        compose.onNodeWithText(text(R.string.api_calls_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.api_calls_reset)).assertDoesNotExist()
    }

    @Test
    fun `debug builds open the page from Settings`() {
        showSettings(onOpenApiCalls = { apiCallsOpened++ })

        compose.onNodeWithText(text(R.string.settings_api_calls)).performScrollTo().performClick()

        assertEquals(1, apiCallsOpened)
    }

    @Test
    fun `release builds have no API calls row in Settings`() {
        showSettings(onOpenApiCalls = null)

        compose.onNodeWithText(text(R.string.settings_developer_section)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_api_calls)).assertDoesNotExist()
    }
}
