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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
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
private val TextInsetX = 26.dp
private val TextInsetTop = 34.dp

/** Room under the text for the page number. */
private val FooterHeight = 40.dp

/**
 * The edges of the book's other pages, showing beside the open one as in a real book: those still to
 * come on the right (and below it), those already read on the left. At most [MAX_EDGES] on a side:
 * the book looks thinner on the right and thicker on the left as it is read.
 */
private const val MAX_EDGES = 6
private val EdgeStepX = 1.4.dp
private val EdgeStepY = 0.9.dp

/** Room kept for the edges on each side, so the page and its text stay put however many show. */
private val EdgeRoomX = EdgeStepX * MAX_EDGES
private val EdgeRoomY = EdgeStepY * MAX_EDGES

/** How far each page sits out of line (dp): a book's edge is never quite straight. */
private val EdgeJitter = floatArrayOf(0.4f, 0f, 0.7f, 0.2f, 0.9f, 0.3f)

/** Where the ribbon hangs from the top of the page: in from its right edge. */
internal val BookRibbonEndPadding = BookPageMargin + EdgeRoomX + 12.dp

/** Space under the title on the first page, around its ornament. */
private val TitleGap = 30.dp

/** The paper and ink of the Read tab's pages: warm, dim paper with black ink; reversed at night. */
@Immutable
data class BookColors(
    val paper: Color,
    val ink: Color,
    val rule: Color,
    val faint: Color,
    /** Night pages: the edges are drawn lighter than the paper and the shadows deeper, so they show on dark. */
    val night: Boolean = false,
)

private val DayBook = BookColors(
    paper = Color(0xFFF1E7D0),
    ink = Color(0xFF1B1712),
    rule = Color(0xFF9C8259),
    faint = Color(0xFF6E5E46),
)
private val NightBook = BookColors(
    // Warm dark-brown paper, a few shades lighter than the dark screen around it, so the page shows
    // as a page; soft warm ink, not glaring white.
    paper = Color(0xFF2A251F),
    ink = Color(0xFFE3D6BC),
    rule = Color(0xFFA88B57),
    faint = Color(0xFFA2927A),
    night = true,
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
            val textWidth = (width - (BookPageMargin + EdgeRoomX + TextInsetX) * 2).roundToPx().coerceAtLeast(1)
            val textHeight = (height - TextInsetTop - FooterHeight - EdgeRoomY).toPx()
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

/** How many page edges show beside the open page: [read] on the left, [toCome] on the right. */
internal data class BookEdges(val read: Int, val toCome: Int)

/**
 * The edges beside page [index] of [pageCount]: the pages still to come on the right, the ones read
 * on the left, in proportion. A one-page story shows the pages under it.
 */
internal fun bookEdges(index: Int, pageCount: Int): BookEdges {
    if (pageCount <= 1) return BookEdges(read = 0, toCome = MAX_EDGES)
    val toCome = Math.round((pageCount - 1 - index).toFloat() / (pageCount - 1) * MAX_EDGES).coerceIn(0, MAX_EDGES)
    return BookEdges(read = MAX_EDGES - toCome, toCome = toCome)
}

/**
 * Page [index] of [book]: its text inside a book page's double-ruled frame, the page darkening
 * toward the spine on the left, its number at the foot, and the book's other pages showing at its
 * edges (see [bookEdges]). The first page opens with the title.
 */
@Composable
fun BookTextPage(book: TextBook, index: Int, modifier: Modifier = Modifier) {
    val colors = book.colors
    val edges = bookEdges(index, book.pages.size)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BookPageMargin)
            .height(book.pageHeight)
            .drawBehind { drawBookPage(colors, edges) },
    ) {
        Column(Modifier.padding(horizontal = EdgeRoomX + TextInsetX).padding(top = TextInsetTop)) {
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
                .padding(horizontal = EdgeRoomX)
                .padding(bottom = EdgeRoomY + FrameInsetY + 10.dp),
        )
    }
}

