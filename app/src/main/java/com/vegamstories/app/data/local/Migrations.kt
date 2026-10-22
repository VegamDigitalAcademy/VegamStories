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
