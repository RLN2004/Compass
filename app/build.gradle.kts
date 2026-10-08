plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "in.swalearn.compasslite"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.swalearn.compasslite"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
