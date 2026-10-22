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
