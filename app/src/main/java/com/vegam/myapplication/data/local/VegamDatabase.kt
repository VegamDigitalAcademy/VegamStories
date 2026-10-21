package com.vegam.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vegam.myapplication.data.local.dao.LibraryDao
import com.vegam.myapplication.data.local.dao.StoryDao
import com.vegam.myapplication.data.local.entity.CategoryEntity
import com.vegam.myapplication.data.local.entity.DownloadEntity
import com.vegam.myapplication.data.local.entity.FavoriteEntity
import com.vegam.myapplication.data.local.entity.PendingLikeEntity
import com.vegam.myapplication.data.local.entity.ReadingProgressEntity
import com.vegam.myapplication.data.local.entity.StoryEntity
import com.vegam.myapplication.data.local.entity.StoryPageEntity

@Database(
    entities = [
        CategoryEntity::class,
        StoryEntity::class,
        StoryPageEntity::class,
        FavoriteEntity::class,
        DownloadEntity::class,
        ReadingProgressEntity::class,
        PendingLikeEntity::class,
    ],
    // 2: story_pages holds one image per page instead of a JSON list of panels.
    // 3: stories.likeCount + pending_likes (see MIGRATION_2_3).
    version = 3,
    exportSchema = false,
)
abstract class VegamDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "vegam_stories.db"
    }
}
