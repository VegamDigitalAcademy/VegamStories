package com.vegamstories.app.presentation.reader

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.components.StoryCover
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import kotlin.math.roundToInt

/**
 * The story's cover, the page before its first, as big as the pages after it: a cloth-bound book,
 * with the story's picture from its card filling the panel on its front. The page is taller than
 * the picture, so the picture spans the panel's width and a soft, blurred copy of it fills the
 * panel above and below: full, with nothing cut off. The card's picture grows into it when the
 * story opens (StoryOpeningOverlay): [onPlaced] says where it is, and [pictureAlpha] keeps it
 * hidden until the growing one has landed.
 */
@Composable
internal fun ReaderCover(
    story: Story,
    modifier: Modifier = Modifier,
    pictureAlpha: () -> Float = { 1f },
    onPlaced: ((Rect) -> Unit)? = null,
) {
    val book = ImageBitmap.imageResource(R.drawable.book_cover)
    BoxWithConstraints(modifier.fillMaxSize().testTag(READER_COVER_TAG)) {
        // The book fills the page's width at its own scale; only its plain middle stretches (or
        // shrinks) to the page's height, so the corners and the panel's ends keep their shape.
        val scale = maxWidth / book.width.toFloat() // dp per picture pixel
        val panelTop = scale * (book.height * BOOK_PANEL_TOP)
        val panelBottom = scale * (book.height * (1f - BOOK_PANEL_BOTTOM))
        Canvas(Modifier.matchParentSize()) { drawStretchedBook(book) }
        // The panel on the front, filled: the picture across its width, its blurred copy around it.
        Box(
            Modifier
                .offset(x = maxWidth * BOOK_PANEL_LEFT, y = panelTop)
                .size(width = maxWidth * (BOOK_PANEL_RIGHT - BOOK_PANEL_LEFT), height = maxHeight - panelTop - panelBottom)
                .clip(PanelShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    // Before Android 12 there's no blur: the dimmed, enlarged picture still fills it.
                    .blur(FillBlur, BlurredEdgeTreatment.Rectangle)
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = FILL_DIM))
                    },
            ) {
                StoryCover(story = story, contentDescription = null, modifier = Modifier.matchParentSize())
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .then(if (onPlaced != null) Modifier.onGloballyPositioned { onPlaced(it.boundsInRoot()) } else Modifier)
                    .graphicsLayer { alpha = pictureAlpha() }
                    .shadow(10.dp, PictureShape)
                    .clip(PictureShape),
            ) {
                StoryCover(story = story, modifier = Modifier.matchParentSize())
            }
        }
    }
}

/**
 * The book picture over the whole area: its top and bottom at the width's scale, and the plain
 * band between them stretched to fill the rest of the height.
 */
internal fun DrawScope.drawStretchedBook(book: ImageBitmap) {
    val scale = size.width / book.width
    val topSrc = (book.height * STRETCH_FROM).toInt()
    val bottomSrc = (book.height * (1f - STRETCH_TO)).toInt()
    val midSrc = book.height - topSrc - bottomSrc
    val top = (topSrc * scale).roundToInt()
    val bottom = (bottomSrc * scale).roundToInt()
    val width = size.width.roundToInt()
    val height = size.height.roundToInt()
    val mid = (height - top - bottom).coerceAtLeast(1)
    drawImage(book, IntOffset(0, 0), IntSize(book.width, topSrc), IntOffset(0, 0), IntSize(width, top))
    drawImage(book, IntOffset(0, topSrc), IntSize(book.width, midSrc), IntOffset(0, top), IntSize(width, mid))
    drawImage(book, IntOffset(0, topSrc + midSrc), IntSize(book.width, bottomSrc), IntOffset(0, top + mid), IntSize(width, bottom))
}

/**
 * Whether the phone shows animations (not Remove animations in Accessibility, or 0× in Developer
 * options). Without them the cover doesn't turn by itself: the story just opens on its first page.
 */
@Composable
internal fun animationsOn(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Marks the cover page, for tests. */
const val READER_COVER_TAG = "reader_cover"

/** Where the panel on the book's front is (book_cover), as shares of the picture. Also the share card's. */
internal const val BOOK_PANEL_LEFT = 0.181f
internal const val BOOK_PANEL_RIGHT = 0.835f
internal const val BOOK_PANEL_TOP = 0.0975f
internal const val BOOK_PANEL_BOTTOM = 0.863f

/** The plain band of the book picture that stretches to the page's height (shares of its height). */
private const val STRETCH_FROM = 0.25f
private const val STRETCH_TO = 0.75f

/** The panel's corners, and how soft and how dimmed the picture's copy filling it is. */
private val PanelShape = RoundedCornerShape(6.dp)
private val FillBlur = 24.dp
private const val FILL_DIM = 0.28f

/** The picture's corners in its panel. */
internal val PictureShape = RoundedCornerShape(4.dp)

@ComponentPreviews
@Composable
private fun ReaderCoverPreview() = PreviewTheme {
    ReaderCover(PreviewData.stories[0], Modifier.padding(16.dp))
}
