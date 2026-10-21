package com.vegam.myapplication.di

import com.vegam.myapplication.BuildConfig
import com.vegam.myapplication.data.remote.datasource.FakeLikeRemoteDataSource
import com.vegam.myapplication.data.remote.datasource.FakeStoryRemoteDataSource
import com.vegam.myapplication.data.remote.datasource.LikeRemoteDataSource
import com.vegam.myapplication.data.remote.datasource.NetworkLikeRemoteDataSource
import com.vegam.myapplication.data.remote.datasource.NetworkStoryRemoteDataSource
import com.vegam.myapplication.data.remote.datasource.StoryRemoteDataSource
import com.vegam.myapplication.data.repository.LibraryRepositoryImpl
import com.vegam.myapplication.data.repository.StoryRepositoryImpl
import com.vegam.myapplication.data.settings.AppCompatLanguageRepository
import com.vegam.myapplication.data.settings.SettingsRepositoryImpl
import com.vegam.myapplication.domain.repository.LanguageRepository
import com.vegam.myapplication.domain.repository.LibraryRepository
import com.vegam.myapplication.domain.repository.SettingsRepository
import com.vegam.myapplication.domain.repository.StoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStoryRepository(impl: StoryRepositoryImpl): StoryRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLanguageRepository(impl: AppCompatLanguageRepository): LanguageRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * Supabase when SUPABASE_URL / SUPABASE_ANON_KEY are in local.properties, dummy data otherwise
     * (USE_FAKE_BACKEND is set automatically in app/build.gradle.kts).
     * Provider<> means the unused implementation is never even created.
     */
    @Provides
    @Singleton
    fun provideStoryRemoteDataSource(
        fake: Provider<FakeStoryRemoteDataSource>,
        network: Provider<NetworkStoryRemoteDataSource>,
    ): StoryRemoteDataSource = if (BuildConfig.USE_FAKE_BACKEND) fake.get() else network.get()

    @Provides
    @Singleton
    fun provideLikeRemoteDataSource(
        fake: Provider<FakeLikeRemoteDataSource>,
        network: Provider<NetworkLikeRemoteDataSource>,
    ): LikeRemoteDataSource = if (BuildConfig.USE_FAKE_BACKEND) fake.get() else network.get()
}
