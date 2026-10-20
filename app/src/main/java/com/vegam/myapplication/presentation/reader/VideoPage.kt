package com.vegam.myapplication.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegam.myapplication.R
import com.vegam.myapplication.domain.model.StoryVideo
import com.vegam.myapplication.domain.model.youTubeVideoId
import com.vegam.myapplication.presentation.components.StoryImage
import com.vegam.myapplication.presentation.components.YouTubePlayer
import com.vegam.myapplication.presentation.preview.ComponentPreviews
import com.vegam.myapplication.presentation.preview.PreviewData
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.ui.theme.YouTubeRed

/**
 * Last page of the reader: the story's animated video.
 * A single-video YouTube link plays right here; any other link (channel, playlist) shows the
 * thumbnail and opens YouTube. [onWatch] opens the link outside the app.
 */
@Composable
fun VideoPage(video: StoryVideo, onWatch: () -> Unit, modifier: Modifier = Modifier) {
    val videoId = remember(video.videoUrl) { video.youTubeVideoId }
    // Previews can't run a WebView, so they show the thumbnail version.
    val playInApp = videoId != null && !LocalInspectionMode.current

    Column(modifier = modifier) {
        if (playInApp && videoId != null) {
            YouTubePlayer(
                videoId = videoId,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            VideoThumbnail(video = video, onClick = onWatch)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.video_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = video.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (playInApp) {
            // Video already plays above; this is just an extra way to open the YouTube app.
            TextButton(onClick = onWatch) {
                Text(stringResource(R.string.open_in_youtube))
            }
        } else {
            Button(
                onClick = onWatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.watch_on_youtube))
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

/** Thumbnail + red play badge; tapping opens the link in YouTube. */
@Composable
private fun VideoThumbnail(video: StoryVideo, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StoryImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(YouTubeRed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.cd_play_video),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun VideoPagePreview() = PreviewTheme {
    VideoPage(video = PreviewData.video, onWatch = {}, modifier = Modifier.padding(16.dp))
}

@ComponentPreviews
@Composable
private fun VideoPageChannelLinkPreview() = PreviewTheme {
    // Not a single video → thumbnail + "Watch on YouTube" button.
    VideoPage(
        video = PreviewData.video.copy(videoUrl = "https://www.youtube.com/@VegamStories"),
        onWatch = {},
        modifier = Modifier.padding(16.dp),
    )
}
