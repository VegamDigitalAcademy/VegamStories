package com.vegamstories.app.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewTheme

/**
 * A story's cover: the cover image, else its first comic page (both chosen in the data layer as
 * [Story.coverImageUrl]), else — or when the picture can't load — a [TitleCard].
 */
@Composable
fun StoryCover(
    story: Story,
    modifier: Modifier = Modifier,
    contentDescription: String? = story.title,
) {
    StoryImage(
        model = story.coverImageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        fallback = { TitleCard(title = story.title, categoryId = story.categoryId, modifier = Modifier.matchParentSize()) },
    )
}

/**
 * A cover drawn by the app, for stories without a picture: the title on a colour from the logo's
 * palette. Each category always gets the same colour, so a series looks like a series.
 * Sizes itself to the space it gets, from list thumbnails to the big cover on the like page.
 */
@Composable
fun TitleCard(title: String, categoryId: String, modifier: Modifier = Modifier) {
    val colors = coverColorsFor(categoryId)
    BoxWithConstraints(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(colors.top, colors.bottom)))
            // The title is read out by the card around it already.
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val width = maxWidth.value
        val fontSize = (width * 0.13f).coerceIn(9f, 22f)
        val showLogo = width >= 90f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding((width * 0.08f).dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = colors.text,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.3f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = if (width < 80f) 3 else 5,
                overflow = TextOverflow.Ellipsis,
            )
            if (showLogo) {
                Spacer(Modifier.height((width * 0.08f).dp))
                Image(
                    painter = painterResource(R.drawable.vegam_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size((width * 0.2f).dp)
                        .clip(RoundedCornerShape((width * 0.05f).dp))
                        .background(LogoNavy)
                        .padding((width * 0.015f).dp),
                )
            }
        }
    }
}

internal data class CoverColors(val top: Color, val bottom: Color, val text: Color)

private val LogoNavy = Color(0xFF1E1B4B)
private val Cream = Color(0xFFFFF7E6)

/** The logo's colours: navy, orange, amber and brown, each with a lighter top for depth. */
private val coverPalette = listOf(
    CoverColors(top = Color(0xFF332F6E), bottom = LogoNavy, text = Cream),
    CoverColors(top = Color(0xFFFBBF46), bottom = Color(0xFFF59E0B), text = LogoNavy),
    CoverColors(top = Color(0xFFD99A3A), bottom = Color(0xFFBF7D1B), text = Cream),
    CoverColors(top = Color(0xFFA87843), bottom = Color(0xFF895D2B), text = Cream),
)

/** Same category → same colour, on every phone and every run (String.hashCode is stable). */
internal fun coverColorsFor(categoryId: String): CoverColors =
    coverPalette[(categoryId.hashCode() and Int.MAX_VALUE) % coverPalette.size]

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun TitleCardPreview() = PreviewTheme {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
        listOf("moral", "tenali", "panchatantra", "folk").forEach { category ->
            TitleCard(
                title = "నిజాయితీ బహుమతి",
                categoryId = category,
                modifier = Modifier
                    .size(width = 120.dp, height = 160.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        TitleCard(
            title = "The Honest Reward",
            categoryId = "moral",
            modifier = Modifier
                .size(width = 56.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}
