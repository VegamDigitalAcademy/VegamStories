package com.vegamstories.app.di

import android.content.Context
import androidx.room.Room
import com.vegamstories.app.data.local.MIGRATION_2_3
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
            // Keeps favorites/likes, downloads and progress when updating from version 2.
            .addMigrations(MIGRATION_2_3)
            // Server content is only a cache, so during development a schema change can
            // simply rebuild the DB. Replace with real migrations before release.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideStoryDao(db: VegamDatabase): StoryDao = db.storyDao()

    @Provides
    fun provideLibraryDao(db: VegamDatabase): LibraryDao = db.libraryDao()
}
