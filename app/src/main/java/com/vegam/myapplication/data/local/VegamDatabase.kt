package com.vegam.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vegam.myapplication.data.local.dao.LibraryDao
import com.vegam.myapplication.data.local.dao.StoryDao
import com.vegam.myapplication.data.local.entity.CategoryEntity
import com.vegam.myapplication.data.local.entity.DownloadEntity
import com.vegam.myapplication.data.local.entity.FavoriteEntity
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
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class VegamDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "vegam_stories.db"
    }
}
