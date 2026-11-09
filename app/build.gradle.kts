
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nearlink.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nearlink.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
    }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.gms:play-services-nearby:19.5.1")
}
