// Makes the app's Baseline Profile: runs the app on a device or emulator (Android 9+), opening it,
// scrolling Home, opening a story and turning its pages, and records the code that ran so the
// app ships it compiled ahead of time.
//   ./gradlew :app:generateReleaseBaselineProfile
// The profile lands in app/src/release/generated/baselineProfiles and goes into release builds.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.vegamstories.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // The connected phone or emulator, not one Gradle would download and start.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
