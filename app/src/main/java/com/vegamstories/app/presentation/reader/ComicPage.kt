package com.vegamstories.app.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.presentation.components.ShimmerBox
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme

/**
 * One finished comic page. The image already contains the panels, captions and speech bubbles,
 * so the app shows it as-is: full width, at its own height (nothing is cropped).
 * While it loads, a portrait-shaped shimmer keeps the space.
 * [framed] draws the black comic border; the paged reader turns it off so the image can use
 * every pixel of the screen width.
 */
@Composable
fun ComicPage(page: StoryPage, modifier: Modifier = Modifier, framed: Boolean = true) {
    var state by remember(page.imageUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val loaded = state is AsyncImagePainter.State.Success

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (framed) Modifier.border(2.dp, Color.Black) else Modifier)
            .background(Color.White),
    ) {
        AsyncImage(
            model = page.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            onState = { state = it },
            // Until the image has a size of its own, reserve a typical comic-page shape (3:4).
            modifier = if (loaded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(3f / 4f),
        )
        when (state) {
            is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty ->
                ShimmerBox(Modifier.matchParentSize())

            is AsyncImagePainter.State.Error -> Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_broken_image),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(36.dp),
                )
            }

            is AsyncImagePainter.State.Success -> Unit
        }
    }
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun ComicPagePreview() = PreviewTheme {
    // Images don't load in previews, so this shows the loading shimmer at page size.
    ComicPage(page = PreviewData.pages[0], modifier = Modifier.padding(16.dp))
}
