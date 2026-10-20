package com.vegam.myapplication.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vegam.myapplication.R

/**
 * Every network image in the app goes through here:
 *  - loading → shimmer
 *  - failed  → muted "broken image" icon (instead of an empty box)
 *  - success → the image (Coil cross-fades it in)
 */
@Composable
fun StoryImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var state by remember(model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    Box(modifier) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onState = { state = it },
            modifier = Modifier.matchParentSize(),
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
                    modifier = Modifier.size(28.dp),
                )
            }

            is AsyncImagePainter.State.Success -> Unit
        }
    }
}
