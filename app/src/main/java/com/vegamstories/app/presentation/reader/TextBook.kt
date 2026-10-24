package com.vegamstories.app.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vegamstories.app.domain.model.ReaderTextSize

/**
 * Room between the book page and the screen around it, so the page shows as a sheet (the reader's
 * background, not the paper, shows there; it stays put when the page curls).
 */
internal val BookPageMargin = 2.dp

/** The page's frame (a double rule, as in a printed book), in from the page's edges. */
private val FrameInsetX = 14.dp
private val FrameInsetY = 10.dp

/** Where the text sits inside the page. */
private val TextInsetX = 30.dp
private val TextInsetTop = 34.dp

/** Room under the text for the page number. */
private val FooterHeight = 40.dp

/** The edges of the pages under the open one, showing at its right and bottom as in a real book. */
private const val UNDER_PAGES = 6
private val PageStackRight = 8.dp
private val PageStackBottom = 5.dp

/** How far each page underneath sits out of line (dp): a book's edge is never quite straight. */
private val StackJitter = floatArrayOf(0.4f, 0f, 0.7f, 0.2f, 0.9f, 0.3f)

/** Space under the title on the first page, around its ornament. */
private val TitleGap = 30.dp

/** The paper and ink of the Read tab's pages: warm, dim paper with black ink; reversed at night. */
@Immutable
data class BookColors(val paper: Color, val ink: Color, val rule: Color, val faint: Color)

private val DayBook = BookColors(
    paper = Color(0xFFF1E7D0),
    ink = Color(0xFF1B1712),
    rule = Color(0xFF9C8259),
    faint = Color(0xFF6E5E46),
)
private val NightBook = BookColors(
    paper = Color(0xFF1D1B17),
    ink = Color(0xFFE8DFCB),
    rule = Color(0xFF8C7752),
    faint = Color(0xFFA8987A),
)

/** Night pages when the reader is dark (dark app theme, or the Dark page background). */
@Composable
fun bookColors(): BookColors = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) NightBook else DayBook

/** The body text: a serif book face, justified, with room between lines for Telugu's vowel signs. */
private fun bodyStyle(textSize: ReaderTextSize, ink: Color): TextStyle {
    val size = textSize.fontSizeSp.sp
    return TextStyle(
        color = ink,
        fontFamily = FontFamily.Serif,
        fontSize = size,
        lineHeight = size * 1.6f,
        textAlign = TextAlign.Justify,
        // Fixed, untrimmed lines and greedy line breaking: a paragraph split across two pages
        // wraps the same as it was measured, and every line is exactly one line height.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        lineBreak = LineBreak.Simple,
        hyphens = Hyphens.None,
        textIndent = TextIndent(firstLine = 1.6.em),
    )
}

private fun titleStyle(textSize: ReaderTextSize, ink: Color): TextStyle {
    val size = (textSize.fontSizeSp * 1.35f).sp
    return TextStyle(
        color = ink,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size * 1.35f,
        textAlign = TextAlign.Center,
    )
}

/** A run of one paragraph's text, [start] until [end], that sits on one page. */
@Immutable
data class TextSlice(val paragraph: Int, val start: Int, val end: Int)

/** One laid-out line of a paragraph: its text from [start] to [end], from [top] to [bottom] px. */
class BookLine(val start: Int, val end: Int, val top: Float, val bottom: Float)

/**
 * Splits paragraphs, laid out in [lines], into pages [pageHeight] px tall: as many whole lines as
 * fit on each page, a paragraph running on to the next page where it doesn't. [firstPageTop] px of
 * the first page are taken (the title); paragraphs on a page are [gap] px apart. Every page holds
 * at least one line, even one taller than the page, so it always ends.
 */
