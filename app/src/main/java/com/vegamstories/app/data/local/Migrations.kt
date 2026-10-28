package com.vegamstories.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 2 → 3: likes. Keeps the user's favorites, which are now likes. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stories` ADD COLUMN `likeCount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_likes` " +
                "(`storyId` TEXT NOT NULL, `liked` INTEGER NOT NULL, PRIMARY KEY(`storyId`))",
        )
        // Hearts given before likes existed were never sent to the server: send them now.
        db.execSQL("INSERT OR REPLACE INTO `pending_likes` (`storyId`, `liked`) SELECT `storyId`, 1 FROM `favorites`")
    }
}

/** 3 → 4: stories can also be read as text. A new cache table; nothing existing changes. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `story_texts` (`storyId` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, PRIMARY KEY(`storyId`, `language`), FOREIGN KEY(`storyId`) " +
                "REFERENCES `stories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/** 4 → 5: the list of text languages (names, order) comes from the server. A new cache table. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `languages` (`code` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`nativeName` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`code`))",
        )
    }
}

/**
 * 5 → 6: stories, pages and category names per language. The server content tables are only a
 * cache, so they are rebuilt empty in the new shape and the next refresh fills them again. The
 * user's own tables (favorites, pending likes, downloads, reading progress) are not touched.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Children first, so no ON DELETE CASCADE reaches anything else.
        db.execSQL("DROP TABLE IF EXISTS `story_texts`")
        db.execSQL("DROP TABLE IF EXISTS `story_pages`")
        db.execSQL("DROP TABLE IF EXISTS `stories`")
        db.execSQL("DROP TABLE IF EXISTS `categories`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category_names` (`categoryId` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, PRIMARY KEY(`categoryId`, `language`), FOREIGN KEY(`categoryId`) " +
                "REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stories` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                "`episodeNumber` INTEGER NOT NULL, `publishedAt` INTEGER NOT NULL, " +
                "`likeCount` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_categoryId` ON `stories` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_publishedAt` ON `stories` (`publishedAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `story_translations` (`storyId` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `summary` TEXT NOT NULL, `coverImageUrl` TEXT NOT NULL, " +
                "`pageCount` INTEGER NOT NULL, `body` TEXT, `videoTitle` TEXT, `videoDescription` TEXT, " +
                "`videoThumbnailUrl` TEXT, `videoUrl` TEXT, PRIMARY KEY(`storyId`, `language`), " +
                "FOREIGN KEY(`storyId`) REFERENCES `stories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_story_translations_language` ON `story_translations` (`language`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `story_pages` (`storyId` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                "`pageIndex` INTEGER NOT NULL, `imageUrl` TEXT NOT NULL, " +
                "PRIMARY KEY(`storyId`, `language`, `pageIndex`), FOREIGN KEY(`storyId`) " +
                "REFERENCES `stories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/** 6 → 7: the cover is optional. SQLite can't relax NOT NULL in place, so the table is copied. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `story_translations_new` (`storyId` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `summary` TEXT NOT NULL, `coverImageUrl` TEXT, " +
                "`pageCount` INTEGER NOT NULL, `body` TEXT, `videoTitle` TEXT, `videoDescription` TEXT, " +
                "`videoThumbnailUrl` TEXT, `videoUrl` TEXT, PRIMARY KEY(`storyId`, `language`), " +
                "FOREIGN KEY(`storyId`) REFERENCES `stories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("INSERT INTO `story_translations_new` SELECT * FROM `story_translations`")
        db.execSQL("DROP TABLE `story_translations`")
        db.execSQL("ALTER TABLE `story_translations_new` RENAME TO `story_translations`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_story_translations_language` ON `story_translations` (`language`)")
    }
}

/** 7 → 8: bookmarks, pages marked with the ribbon. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bookmarks` (`storyId` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                "`format` TEXT NOT NULL, `position` INTEGER NOT NULL, `textOffset` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`storyId`, `language`, `format`, `position`, `textOffset`))",
        )
    }
}
