package com.vegamstories.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------- Server content (cache; replaced on every refresh) ----------
// A story is shared by all languages (category, episode, date, likes); its title, cover, pages,
// text and video exist once per language (story_translations, story_pages). Category names too.

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val sortOrder: Int,
)

/** A category's name in one language, e.g. ("moral", "te", "నీతి కథలు"). */
@Entity(
    tableName = "category_names",
    primaryKeys = ["categoryId", "language"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CategoryNameEntity(
    val categoryId: String,
    val language: String,
    val name: String,
)

@Entity(
    tableName = "stories",
    indices = [Index("categoryId"), Index("publishedAt")],
)
data class StoryEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val episodeNumber: Int,
    val publishedAt: Long,
    /** Total likes from the server; bumped locally right away when this device likes/unlikes. */
    @ColumnInfo(defaultValue = "0") val likeCount: Int,
)

/** One language version of a story. The app shows a story in a language only if it has this row. */
@Entity(
    tableName = "story_translations",
    primaryKeys = ["storyId", "language"],
    indices = [Index("language")],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StoryTranslationEntity(
    val storyId: String,
    val language: String,
    val title: String,
    val summary: String,
    /** Optional: without it the app shows the first comic page, or a title card. */
    val coverImageUrl: String?,
    /** Comic pages in this language (rows in story_pages). */
    val pageCount: Int,
    /** The story as plain text (paragraphs separated by a blank line); null = no text version. */
    val body: String?,
    val videoTitle: String?,
    val videoDescription: String?,
    val videoThumbnailUrl: String?,
    val videoUrl: String?,
)

@Entity(
    tableName = "story_pages",
    primaryKeys = ["storyId", "language", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StoryPageEntity(
    val storyId: String,
    val language: String,
    val pageIndex: Int,
    /** The whole page as one image (panels, captions and speech bubbles are drawn into it). */
    val imageUrl: String,
)

/** A language stories can be in, as managed in the admin site (e.g. "or", "Odia", "ଓଡ଼ିଆ"). */
@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey val code: String,
    val name: String,
    val nativeName: String,
    val sortOrder: Int,
)

// ---------- User state (kept separate so a server refresh never wipes it) ----------

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val storyId: String,
    val addedAt: Long,
)

/**
 * A like/unlike the server hasn't confirmed yet (offline, or the request failed).
 * Sent again by LikeSyncer on the next like or refresh; removed once the server accepts it.
 */
@Entity(tableName = "pending_likes")
data class PendingLikeEntity(
    @PrimaryKey val storyId: String,
    val liked: Boolean,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val storyId: String,
    val downloadedAt: Long,
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val storyId: String,
    val lastPageIndex: Int,
    val totalPages: Int,
    val isCompleted: Boolean,
    val updatedAt: Long,
)

/**
 * A page marked with the ribbon, in one language version of a story. [format] is the StoryFormat
 * name: COMIC marks page [position]; TEXT marks where the page starts in the text (paragraph
 * [position], character [textOffset]), as text pages change with the text size. Like favorites, kept
 * when the story leaves the catalogue (just not shown).
 */
@Entity(tableName = "bookmarks", primaryKeys = ["storyId", "language", "format", "position", "textOffset"])
data class BookmarkEntity(
    val storyId: String,
    val language: String,
    val format: String,
    val position: Int,
    val textOffset: Int,
    val createdAt: Long,
)

/** A category with its name in the requested language (or another one it has). */
data class CategoryRow(
    val id: String,
    val sortOrder: Int,
    val name: String?,
)

/** A language a story is available in, for the reader's language switch. */
data class StoryLanguageRow(
    val code: String,
    val nativeName: String?,
)

/** A story's like count (see StoryDao.likeCounts). */
data class StoryLikeCount(
    val id: String,
    val likeCount: Int,
)
