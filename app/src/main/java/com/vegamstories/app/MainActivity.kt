package com.vegamstories.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.presentation.MainViewModel
import com.vegamstories.app.presentation.VegamApp
import com.vegamstories.app.presentation.shortcuts.AppShortcutsManager
import com.vegamstories.app.presentation.onboarding.StoryLanguageScreen
import com.vegamstories.app.presentation.shortcuts.ShortcutTarget
import com.vegamstories.app.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
/** AppCompatActivity (not ComponentActivity) so the in-app language setting works on Android 7–12. */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var appShortcutsManager: AppShortcutsManager

    /** Pending app-icon shortcut to open; cleared once VegamApp has navigated. */
    private var shortcutTarget by mutableStateOf<ShortcutTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Logo splash: on launch it stays at least SPLASH_MIN_MS, and longer if the saved theme and
        // story-language choice aren't read yet, so the first screen follows with no blank frame.
        // Not when the screen is only recreated (theme/language change): that would freeze it.
        val splashUntil = SystemClock.uptimeMillis() + if (savedInstanceState == null) SPLASH_MIN_MS else 0L
        installSplashScreen().setKeepOnScreenCondition {
            SystemClock.uptimeMillis() < splashUntil ||
                viewModel.themeMode.value == null || viewModel.needsStoryLanguage.value == null
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh launch — after rotation the NavController already restored the screen.
        if (savedInstanceState == null) handleShortcut(intent)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val needsStoryLanguage by viewModel.needsStoryLanguage.collectAsStateWithLifecycle()
            // Wait for the saved choices so a "Dark" user never sees a light flash, and the
            // language screen never flashes up for someone who already chose.
            val mode = themeMode
            val firstLaunch = needsStoryLanguage
            if (mode != null && firstLaunch != null) {
                val darkTheme = when (mode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme() // follows the phone, live
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

                // Status/navigation bar icons must match the app theme, not the phone's.
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                    )
                    onDispose {}
                }

                MyApplicationTheme(darkTheme = darkTheme) {
                    if (firstLaunch) {
                        // Once, before Home. A shortcut tapped meanwhile stays pending and opens after.
                        StoryLanguageScreen()
                    } else {
                        VegamApp(
                            shortcutTarget = shortcutTarget,
                            onShortcutHandled = { shortcutTarget = null },
                        )
                    }
                }
            }
        }
    }

    /** App already open and the user taps a shortcut (singleTop activity). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent?) {
        ShortcutTarget.from(intent)?.let { target ->
            shortcutTarget = target
            appShortcutsManager.reportUsed(target)
        }
    }

    private companion object {
        /** The logo splash stays at least this long. */
        const val SPLASH_MIN_MS = 2_000L

        // Same scrims the platform uses for 3-button navigation bars.
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
