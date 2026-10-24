import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Supabase keys are read from local.properties (never committed to git):
//   SUPABASE_URL=https://xxxx.supabase.co
//   SUPABASE_ANON_KEY=eyJ...   (or sb_publishable_...)
// Without them the app runs on dummy data, so a fresh checkout still builds and runs.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val supabaseUrl: String = localProperties.getProperty("SUPABASE_URL").orEmpty().trim().removeSuffix("/")
val supabaseAnonKey: String = localProperties.getProperty("SUPABASE_ANON_KEY").orEmpty().trim()
val hasSupabase = supabaseUrl.isNotEmpty() && supabaseAnonKey.isNotEmpty()

android {
    namespace = "com.vegamstories.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vegamstories.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase REST API (PostgREST). The anon/publishable key is meant to ship in apps;
        // Row Level Security in Supabase is what keeps the data read-only.
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField(
            "String",
            "BASE_URL",
            "\"${if (hasSupabase) "$supabaseUrl/rest/v1/" else "https://localhost/"}\"",
        )
        // Real Supabase data when the keys are present, dummy data otherwise.
        buildConfigField("boolean", "USE_FAKE_BACKEND", (!hasSupabase).toString())
    }

    buildTypes {
        release {
            // R8: shrinks, optimizes and obfuscates the code and removes unused resources.
            // App keep rules live in src/main/keepRules/; libraries bring their own.
            // (optimization { enable = true } is AGP 9's experimental "gradual R8", so not used.)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room writes each DB version's schema here (commit it). Needed to review and test migrations.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Core / Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Background refresh + new-story notifications (WorkManager, workers injected by Hilt)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Video: YouTube player inside the reader
    implementation(libs.youtube.player.core)

    // Splash screen with the logo
    implementation(libs.androidx.core.splashscreen)

    // In-app language switch (English / Telugu)
    implementation(libs.androidx.appcompat)

    // Logging
    implementation(libs.timber)

    // Settings (theme choice)
    implementation(libs.androidx.datastore.preferences)

    // Kotlin
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Images
    implementation(libs.coil.compose)

    // Home screen widget (continue reading / today's new story)
    implementation(libs.glance.appwidget)
    implementation(libs.coil.network.okhttp)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
