package com.vegamstories.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vegamstories.app.data.local.entity.LanguageEntity
import com.vegamstories.app.data.local.entity.PendingLikeEntity
import com.vegamstories.app.data.local.entity.ReadingProgressEntity
import com.vegamstories.app.data.local.entity.StoryTextEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrading the app must keep the user's favorites, downloads and reading progress.
 *
 * Old databases are created here by hand, exactly as Room created them: version 2 was never
 * exported (exportSchema was off), versions 3 and 4 match app/schemas/.../3.json and 4.json.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun upgradeFrom2_keepsUserStateAndQueuesLikesForFavorites() {
        createDatabase(
            version = 2,
            schema = VERSION_2_SCHEMA,
            "INSERT INTO categories (id, name, sortOrder) VALUES ('moral', 'Moral', 0)",
            insertStory("moral-1", episode = 1),
            insertStory("moral-2", episode = 2),
            "INSERT INTO story_pages (storyId, pageIndex, imageUrl) VALUES ('moral-1', 0, 'https://example.com/p0.webp')",
            "INSERT INTO favorites (storyId, addedAt) VALUES ('moral-1', 1700000000000)",
            "INSERT INTO downloads (storyId, downloadedAt) VALUES ('moral-1', 1700000000001)",
            "INSERT INTO reading_progress (storyId, lastPageIndex, totalPages, isCompleted, updatedAt) " +
                "VALUES ('moral-2', 3, 8, 0, 1700000000002)",
        )

        withMigratedDatabase { db ->
            val library = db.libraryDao()
            val stories = db.storyDao()

            assertTrue(library.isFavorite("moral-1"))
            assertFalse(library.isFavorite("moral-2"))
            assertEquals(0, stories.getStory("moral-1")?.likeCount)
            assertEquals(0, stories.getStory("moral-2")?.likeCount)
            // The heart given before likes existed is sent to the server; nothing else is.
            assertEquals(listOf(PendingLikeEntity("moral-1", liked = true)), library.getPendingLikes())

            assertEquals(1, stories.getPages("moral-1").size)
            assertTrue(library.isDownloaded("moral-1"))
            assertEquals(
                ReadingProgressEntity("moral-2", lastPageIndex = 3, totalPages = 8, isCompleted = false, updatedAt = 1700000000002),
                library.getProgress("moral-2"),
            )
        }
    }

    @Test
    fun upgradeFrom3_keepsEverythingAndStoriesCanGetTexts() {
        createDatabase(
            version = 3,
            schema = VERSION_3_SCHEMA,
            "INSERT INTO categories (id, name, sortOrder) VALUES ('moral', 'Moral', 0)",
            insertStory("moral-1", episode = 1, likeCount = 42),
            "INSERT INTO story_pages (storyId, pageIndex, imageUrl) VALUES ('moral-1', 0, 'https://example.com/p0.webp')",
            "INSERT INTO favorites (storyId, addedAt) VALUES ('moral-1', 1700000000000)",
            "INSERT INTO pending_likes (storyId, liked) VALUES ('moral-1', 1)",
            "INSERT INTO reading_progress (storyId, lastPageIndex, totalPages, isCompleted, updatedAt) " +
                "VALUES ('moral-1', 2, 8, 0, 1700000000002)",
        )

        withMigratedDatabase { db ->
            val library = db.libraryDao()
            val stories = db.storyDao()

            assertTrue(library.isFavorite("moral-1"))
            assertEquals(42, stories.getStory("moral-1")?.likeCount)
            assertEquals(listOf(PendingLikeEntity("moral-1", liked = true)), library.getPendingLikes())
            assertEquals(1, stories.getPages("moral-1").size)
            assertEquals(2, library.getProgress("moral-1")?.lastPageIndex)

            // No text yet: the next refresh brings it.
            assertFalse(stories.observeStory("moral-1").first()!!.hasText)
            val text = StoryTextEntity("moral-1", language = "te", body = "ఒకప్పుడు…")
            stories.upsertTexts(listOf(text))
            assertTrue(stories.observeStory("moral-1").first()!!.hasText)
            assertEquals(listOf(text), stories.observeTexts("moral-1").first().map { it.text })
        }
    }

    @Test
    fun upgradeFrom4_keepsTextsAndAddsLanguages() {
        createDatabase(
            version = 4,
            schema = VERSION_4_SCHEMA,
            "INSERT INTO categories (id, name, sortOrder) VALUES ('moral', 'Moral', 0)",
            insertStory("moral-1", episode = 1, likeCount = 5),
            "INSERT INTO favorites (storyId, addedAt) VALUES ('moral-1', 1700000000000)",
            "INSERT INTO story_texts (storyId, language, body) VALUES ('moral-1', 'te', 'కథ')",
            "INSERT INTO story_texts (storyId, language, body) VALUES ('moral-1', 'or', 'କାହାଣୀ')",
        )

        withMigratedDatabase { db ->
            val library = db.libraryDao()
            val stories = db.storyDao()

            assertTrue(library.isFavorite("moral-1"))
            assertEquals(5, stories.getStory("moral-1")?.likeCount)
            // No languages saved yet (the next refresh brings them): texts still load, by code.
            assertEquals(setOf("te", "or"), stories.observeTexts("moral-1").first().map { it.text.language }.toSet())
            stories.upsertLanguages(
                listOf(LanguageEntity("or", "Odia", "ଓଡ଼ିଆ", sortOrder = 0), LanguageEntity("te", "Telugu", "తెలుగు", sortOrder = 1)),
            )
            val texts = stories.observeTexts("moral-1").first()
            assertEquals(listOf("or", "te"), texts.map { it.text.language }) // server order
            assertEquals("ଓଡ଼ିଆ", texts.first().nativeName)
        }
    }

    /** Builds the database file the way an installed older app left it. */
    private fun createDatabase(version: Int, schema: List<String>, vararg inserts: String) {
        val file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.forEach(db::execSQL)
            inserts.forEach(db::execSQL)
            db.version = version
        }
    }

    /**
     * Opens the old file with the current Room database, which runs the migrations. There is no
     * destructive fallback here, so a missing or broken migration fails the test. Room also checks
     * the migrated tables against the current entities when it opens the database.
     */
    private fun withMigratedDatabase(block: suspend (VegamDatabase) -> Unit) {
        val db = Room.databaseBuilder(context, VegamDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    private fun insertStory(id: String, episode: Int, likeCount: Int? = null): String {
        val likeColumn = if (likeCount != null) ", likeCount" else ""
        val likeValue = if (likeCount != null) ", $likeCount" else ""
        return "INSERT INTO stories (id, categoryId, episodeNumber, title, summary, coverImageUrl, pageCount, " +
            "publishedAt, videoTitle, videoDescription, videoThumbnailUrl, videoUrl$likeColumn) " +
            "VALUES ('$id', 'moral', $episode, 'Story $episode', '', 'https://example.com/$id.webp', 1, " +
            "1700000000000, NULL, NULL, NULL, NULL$likeValue)"
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        private val CATEGORIES = "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        private const val STORY_COLUMNS = "`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
            "`episodeNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
            "`coverImageUrl` TEXT NOT NULL, `pageCount` INTEGER NOT NULL, `publishedAt` INTEGER NOT NULL, " +
            "`videoTitle` TEXT, `videoDescription` TEXT, `videoThumbnailUrl` TEXT, `videoUrl` TEXT"
        private val STORY_INDICES = listOf(
            "CREATE INDEX IF NOT EXISTS `index_stories_categoryId` ON `stories` (`categoryId`)",
            "CREATE INDEX IF NOT EXISTS `index_stories_publishedAt` ON `stories` (`publishedAt`)",
        )
        private val USER_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `story_pages` (`storyId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, " +
                "`imageUrl` TEXT NOT NULL, PRIMARY KEY(`storyId`, `pageIndex`), FOREIGN KEY(`storyId`) " +
                "REFERENCES `stories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `favorites` (`storyId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`storyId`))",
            "CREATE TABLE IF NOT EXISTS `downloads` (`storyId` TEXT NOT NULL, `downloadedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`storyId`))",
            "CREATE TABLE IF NOT EXISTS `reading_progress` (`storyId` TEXT NOT NULL, `lastPageIndex` INTEGER NOT NULL, " +
                "`totalPages` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`storyId`))",
        )

        /** Version 3 without `stories.likeCount` and without `pending_likes`. */
        val VERSION_2_SCHEMA = listOf(CATEGORIES, "CREATE TABLE IF NOT EXISTS `stories` ($STORY_COLUMNS, PRIMARY KEY(`id`))") +
            STORY_INDICES + USER_TABLES

        /** Room's CREATE statements from app/schemas/.../3.json. */
        val VERSION_3_SCHEMA = listOf(
            CATEGORIES,
            "CREATE TABLE IF NOT EXISTS `stories` ($STORY_COLUMNS, `likeCount` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        ) + STORY_INDICES + USER_TABLES + listOf(
            "CREATE TABLE IF NOT EXISTS `pending_likes` (`storyId` TEXT NOT NULL, `liked` INTEGER NOT NULL, PRIMARY KEY(`storyId`))",
        )

        /** Room's CREATE statements from app/schemas/.../4.json. */
        val VERSION_4_SCHEMA = VERSION_3_SCHEMA + listOf(
            "CREATE TABLE IF NOT EXISTS `story_texts` (`storyId` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, PRIMARY KEY(`storyId`, `language`), FOREIGN KEY(`storyId`) " +
                "REFERENCES `stories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
