package com.vegamstories.app.presentation

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.R
import com.vegamstories.app.data.image.readableOffline
import com.vegamstories.app.presentation.offline.NoInternetDialog
import com.vegamstories.app.presentation.offline.NoInternetNotice
import com.vegamstories.app.presentation.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** No internet: which stories still open, and the notice when one doesn't. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoInternetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(id: Int) = context.getString(id)

    private val pages = listOf("p1.webp", "p2.webp", "p3.webp")

    @Test
    fun `a downloaded story opens without internet`() {
        assertTrue(readableOffline(downloaded = true, pageUrls = pages, hasText = false, saved = { false }))
    }

    @Test
    fun `a story read before opens, its pages being saved on the phone`() {
        assertTrue(readableOffline(downloaded = false, pageUrls = pages, hasText = false, saved = { true }))
    }

    @Test
    fun `a story with pages not yet on the phone doesn't`() {
        assertFalse(readableOffline(downloaded = false, pageUrls = pages, hasText = true, saved = { it != "p3.webp" }))
    }

    @Test
    fun `a text-only story opens, the app always keeping the text`() {
        assertTrue(readableOffline(downloaded = false, pageUrls = emptyList(), hasText = true, saved = { false }))
        assertFalse(readableOffline(downloaded = false, pageUrls = emptyList(), hasText = false, saved = { false }))
    }

    @Test
    fun `going offline says so, and how to fix it`() {
        compose.setContent { PreviewTheme { NoInternetDialog(NoInternetNotice.OFFLINE, onDismiss = {}) } }

        compose.onNodeWithText(text(R.string.no_internet_title)).assertExists()
        compose.onNodeWithText(text(R.string.no_internet_message)).assertExists()
        compose.onNodeWithText(text(R.string.no_internet_turn_on)).assertExists()
    }

    @Test
    fun `a story not saved on the phone says why it won't open`() {
        var dismissed = 0
        compose.setContent { PreviewTheme { NoInternetDialog(NoInternetNotice.STORY_NOT_SAVED, onDismiss = { dismissed++ }) } }

        compose.onNodeWithText(text(R.string.no_internet_story_message)).assertExists()
        compose.onNodeWithText(text(R.string.no_internet_ok)).performClick()

        assertEquals(1, dismissed)
    }
}
