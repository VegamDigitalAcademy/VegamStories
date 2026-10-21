package com.vegamstories.app.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Plays a YouTube video inside the app (android-youtube-player, YouTube's official IFrame player
 * in a WebView). The video is cued, not auto-played: it starts when the child taps play.
 * The fullscreen button shows the video edge-to-edge in landscape.
 */
@Composable
fun YouTubePlayer(videoId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var fullscreenContent by remember { mutableStateOf<View?>(null) }
    var exitFullscreenAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val playerView = remember(videoId) {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                    fullscreenContent = fullscreenView
                    exitFullscreenAction = exitFullscreen
                }

                override fun onExitFullscreen() {
                    fullscreenContent = null
                    exitFullscreenAction = null
                }
            })
            val options = IFramePlayerOptions.Builder(context)
                .controls(1)
                .fullscreen(1) // show the fullscreen button
                .rel(0) // related videos at the end: only from the same channel
                .ivLoadPolicy(3) // no annotations
                .ccLoadPolicy(1) // captions on when available
                .build()
            initialize(
                object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.cueVideo(videoId, 0f)
                    }
                },
                true, // pause/resume on network loss
                options,
            )
        }
    }

    // Pauses with the app and frees the WebView when the page leaves the screen.
    DisposableEffect(lifecycleOwner, playerView) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }

    AndroidView(factory = { playerView }, modifier = modifier)

    fullscreenContent?.let { view ->
        FullscreenVideo(view = view, onExit = { exitFullscreenAction?.invoke() })
    }
}

/** Full-screen, landscape, system bars hidden. Back or the player's button exits. */
@Composable
private fun FullscreenVideo(view: View, onExit: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose {}
        }
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    (view.parent as? ViewGroup)?.removeView(view)
                    addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                }
            },
            onRelease = { it.removeAllViews() },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        )
    }
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
