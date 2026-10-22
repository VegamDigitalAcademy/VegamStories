package com.vegamstories.app.di

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.vegamstories.app.BuildConfig
import com.vegamstories.app.data.remote.api.StoryApi
import com.vegamstories.app.data.remote.logging.PrettyApiLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
        .apply {
            // Debug builds only: every Supabase call in Logcat (tag "VegamApi") with pretty JSON.
            // Added after the auth interceptor so it shows the real request; keys are masked.
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor(PrettyApiLogger()).apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader("apikey")
                        redactHeader("Authorization")
                    },
                )
            }
        }
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

    /** The app-wide Coil loader (configured in VegamStoriesApp). */
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        SingletonImageLoader.get(context)
}