internal fun paginate(
    lines: List<List<BookLine>>,
    pageHeight: Float,
    firstPageTop: Float,
    gap: Float,
): List<List<TextSlice>> {
    val pages = mutableListOf<List<TextSlice>>()
    var page = mutableListOf<TextSlice>()
    var used = firstPageTop
    fun nextPage() {
        pages += page
        page = mutableListOf()
        used = 0f
    }
    lines.forEachIndexed { paragraph, paragraphLines ->
        var first = 0
        while (first < paragraphLines.size) {
            val spacing = if (page.isEmpty()) 0f else gap
            val top = paragraphLines[first].top
            var end = first
            while (end < paragraphLines.size && used + spacing + paragraphLines[end].bottom - top <= pageHeight) end++
            if (end == first) {
                // Not even one more line fits here: on to a fresh page, unless this one is already empty.
                if (page.isNotEmpty() || used > 0f) {
                    nextPage()
                    continue
                }
                end = first + 1
            }
            page += TextSlice(paragraph, paragraphLines[first].start, paragraphLines[end - 1].end)
            used += spacing + paragraphLines[end - 1].bottom - top
            first = end
            if (first < paragraphLines.size) nextPage()
        }
    }
    if (page.isNotEmpty() || pages.isEmpty()) pages += page
    return pages
}

/** The story laid out as book pages for one page size and text size. */
@Immutable
class TextBook(
    val title: String,
    val paragraphs: List<String>,
    val pages: List<List<TextSlice>>,
    val pageHeight: Dp,
    val body: TextStyle,
    val titleStyle: TextStyle,
    val gap: Dp,
    val colors: BookColors,
) {
    /** The page showing the text at [offset] in [paragraph]. */
    fun pageAt(paragraph: Int, offset: Int = 0): Int {
        var found = 0
        pages.forEachIndexed { i, page ->
            val first = page.firstOrNull() ?: return@forEachIndexed
            if (first.paragraph < paragraph || (first.paragraph == paragraph && first.start <= offset)) found = i
        }
        return found
    }

    /** The last paragraph that is (at least partly) on [page]. */
    fun lastParagraphOn(page: Int): Int = pages.getOrNull(page)?.lastOrNull()?.paragraph ?: -1
}

/**
 * Lays [paragraphs] out as pages for the room each page has in the pager, [width] × [height]: the
 * page itself is [BookPageMargin] narrower on each side (see [BookTextPage] for where the text sits).
 */
@Composable
fun rememberTextBook(title: String, paragraphs: List<String>, textSize: ReaderTextSize, width: Dp, height: Dp): TextBook {
    val colors = bookColors()
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return remember(title, paragraphs, textSize, width, height, colors, density) {
        val body = bodyStyle(textSize, colors.ink)
        val titleStyle = titleStyle(textSize, colors.ink)
        with(density) {
            val textWidth = (width - BookPageMargin * 2 - TextInsetX * 2 - PageStackRight).roundToPx().coerceAtLeast(1)
            val textHeight = (height - TextInsetTop - FooterHeight - PageStackBottom).toPx()
            val gap = (textSize.fontSizeSp * 0.7f).sp.toDp()
            // Laid-out heights are whole pixels: allow for rounding up at every paragraph.
            val slack = 2f
            val constraints = Constraints(maxWidth = textWidth)
            val titleHeight = measurer.measure(title, titleStyle, constraints = constraints).size.height +
                TitleGap.toPx() + slack
            val lines = paragraphs.map { paragraph ->
                measurer.measure(paragraph, body, constraints = constraints).bookLines(slack)
            }
            TextBook(
                title = title,
                paragraphs = paragraphs,
                pages = paginate(lines, pageHeight = textHeight, firstPageTop = titleHeight, gap = gap.toPx() + slack),
                pageHeight = height,
                body = body,
                titleStyle = titleStyle,
                gap = gap,
                colors = colors,
            )
        }
    }
}

private fun TextLayoutResult.bookLines(slack: Float): List<BookLine> = List(lineCount) { i ->
    val last = i == lineCount - 1
    BookLine(
        start = getLineStart(i),
        end = getLineEnd(i),
        top = getLineTop(i),
        bottom = getLineBottom(i) + if (last) slack else 0f,
    )
}

/**
 * Page [index] of [book]: its text inside a book page's double-ruled frame, the page darkening
 * toward the spine on the left, and its number at the foot. The first page opens with the title.
 */
