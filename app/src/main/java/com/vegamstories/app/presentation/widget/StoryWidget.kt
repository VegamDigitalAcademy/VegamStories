package com.vegamstories.app.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vegamstories.app.MainActivity
import com.vegamstories.app.R
import com.vegamstories.app.presentation.shortcuts.ShortcutTarget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Home screen widget: "Continue reading" and/or today's new story. Tapping a row opens it. */
class StoryWidget : GlanceAppWidget() {

    /** Hilt can't inject into Glance classes, so dependencies come through an entry point. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun storyWidgetData(): StoryWidgetData
    }

    // Short widget: one row. Tall widget: both rows.
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).storyWidgetData()
        val entries = data.load()
        provideContent {
            GlanceTheme { WidgetContent(entries) }
        }
    }

    companion object {
        private val SMALL = DpSize(250.dp, 110.dp)
        private val TALL = DpSize(250.dp, 220.dp)
    }
}

@Composable
private fun WidgetContent(entries: List<WidgetEntry>) {
    val context = LocalContext.current
    val showBoth = LocalSize.current.height >= 200.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (entries.isEmpty()) {
            EmptyContent()
        } else {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val shown = if (showBoth) entries else entries.take(1)
                shown.forEachIndexed { index, entry ->
                    if (index > 0) Spacer(GlanceModifier.height(10.dp))
                    EntryRow(
                        entry = entry,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .clickable(actionStartActivity(ShortcutTarget.Story(entry.story.id).toIntent(context))),
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: WidgetEntry, modifier: GlanceModifier) {
    val context = LocalContext.current
    val story = entry.story
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val cover = entry.cover
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.size(width = 60.dp, height = 80.dp).cornerRadius(10.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(
                    when (entry.kind) {
                        WidgetEntry.Kind.CONTINUE_READING -> R.string.continue_reading
                        WidgetEntry.Kind.NEW_TODAY -> R.string.widget_new_today
                        WidgetEntry.Kind.LATEST -> R.string.widget_latest
                    },
                ),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = story.title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Text(
                text = context.getString(R.string.episode_category, story.episodeNumber, story.categoryName),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            val progress = story.progress
            if (entry.kind == WidgetEntry.Kind.CONTINUE_READING && progress != null) {
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progress.fraction,
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = context.getString(R.string.pages_progress, progress.lastPageIndex + 1, progress.totalPages),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Nothing cached yet (app never opened, or no stories): tap to open the app. */
@Composable
private fun EmptyContent() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = context.getString(R.string.widget_empty),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}
