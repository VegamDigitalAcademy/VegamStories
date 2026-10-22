package com.vegamstories.app.di

import android.content.Context
import androidx.room.Room
import com.vegamstories.app.data.local.MIGRATION_2_3
import com.vegamstories.app.data.local.MIGRATION_3_4
import com.vegamstories.app.data.local.MIGRATION_4_5
import com.vegamstories.app.data.local.VegamDatabase
import com.vegamstories.app.data.local.dao.LibraryDao
import com.vegamstories.app.data.local.dao.StoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VegamDatabase =
        Room.databaseBuilder(context, VegamDatabase::class.java, VegamDatabase.NAME)
            // Keeps favorites/likes, downloads and progress when updating from version 2, 3 or 4.
            // Every version bump needs a Migration here: an upgrade without one crashes on
            // purpose instead of wiping the user's favorites, likes, downloads and progress.
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            // Version 1 never shipped and has no migration; only dev installs can have it.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            // Installing an older build over a newer one has no way back, so start fresh.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideStoryDao(db: VegamDatabase): StoryDao = db.storyDao()

    @Provides
    fun provideLibraryDao(db: VegamDatabase): LibraryDao = db.libraryDao()
}
