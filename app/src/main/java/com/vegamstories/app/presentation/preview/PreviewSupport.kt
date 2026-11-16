package com.vegamstories.app.presentation.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.asPainter
import coil3.request.SuccessResult
import com.vegamstories.app.ui.theme.AppBackground
import com.vegamstories.app.ui.theme.MyApplicationTheme

/** Full phone-sized screen, light + dark. Use on screen-level previews. */
@Preview(name = "Light", showBackground = true, device = "spec:width=411dp,height=891dp")
@Preview(
    name = "Dark",
    showBackground = true,
    device = "spec:width=411dp,height=891dp",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ScreenPreviews

/** Phone-sized screen in Telugu (light). Add next to @ScreenPreviews to check translations. */
@Preview(name = "Telugu", showBackground = true, device = "spec:width=411dp,height=891dp", locale = "te")
annotation class TeluguPreview

/** Content-sized, light + dark. Use on component previews. */
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ComponentPreviews

/** Wraps preview content in the app theme on a themed background, with [OfflineImages]. */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides OfflineImages) {
        MyApplicationTheme {
            AppBackground { content() }
        }
    }
}

/**
 * Web images in previews. Coil would otherwise download each one while the preview renders, on
 * Android Studio's render thread, which is slow (and fails offline). Instead each one is a block
 * of colour shaped like the picture (see [previewImage]); anything else loads as usual.
 */
@OptIn(ExperimentalCoilApi::class)
private val OfflineImages = AsyncImagePreviewHandler { imageLoader, request ->
    val url = request.data as? String
    if (url != null && url.startsWith("http")) {
        val image = previewImage(url)
        AsyncImagePainter.State.Success(image.asPainter(request.context), SuccessResult(image, request))
    } else {
        AsyncImagePreviewHandler.Default.handle(imageLoader, request)
    }
}

/** As big as the picture at [url] (PreviewData's addresses end in /width/height), else 3:4. */
private fun previewImage(url: String): ColorImage {
    val size = url.split('/').takeLast(2).mapNotNull { it.toIntOrNull() }
    val (width, height) = if (size.size == 2) size else listOf(600, 800)
    return ColorImage(PreviewImageColors[url.hashCode().mod(PreviewImageColors.size)], width, height)
}

/** Soft colours, so pages and covers next to each other look different. */
private val PreviewImageColors = listOf(0xFFC9DCEB, 0xFFF0DDBE, 0xFFCFE3CF, 0xFFE2D3EA, 0xFFF3CFCF).map { it.toInt() }
