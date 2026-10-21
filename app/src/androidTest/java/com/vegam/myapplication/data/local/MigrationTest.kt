package com.vegam.myapplication.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vegam.myapplication.data.local.entity.PendingLikeEntity
import com.vegam.myapplication.data.local.entity.ReadingProgressEntity
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
 * Version 2 was never exported (exportSchema was off), so its tables are created here by hand,
 * exactly as Room created them: version 3 without `stories.likeCount` and without `pending_likes`.
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
    fun migrate2To3_keepsUserStateAndQueuesLikesForFavorites() {
        createVersion2Database(
            "INSERT INTO categories (id, name, sortOrder) VALUES ('moral', 'Moral', 0)",
            insertStory("moral-1", episode = 1),
            insertStory("moral-2", episode = 2),
            "INSERT INTO story_pages (storyId, pageIndex, imageUrl) VALUES ('moral-1', 0, 'https://example.com/p0.webp')",
            "INSERT INTO favorites (storyId, addedAt) VALUES ('moral-1', 1700000000000)",
            "INSERT INTO downloads (storyId, downloadedAt) VALUES ('moral-1', 1700000000001)",
            "INSERT INTO reading_progress (storyId, lastPageIndex, totalPages, isCompleted, updatedAt) " +
                "VALUES ('moral-2', 3, 8, 0, 1700000000002)",
        )

        // No destructive fallback here, so a missing or broken migration fails the test. Room also
        // checks the migrated tables against the version 3 entities when it opens the database.
        val db = Room.databaseBuilder(context, VegamDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_2_3)
            .build()
        try {
            runBlocking {
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
        } finally {
            db.close()
        }
    }

    /** Builds the version 2 database file the way an installed version 2 app left it. */
    private fun createVersion2Database(vararg inserts: String) {
        val file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_2_SCHEMA.forEach(db::execSQL)
            inserts.forEach(db::execSQL)
            db.version = 2
        }
    }

    private fun insertStory(id: String, episode: Int) =
        "INSERT INTO stories (id, categoryId, episodeNumber, title, summary, coverImageUrl, pageCount, " +
            "publishedAt, videoTitle, videoDescription, videoThumbnailUrl, videoUrl) " +
            "VALUES ('$id', 'moral', $episode, 'Story $episode', '', 'https://example.com/$id.webp', 1, " +
            "1700000000000, NULL, NULL, NULL, NULL)"

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** Room's CREATE statements for version 2 (see app/schemas/ for version 3 onwards). */
        val VERSION_2_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `stories` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                "`episodeNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                "`coverImageUrl` TEXT NOT NULL, `pageCount` INTEGER NOT NULL, `publishedAt` INTEGER NOT NULL, " +
                "`videoTitle` TEXT, `videoDescription` TEXT, `videoThumbnailUrl` TEXT, `videoUrl` TEXT, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_stories_categoryId` ON `stories` (`categoryId`)",
            "CREATE INDEX IF NOT EXISTS `index_stories_publishedAt` ON `stories` (`publishedAt`)",
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
    }
}