private fun DrawScope.drawBookPage(colors: BookColors, edges: BookEdges) {
    val stepX = EdgeStepX.toPx()
    val stepY = EdgeStepY.toPx()
    val pageLeft = EdgeRoomX.toPx()
    val pageRight = size.width - EdgeRoomX.toPx()
    val pageBottom = size.height - EdgeRoomY.toPx()
    val hairline = 0.6.dp.toPx()
    val night = colors.night
    val gaps = lerp(colors.paper, Color.Black, if (night) 0.55f else 0.22f) // between the edges
    // At night the edges are lighter than the paper, as page edges catch the light; darker by day.
    fun edgePaper(depth: Int) = if (night) {
        lerp(lerp(colors.paper, Color.White, 0.12f), Color.Black, 0.035f * depth)
    } else {
        lerp(colors.paper, Color.Black, 0.03f + 0.025f * depth)
    }
    val edgeLine = colors.rule.copy(alpha = if (night) 0.55f else 0.35f)
    // Shadows need to be deeper to show on dark paper.
    val depth = if (night) 2.6f else 1f
    fun shade(alpha: Float) = Color.Black.copy(alpha = (alpha * depth).coerceAtMost(0.6f))

    // The pages still to come, under the open one, the deepest first: each reaches a little further
    // right and down than the one on it, and none sits quite square, so they read as a block of pages.
    if (edges.toCome > 0) {
        drawRect(gaps, topLeft = Offset(pageLeft, 0f), size = Size(pageRight - pageLeft + edges.toCome * stepX, pageBottom + edges.toCome * stepY))
        for (i in edges.toCome downTo 1) {
            val jitter = EdgeJitter[i - 1].dp.toPx()
            val top = i * 0.8.dp.toPx() + jitter
            val right = pageRight + i * stepX - jitter
            val bottom = pageBottom + i * stepY - jitter / 2
            drawRect(edgePaper(i), topLeft = Offset(pageLeft, top), size = Size(right - pageLeft, bottom - top))
            drawLine(edgeLine, Offset(right, top), Offset(right, bottom), hairline)
            drawLine(edgeLine, Offset(pageLeft, bottom), Offset(right, bottom), hairline)
        }
    }
    // The pages already read, showing on the left the same way.
    if (edges.read > 0) {
        drawRect(gaps, topLeft = Offset(pageLeft - edges.read * stepX, 0f), size = Size(edges.read * stepX, pageBottom))
        for (i in edges.read downTo 1) {
            val jitter = EdgeJitter[MAX_EDGES - i].dp.toPx()
            val top = i * 0.8.dp.toPx() + jitter
            val left = pageLeft - i * stepX + jitter
            val bottom = pageBottom - jitter / 2
            drawRect(edgePaper(i), topLeft = Offset(left, top), size = Size(pageLeft - left, bottom - top))
            drawLine(edgeLine, Offset(left, top), Offset(left, bottom), hairline)
        }
    }
    // The open page, its shadow falling on the edges around it.
    val shadow = 4.dp.toPx()
    if (edges.toCome > 0) {
        drawRect(
            Brush.horizontalGradient(0f to shade(0.12f), 1f to Color.Transparent, startX = pageRight, endX = pageRight + shadow),
            topLeft = Offset(pageRight, 0f),
            size = Size(shadow, pageBottom),
        )
        drawRect(
            Brush.verticalGradient(0f to shade(0.10f), 1f to Color.Transparent, startY = pageBottom, endY = pageBottom + shadow),
            topLeft = Offset(pageLeft, pageBottom),
            size = Size(pageRight - pageLeft, shadow),
        )
    }
    if (edges.read > 0) {
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 1f to shade(0.12f), startX = pageLeft - shadow, endX = pageLeft),
            topLeft = Offset(pageLeft - shadow, 0f),
            size = Size(shadow, pageBottom),
        )
    }
    drawRect(colors.paper, topLeft = Offset(pageLeft, 0f), size = Size(pageRight - pageLeft, pageBottom))
    // A soft shade along the binding on the left, and a little at the page's outer edge.
    drawRect(
        Brush.horizontalGradient(0f to shade(0.08f), 1f to Color.Transparent, startX = pageLeft, endX = pageLeft + 26.dp.toPx()),
        topLeft = Offset(pageLeft, 0f),
        size = Size(26.dp.toPx(), pageBottom),
    )
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 1f to shade(0.06f), startX = pageRight - 10.dp.toPx(), endX = pageRight),
        topLeft = Offset(pageRight - 10.dp.toPx(), 0f),
        size = Size(10.dp.toPx(), pageBottom),
    )
    // A double rule: a firm outer line, a fine inner one.
    val x = pageLeft + FrameInsetX.toPx()
    val y = FrameInsetY.toPx()
    val width = pageRight - pageLeft - 2 * FrameInsetX.toPx()
    drawRect(
        colors.rule.copy(alpha = 0.7f),
        topLeft = Offset(x, y),
        size = Size(width, pageBottom - 2 * y),
        style = Stroke(1.2.dp.toPx()),
    )
    val inner = 4.dp.toPx()
    drawRect(
        colors.rule.copy(alpha = 0.45f),
        topLeft = Offset(x + inner, y + inner),
        size = Size(width - 2 * inner, pageBottom - 2 * (y + inner)),
        style = Stroke(hairline),
    )
    drawSpiral(pageLeft, pageBottom, night)
}

