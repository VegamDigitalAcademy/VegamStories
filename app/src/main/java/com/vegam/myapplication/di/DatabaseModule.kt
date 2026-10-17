package com.vegam.myapplication.di

import android.content.Context
import androidx.room.Room
import com.vegam.myapplication.data.local.VegamDatabase
import com.vegam.myapplication.data.local.dao.LibraryDao
import com.vegam.myapplication.data.local.dao.StoryDao
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
            // Server content is only a cache, so during development a schema change can
            // simply rebuild the DB. Replace with real migrations before release.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideStoryDao(db: VegamDatabase): StoryDao = db.storyDao()

    @Provides
    fun provideLibraryDao(db: VegamDatabase): LibraryDao = db.libraryDao()
}