@Composable
fun BookTextPage(book: TextBook, index: Int, modifier: Modifier = Modifier) {
    val colors = book.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BookPageMargin)
            .height(book.pageHeight)
            .drawBehind { drawBookPage(colors) },
    ) {
        Column(Modifier.padding(start = TextInsetX, end = TextInsetX + PageStackRight, top = TextInsetTop)) {
            if (index == 0) {
                Text(book.title, style = book.titleStyle, modifier = Modifier.fillMaxWidth())
                Box(Modifier.fillMaxWidth().height(TitleGap), contentAlignment = Alignment.Center) {
                    Text("❦", color = colors.rule, fontSize = 18.sp, lineHeight = 18.sp)
                }
            }
            book.pages[index].forEachIndexed { i, slice ->
                if (i > 0) Spacer(Modifier.height(book.gap))
                val runsOn = slice.start > 0 // the rest of a paragraph begun on an earlier page
                Text(
                    text = book.paragraphs[slice.paragraph].substring(slice.start, slice.end).trimEnd(),
                    style = if (runsOn) book.body.copy(textIndent = TextIndent.None) else book.body,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            text = "— ${index + 1} —",
            color = colors.faint,
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(end = PageStackRight, bottom = PageStackBottom + FrameInsetY + 10.dp),
        )
    }
}

private fun DrawScope.drawBookPage(colors: BookColors) {
    val pageWidth = size.width - PageStackRight.toPx()
    val pageHeight = size.height - PageStackBottom.toPx()
    val hairline = 0.6.dp.toPx()
    // Beyond the pages: the gaps between their edges.
    drawRect(lerp(colors.paper, Color.Black, 0.22f))
    // The pages underneath, the deepest first: each reaches a little further out than the one on
    // it, and none sits quite square, so the edge reads as a block of pages, not one straight line.
    for (i in UNDER_PAGES downTo 1) {
        val depth = i / UNDER_PAGES.toFloat()
        val jitter = StackJitter[i - 1].dp.toPx()
        val top = i * 0.8.dp.toPx() + jitter
        val right = pageWidth + depth * PageStackRight.toPx() - jitter
        val bottom = pageHeight + depth * PageStackBottom.toPx() - jitter / 2
        drawRect(
            lerp(colors.paper, Color.Black, 0.03f + 0.025f * i),
            topLeft = Offset(0f, top),
            size = Size(right, bottom - top),
        )
        drawLine(colors.rule.copy(alpha = 0.35f), Offset(right, top), Offset(right, bottom), hairline)
        drawLine(colors.rule.copy(alpha = 0.3f), Offset(0f, bottom), Offset(right, bottom), hairline)
    }
    // The open page, its shadow falling on the edges beside and below it.
    val shadow = 4.dp.toPx()
    drawRect(
        Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.12f), 1f to Color.Transparent, startX = pageWidth, endX = pageWidth + shadow),
        topLeft = Offset(pageWidth, 0f),
        size = Size(shadow, pageHeight),
    )
    drawRect(
        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.10f), 1f to Color.Transparent, startY = pageHeight, endY = pageHeight + shadow),
        topLeft = Offset(0f, pageHeight),
        size = Size(pageWidth, shadow),
    )
    drawRect(colors.paper, size = Size(pageWidth, pageHeight))
    // The spine: the page curves into the binding on the left, and a little at its outer edge.
    drawRect(
        Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.14f), 1f to Color.Transparent, startX = 0f, endX = 26.dp.toPx()),
        size = Size(26.dp.toPx(), pageHeight),
    )
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.06f), startX = pageWidth - 10.dp.toPx(), endX = pageWidth),
        topLeft = Offset(pageWidth - 10.dp.toPx(), 0f),
        size = Size(10.dp.toPx(), pageHeight),
    )
    // A double rule: a firm outer line, a fine inner one.
    val x = FrameInsetX.toPx()
    val y = FrameInsetY.toPx()
    drawRect(
        colors.rule.copy(alpha = 0.7f),
        topLeft = Offset(x, y),
        size = Size(pageWidth - 2 * x, pageHeight - 2 * y),
        style = Stroke(1.2.dp.toPx()),
    )
    val inner = 4.dp.toPx()
    drawRect(
        colors.rule.copy(alpha = 0.45f),
        topLeft = Offset(x + inner, y + inner),
        size = Size(pageWidth - 2 * (x + inner), pageHeight - 2 * (y + inner)),
        style = Stroke(hairline),
    )
}