/** The spiral binding's wire: dark steel by day, lighter at night so it shows on dark paper. */
private val WireDay = Color(0xFF2E2E30)
private val WireNight = Color(0xFF9A9A9E)

/**
 * A spiral binding down the page's left edge, like a notebook: a punched hole every [SpiralStep]
 * and a coil of wire through each, looping out past the edge and back in.
 */
private fun DrawScope.drawSpiral(pageLeft: Float, pageBottom: Float, night: Boolean) {
    val step = SpiralStep.toPx()
    val holeX = pageLeft + 9.dp.toPx()
    val holeRadius = 2.6.dp.toPx()
    val outX = pageLeft - 7.dp.toPx() // how far the coil reaches beyond the edge
    val wire = if (night) WireNight else WireDay
    val shine = Color.White.copy(alpha = if (night) 0.55f else 0.45f)
    val hole = Color.Black.copy(alpha = if (night) 0.65f else 0.5f)
    val wireWidth = 2.dp.toPx()
    val count = ((pageBottom - 2 * SpiralMargin.toPx()) / step).toInt().coerceAtLeast(1)
    val first = (pageBottom - (count - 1) * step) / 2
    val coil = Path()
    for (i in 0 until count) {
        val y = first + i * step
        // The hole, with the paper's lit lower lip.
        drawCircle(hole, holeRadius, Offset(holeX, y))
        drawCircle(Color.White.copy(alpha = 0.12f), holeRadius, Offset(holeX, y + 0.8.dp.toPx()), style = Stroke(0.6.dp.toPx()))
        // Two turns of wire side by side, from the hole over the edge and round behind it.
        for (turn in 0..1) {
            val dy = (turn - 0.5f) * 2.6.dp.toPx()
            coil.reset()
            coil.moveTo(holeX, y + dy)
            coil.cubicTo(holeX - 3.dp.toPx(), y + dy - 6.dp.toPx(), outX, y + dy - 6.dp.toPx(), outX, y + dy - 1.dp.toPx())
            coil.cubicTo(outX, y + dy + 3.dp.toPx(), pageLeft - 2.dp.toPx(), y + dy + 4.dp.toPx(), pageLeft, y + dy + 3.dp.toPx())
            drawPath(coil, Color.Black.copy(alpha = 0.25f), style = Stroke(wireWidth + 1.dp.toPx(), cap = StrokeCap.Round))
            drawPath(coil, wire, style = Stroke(wireWidth, cap = StrokeCap.Round))
            drawPath(coil, shine, style = Stroke(0.6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Space between the spiral's coils, and the paper left clear above the first and below the last. */
private val SpiralStep = 22.dp
private val SpiralMargin = 18.dp
