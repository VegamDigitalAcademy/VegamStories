package com.vegamstories.app.presentation.reader

import com.vegamstories.app.core.logging.AppLogger
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.components.TitleCard
import com.vegamstories.app.presentation.components.VegamLogoMark
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val TAG = "MoralCard"

/**
 * The card's own colours, the same in light and dark mode: shared as a picture, it should look
 * the same on every phone. Warm paper and ink, like the Read tab's pages, on the story's book.
 */
private val CardPaper = Color(0xFFF6EDD8)
/** The book's cloth: behind the shared picture, which can't have clear parts (WhatsApp turns them black). */
private val BookCloth = android.graphics.Color.rgb(0x1D, 0x1D, 0x4F)
private val CardInk = Color(0xFF1B1712)
private val CardRule = Color(0xFF9C8259)
private val CardFaint = Color(0xFF6E5E46)

/**
 * The end-of-story card: the story's book (the reader's cover) with a page set into the panel on
 * its front: the cover picture, the title and the story's [moral] (else its summary), signed
 * Vegam Stories. Made to be shared as a picture (see [shareMoralCard]); [layer] records what it
 * draws for that.
 */
@Composable
fun MoralCard(story: Story, moral: String?, layer: GraphicsLayer, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val book = ImageBitmap.imageResource(R.drawable.book_cover)
    // Just the book, as wide as the page: the bigger it is, the more of the story's words it holds.
    Box(
        modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                // On a dark screen the bright page is toned down; the shared picture keeps its colours.
                if (dim) drawRect(Color.Black.copy(alpha = 0.22f))
            },
    ) {
        // The book, as tall as the page in it needs: its plain middle stretches (drawStretchedBook).
        BoxWithConstraints(Modifier.fillMaxWidth().drawBehind { drawStretchedBook(book) }) {
        val bookHeight = maxWidth * (book.height.toFloat() / book.width)
        Column(
            Modifier
                .fillMaxWidth()
                // Into the panel on the book's front.
                .padding(
                    start = maxWidth * BOOK_PANEL_LEFT + PanelInset,
                    end = maxWidth * (1f - BOOK_PANEL_RIGHT) + PanelInset,
                    top = bookHeight * BOOK_PANEL_TOP + PanelInset,
                    bottom = bookHeight * (1f - BOOK_PANEL_BOTTOM) + PanelInset,
                )
                .clip(RoundedCornerShape(6.dp))
                .background(CardPaper)
                .border(1.dp, CardRule.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardCover(story)
            Column(
                Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = story.title,
                    color = CardInk,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                if (moral != null) {
                    val label = storyString(R.string.moral_label, story.language)
                    // Spaced capitals in Latin script; Telugu and Odia letters are set as written.
                    val latin = label.all { it.code < 0x250 }
                    Text(
                        text = if (latin) label.uppercase() else label,
                        color = CardRule,
                        fontSize = if (latin) 11.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (latin) 1.5.sp else 0.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "“$moral”",
                        color = CardInk,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = story.summary,
                        color = CardInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                CardSignature(story.language)
            }
        }
        }
    }
}

/** Room between the book's panel and the page set into it. */
private val PanelInset = 4.dp

/**
 * The whole cover at the top of the card, as drawn (fitted, never cropped, nothing laid over it),
 * no taller than [CoverMaxHeight]; a title card if there is none.
 */
@Composable
private fun CardCover(story: Story) {
    val cover = story.coverImageUrl
    val shape = RoundedCornerShape(10.dp)
    val modifier = Modifier
        .padding(start = 12.dp, end = 12.dp, top = 12.dp)
        .fillMaxWidth()
    if (cover != null) {
        AsyncImage(
            // A software bitmap: the card is copied into a picture for sharing, and older Android
            // can't copy hardware bitmaps.
            model = ImageRequest.Builder(LocalContext.current).data(cover).allowHardware(false).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .heightIn(max = CoverMaxHeight)
                .clip(shape),
        )
    } else {
        TitleCard(title = story.title, categoryId = story.categoryId, modifier = modifier.height(140.dp).clip(shape))
    }
}

/** Keeps the cover from taking most of the page: the words matter more. */
private val CoverMaxHeight = 220.dp

/** The logo and the app's name, so whoever gets the picture knows where the story is from. */
@Composable
private fun CardSignature(language: String) {
    // The tagline on a line of its own: the page in the book is narrow.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            VegamLogoMark(size = 24)
            Spacer(Modifier.width(8.dp))
            Text(
                text = storyString(R.string.app_name, language),
                color = CardInk,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        Text(
            text = storyString(R.string.share_card_tagline, language),
            color = CardFaint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shares the card drawn in [card] as a picture, with a line of text and the app's Play Store link,
 * through the share sheet (WhatsApp, etc.). Shares just the text if the picture can't be made.
 */
suspend fun shareMoralCard(context: Context, card: GraphicsLayer, story: Story, moral: String?) {
    val picture = try {
        val bitmap = card.toImageBitmap().asAndroidBitmap()
        withContext(Dispatchers.IO) { saveForSharing(context, bitmap, story.id) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        shareLog.w("$TAG: Couldn't make the story card picture; sharing the text only", e)
        null
    }
    val link = "https://play.google.com/store/apps/details?id=${context.packageName}"
    val words = context.inLanguage(story.language)
    val text = if (moral != null) {
        words.getString(R.string.share_text_moral, moral, story.title, link)
    } else {
        words.getString(R.string.share_text_story, story.title, link)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (picture != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, picture)
            clipData = ClipData.newRawUri(null, picture)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    shareLog.i("$TAG: User: share story card ${story.id} (${if (picture != null) "picture and text" else "text only"})")
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_story_title)))
}

/**
 * The app's words in [language] (a story's), where the app has them, else its default: the card
 * and its message read in the story's language, whatever language the app is in.
 */
private fun Context.inLanguage(language: String): Context {
    val config = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
    return createConfigurationContext(config)
}

@Composable
private fun storyString(@StringRes id: Int, language: String): String {
    val context = LocalContext.current
    return remember(context, id, language) { context.inLanguage(language).getString(id) }
}

private val shareLog = AppLogger(TAG)

/** Writes [bitmap] to the app's cache (one picture per story, replaced each time) and returns a shareable link to it. */
private fun saveForSharing(context: Context, bitmap: Bitmap, storyId: String): Uri {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "story-${storyId.filter { it.isLetterOrDigit() || it == '-' }}.png")
    val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
    file.outputStream().use { bookOnly(readable).compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * The card as a picture of the book alone: without the soft shadow the book picture has to its
 * right and below, and on the book's own cloth colour where anything is still see-through.
 */
private fun bookOnly(card: Bitmap): Bitmap {
    val scale = card.width / BOOK_PICTURE_WIDTH
    val left = (BOOK_SOLID_LEFT * scale).toInt()
    val top = (BOOK_SOLID_TOP * scale).toInt()
    val right = card.width - (BOOK_SOLID_RIGHT * scale).toInt()
    val bottom = card.height - (BOOK_SOLID_BOTTOM * scale).toInt()
    if (right <= left || bottom <= top) return card
    val out = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).apply {
        drawColor(BookCloth)
        drawBitmap(card, -left.toFloat(), -top.toFloat(), null)
    }
    return out
}

/** The book picture (book_cover) is this wide; its shadow and edges past the cover, in its pixels. */
private const val BOOK_PICTURE_WIDTH = 1260f
private const val BOOK_SOLID_LEFT = 6
private const val BOOK_SOLID_TOP = 12
private const val BOOK_SOLID_RIGHT = 40
private const val BOOK_SOLID_BOTTOM = 38

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun MoralCardPreview() = PreviewTheme {
    MoralCard(
        story = PreviewData.story(index = 1),
        moral = "కలిసి ఉంటే ఏ కష్టమైనా సులభమే.",
        layer = rememberGraphicsLayer(),
        modifier = Modifier.padding(16.dp),
    )
}
