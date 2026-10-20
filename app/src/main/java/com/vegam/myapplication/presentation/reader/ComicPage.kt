package com.vegam.myapplication.presentation.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegam.myapplication.domain.model.Panel
import com.vegam.myapplication.domain.model.StoryPage
import com.vegam.myapplication.presentation.components.StoryImage
import com.vegam.myapplication.presentation.preview.ComponentPreviews
import com.vegam.myapplication.presentation.preview.PreviewData
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.ui.theme.ComicCaption

private val PanelBorder = 2.5.dp
private val Gutter = 6.dp
private val ActivePanelBorder = 4.dp

/**
 * Lays a page's panels out like a comic strip: rows of 1 or 2 panels.
 * When the server sends ready-made page images instead, this can become a single image.
 */
@Composable
fun ComicPage(
    page: StoryPage,
    modifier: Modifier = Modifier,
    /** Panel being read aloud right now; it gets a red outline. */
    activePanelIndex: Int? = null,
) {
    val rows = rowsFor(page.panels.size)
    Column(
        modifier = modifier
            .background(Color.White)
            .border(PanelBorder, Color.Black)
            .padding(Gutter),
        verticalArrangement = Arrangement.spacedBy(Gutter),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            // Single-panel rows are taller "establishing shots".
            val height: Dp = if (row.size == 1) 190.dp else 150.dp
            Row(
                horizontalArrangement = Arrangement.spacedBy(Gutter),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEachIndexed { i, panelIndex ->
                    ComicPanel(
                        panel = page.panels[panelIndex],
                        bubbleOnRight = (rowIndex + i) % 2 == 0,
                        isActive = panelIndex == activePanelIndex,
                        modifier = Modifier
                            .weight(1f)
                            .height(height),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComicPanel(
    panel: Panel,
    bubbleOnRight: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Black,
        label = "panelBorder",
    )
    Box(modifier = modifier.border(if (isActive) ActivePanelBorder else PanelBorder, borderColor)) {
        StoryImage(
            model = panel.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        panel.caption?.let { caption ->
            Text(
                text = caption,
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(PanelBorder)
                    .background(ComicCaption)
                    .border(1.5.dp, Color.Black)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (panel.dialogues.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(if (bubbleOnRight) Alignment.TopEnd else Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = if (panel.caption != null && bubbleOnRight) 26.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                panel.dialogues.forEach { line -> SpeechBubble(line) }
            }
        }
    }
}

@Composable
private fun SpeechBubble(text: String) {
    Text(
        text = text,
        color = Color.Black,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .widthIn(max = 170.dp)
            .background(Color.White, RoundedCornerShape(50))
            .border(1.5.dp, Color.Black, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Panel indices grouped into rows. 1 → [1]; 2 → [1,1]; 3 → [1,2]; 4 → [1,2,1]; 5 → [2,1,2]; more → pairs. */
private fun rowsFor(count: Int): List<List<Int>> {
    val pattern = when (count) {
        0 -> emptyList()
        1 -> listOf(1)
        2 -> listOf(1, 1)
        3 -> listOf(1, 2)
        4 -> listOf(1, 2, 1)
        5 -> listOf(2, 1, 2)
        else -> List((count + 1) / 2) { 2 }
    }
    val rows = mutableListOf<List<Int>>()
    var start = 0
    for (size in pattern) {
        if (start >= count) break
        val end = (start + size).coerceAtMost(count)
        rows += (start until end).toList()
        start = end
    }
    return rows
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun ComicPagePreview() = PreviewTheme {
    ComicPage(page = PreviewData.pages[0], modifier = Modifier.padding(16.dp))
}

@ComponentPreviews
@Composable
private fun ComicPageThreePanelsPreview() = PreviewTheme {
    ComicPage(page = PreviewData.pages[1], modifier = Modifier.padding(16.dp))
}

@ComponentPreviews
@Composable
private fun ComicPageReadingAloudPreview() = PreviewTheme {
    ComicPage(page = PreviewData.pages[0], activePanelIndex = 1, modifier = Modifier.padding(16.dp))
}
