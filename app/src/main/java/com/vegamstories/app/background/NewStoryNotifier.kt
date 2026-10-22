package com.vegamstories.app.background

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.shortcuts.ShortcutTarget
import com.vegamstories.app.ui.theme.VegamRed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the "New story" notification. Tapping it opens the story in the reader. */
@Singleton
class NewStoryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) {

    /** Does nothing if notifications are blocked (switched off, or permission not granted on Android 13+). */
    @SuppressLint("MissingPermission") // checked by areNotificationsEnabled(), which covers POST_NOTIFICATIONS
    suspend fun show(story: Story) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)

        val cover = loadCover(story.coverImageUrl)
        val open = ShortcutTarget.Story(story.id).toIntent(context)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            context,
            story.id.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(VegamRed.toArgb())
            .setContentTitle(context.getString(R.string.notification_new_story_title))
            .setContentText(story.title)
            .setSubText(context.getString(R.string.episode_category, story.episodeNumber, story.categoryName))
            .setLargeIcon(cover)
            .apply {
                // Expanded: the cover as a big picture (the small large-icon hides itself then).
                if (cover != null) {
                    setStyle(NotificationCompat.BigPictureStyle().bigPicture(cover).bigLargeIcon(null as Bitmap?))
                }
            }
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            // One notification per story: a second check for the same story replaces it.
            manager.notify(NOTIFICATION_TAG, story.id.hashCode(), notification)
        } catch (e: SecurityException) {
            Timber.w(e, "New-story notification not allowed")
        }
    }

    private suspend fun loadCover(url: String): Bitmap? = try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(COVER_WIDTH_PX, COVER_HEIGHT_PX)
            .allowHardware(false) // notifications are drawn by another process
            .build()
        (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Cover for notification failed to load")
        null
    }

    companion object {
        private const val CHANNEL_ID = "new_stories"
        private const val NOTIFICATION_TAG = "new_story"
        private const val COVER_WIDTH_PX = 360
        private const val COVER_HEIGHT_PX = 480

        /** Safe to call often. Called when the user turns alerts on, so the channel shows up in system settings. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_new_stories),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_new_stories_desc) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
