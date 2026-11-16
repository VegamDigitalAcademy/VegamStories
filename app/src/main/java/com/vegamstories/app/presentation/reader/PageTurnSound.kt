package com.vegamstories.app.presentation.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.vegamstories.app.R
import java.util.concurrent.Executors

/** Plays the page-turn sound; the page-by-page reader calls it each time a page passes. */
internal val LocalPageTurnSound = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The rustle of a page turning (Settings → Reading), or nothing when it's [enabled] off. It plays
 * at the phone's media volume, and never while the phone is on silent or vibrate.
 */
@Composable
internal fun rememberPageTurnSound(enabled: Boolean): () -> Unit {
    val context = LocalContext.current.applicationContext
    val player = remember(enabled) { if (enabled) PageTurnPlayer(context) else null }
    DisposableEffect(player) { onDispose { player?.release() } }
    return remember(player) { { player?.play() } }
}

/** How one page's sound plays: quicker and lighter when pages are turned one after another. */
internal data class PageTurnPlayback(val rate: Float, val volume: Float)

/**
 * A turn [sinceLastMillis] after the one before: a slow turn gets the full, unhurried rustle; turning
 * pages fast, like flicking through a book, gets quicker, lighter ones that keep up. null when it's
 * too soon to be a turn of its own (the pager passing several pages in one jump): no extra sound.
 */
internal fun pageTurnPlayback(sinceLastMillis: Long): PageTurnPlayback? {
    if (sinceLastMillis < SAME_TURN_MILLIS) return null
    val quick = ((SLOW_TURN_MILLIS - sinceLastMillis).toFloat() / (SLOW_TURN_MILLIS - FAST_TURN_MILLIS)).coerceIn(0f, 1f)
    return PageTurnPlayback(rate = 1f + MAX_SPEED_UP * quick, volume = VOLUME - VOLUME_DROP * quick)
}

private class PageTurnPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder()
        // Fast turns overlap: each rustle fades out under the next.
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val sound = pool.load(context, R.raw.page_turn, 1)

    /** The sound plays from its own thread: a page turn never waits for it. */
    private val worker = Executors.newSingleThreadExecutor()

    private var lastTurnAt = -SLOW_TURN_MILLIS

    fun play() {
        val now = SystemClock.uptimeMillis()
        val playback = pageTurnPlayback(now - lastTurnAt) ?: return
        lastTurnAt = now
        worker.execute {
            if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return@execute
            pool.play(sound, playback.volume, playback.volume, 1, 0, playback.rate)
        }
    }

    fun release() {
        worker.execute { pool.release() }
        worker.shutdown()
    }
}

/** Turns closer together than this are one jump, with one sound. */
private const val SAME_TURN_MILLIS = 90L

/** A turn this soon after the last plays fastest; this long after or more, at its natural pace. */
private const val FAST_TURN_MILLIS = 250L
private const val SLOW_TURN_MILLIS = 900L

/** Soft: a page sound, not a notification. */
private const val VOLUME = 0.5f
private const val VOLUME_DROP = 0.15f

/** At the fastest, the rustle plays this much quicker (1.6×). */
private const val MAX_SPEED_UP = 0.6f
