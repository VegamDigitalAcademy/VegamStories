package com.vegamstories.app.presentation.reader

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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
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
import timber.log.Timber
import java.io.File
import java.util.Locale

/**
 * The card's own colours, the same in light and dark mode: shared as a picture, it should look
 * the same on every phone. Warm paper and ink, like the Read tab's pages.
 */
private val CardPaper = Color(0xFFF6EDD8)
private val CardFrame = Color(0xFFE6D5B0)
private val CardInk = Color(0xFF1B1712)
private val CardRule = Color(0xFF9C8259)
private val CardFaint = Color(0xFF6E5E46)

/**
 * The end-of-story card: the cover, the title and the story's [moral] (else its summary), signed
 * Vegam Stories. Made to be shared as a picture (see [shareMoralCard]); [layer] records what it
 * draws for that.
 */
@Composable
fun MoralCard(story: Story, moral: String?, layer: GraphicsLayer, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            // Rounded on screen only: the picture keeps square, filled corners (WhatsApp turns
            // transparent corners black).
            .clip(RoundedCornerShape(20.dp))
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                // On a dark screen the bright card is toned down; the shared picture keeps its colours.
                if (dim) drawRect(Color.Black.copy(alpha = 0.22f))
            }
            .background(CardFrame)
            .padding(10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardPaper)
                .border(1.dp, CardRule.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
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
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                CardSignature(story.language)
            }
        }
    }
}

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

/** Keeps the card short enough for the heart below it to show on most phones. */
private val CoverMaxHeight = 190.dp

/** The logo and the app's name, so whoever gets the picture knows where the story is from. */
@Composable
private fun CardSignature(language: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        VegamLogoMark(size = 24)
        Spacer(Modifier.width(8.dp))
        Text(
            text = storyString(R.string.app_name, language),
            color = CardInk,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            text = " · " + storyString(R.string.share_card_tagline, language),
            color = CardFaint,
            fontSize = 12.sp,
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
        Timber.w(e, "Couldn't make the story card picture; sharing the text only")
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

/** Writes [bitmap] to the app's cache (one picture per story, replaced each time) and returns a shareable link to it. */
private fun saveForSharing(context: Context, bitmap: Bitmap, storyId: String): Uri {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "story-${storyId.filter { it.isLetterOrDigit() || it == '-' }}.png")
    val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
    file.outputStream().use { readable.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

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
