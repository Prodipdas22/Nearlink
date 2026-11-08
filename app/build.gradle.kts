plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nearlink.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nearlink.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
