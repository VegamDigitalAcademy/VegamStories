package com.vegamstories.app.di

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.vegamstories.app.BuildConfig
import com.vegamstories.app.data.remote.api.CatalogueApi
import com.vegamstories.app.data.remote.api.StoryApi
import com.vegamstories.app.data.remote.logging.ApiLoggingInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        // Supabase needs the project key on every request. This client is only used by
        // Retrofit (Supabase); Coil has its own client, so the key never goes to R2.
        .addInterceptor { chain ->
            val key = BuildConfig.SUPABASE_ANON_KEY
            val request = chain.request().newBuilder()
                .header("apikey", key)
                .apply {
                    // Legacy anon keys are JWTs ("eyJ…") and go in Authorization too.
                    // New publishable keys ("sb_publishable_…") must NOT be sent there.
                    if (key.startsWith("eyJ")) header("Authorization", "Bearer $key")
                }
                .build()
            chain.proceed(request)
        }
        // Every call and its response in the logs (tag "ApiLoggingInterceptor"): Logcat in debug builds, the log
        // files in every build. After the auth interceptor, so it sees the real request; it never
        // logs headers, so the key stays out of the logs.
        .addInterceptor(ApiLoggingInterceptor())
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideStoryApi(retrofit: Retrofit): StoryApi = retrofit.create(StoryApi::class.java)

    /**
     * The story catalogue in R2 (media.vegamstories.com). Its own client: these requests must not
     * carry the Supabase key. Logged like the Supabase calls.
     */
    @Provides
    @Singleton
    fun provideCatalogueApi(json: Json): CatalogueApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(ApiLoggingInterceptor())
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.CATALOGUE_URL.substringBeforeLast('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CatalogueApi::class.java)
    }

    /** The app-wide Coil loader (configured in VegamStoriesApp). */
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        SingletonImageLoader.get(context)
}
