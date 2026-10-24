package com.vegamstories.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vegamstories.app.data.local.dao.LibraryDao
import com.vegamstories.app.data.local.dao.StoryDao
import com.vegamstories.app.data.local.entity.CategoryEntity
import com.vegamstories.app.data.local.entity.CategoryNameEntity
import com.vegamstories.app.data.local.entity.DownloadEntity
import com.vegamstories.app.data.local.entity.FavoriteEntity
import com.vegamstories.app.data.local.entity.LanguageEntity
import com.vegamstories.app.data.local.entity.PendingLikeEntity
import com.vegamstories.app.data.local.entity.ReadingProgressEntity
import com.vegamstories.app.data.local.entity.StoryEntity
import com.vegamstories.app.data.local.entity.StoryPageEntity
import com.vegamstories.app.data.local.entity.StoryTranslationEntity

@Database(
    entities = [
        CategoryEntity::class,
        StoryEntity::class,
        StoryPageEntity::class,
        FavoriteEntity::class,
        DownloadEntity::class,
        ReadingProgressEntity::class,
        PendingLikeEntity::class,
        StoryTranslationEntity::class,
        CategoryNameEntity::class,
        LanguageEntity::class,
    ],
    // 2: story_pages holds one image per page instead of a JSON list of panels.
    // 3: stories.likeCount + pending_likes (see MIGRATION_2_3).
    // 4: story_texts, the story as text (see MIGRATION_3_4).
    // 5: languages, managed on the server (see MIGRATION_4_5).
    // 6: everything per language: story_translations, story_pages.language, category_names (MIGRATION_5_6).
    // 7: story_translations.coverImageUrl optional (MIGRATION_6_7).
    version = 7,
    // Schemas go to app/schemas/ (see the room {} block in app/build.gradle.kts).
    exportSchema = true,
)
abstract class VegamDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "vegam_stories.db"
    }
}
